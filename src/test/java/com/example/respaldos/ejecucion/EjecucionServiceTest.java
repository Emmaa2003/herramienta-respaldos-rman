package com.example.respaldos.ejecucion;

import com.example.respaldos.comun.ConflictoException;
import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.generador.HuellaConfiguracion;
import com.example.respaldos.infraestructura.ClienteContenedor;
import com.example.respaldos.infraestructura.ResultadoProceso;
import com.example.respaldos.modelo.*;
import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EjecucionRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import com.example.respaldos.validacion.Hallazgo;
import com.example.respaldos.validacion.ResultadoValidacion;
import com.example.respaldos.validacion.ValidacionFallidaException;
import com.example.respaldos.validacion.ValidadorEstrategia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.example.respaldos.ejecucion.SalidasRman.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de ejecucion con el contenedor simulado: no se ejecuta RMAN real.
 * La ejecucion en segundo plano se invoca directamente (ejecutar) para que sea sincronica.
 * Cada prueba hace rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EjecucionServiceTest {

    private static final String SCRIPT = "RUN {\n  BACKUP INCREMENTAL LEVEL 0 TAG 'EST7_N0' DATABASE;\n}\n";

    @Autowired EjecucionService servicio;
    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;
    @Autowired EjecucionRepository ejecuciones;
    @Autowired AlertaRepository alertas;
    @MockitoBean ClienteContenedor cliente;
    @MockitoBean ValidadorEstrategia validador;

    private Estrategia estrategia;

    @BeforeEach
    void preparar() {
        estrategia = estrategia();
        when(validador.validar(any(Estrategia.class)))
                .thenReturn(new ResultadoValidacion(estrategia.getId(), true, LocalDateTime.now(), List.of()));
    }

    @Test
    void ejecutaElScriptAprobadoTalCualVerificaYQuedaExitoso() {
        aprobar();
        when(cliente.ejecutarRman(eq("oracle-xe"), anyString())).thenReturn(proceso(0, EXITOSA), proceso(0, VERIFICACION_OK));
        when(cliente.tamanoArchivo("oracle-xe", PIEZA)).thenReturn(Optional.of(1_000_000L));
        when(cliente.tamanoArchivo("oracle-xe", AUTORESPALDO)).thenReturn(Optional.of(11_000_000L));

        EjecucionRespuesta inicio = servicio.ejecutarManual(estrategia.getId());
        assertThat(inicio.estado()).isEqualTo(EstadoEjecucion.EN_CURSO);
        servicio.ejecutar(inicio.id());

        verify(cliente).ejecutarRman("oracle-xe", SCRIPT);
        verify(cliente).ejecutarRman(eq("oracle-xe"), argThat(s -> s.startsWith("# Verificacion")
                && s.contains("CROSSCHECK BACKUPPIECE") && s.contains("RESTORE DATABASE VALIDATE;")));

        Ejecucion x = ejecuciones.findById(inicio.id()).orElseThrow();
        assertThat(x.getEstado()).isEqualTo(EstadoEjecucion.EXITOSO);
        assertThat(x.getVerificacion()).isEqualTo(EstadoVerificacion.VERIFICADO);
        assertThat(x.getOrigen()).isEqualTo(OrigenEjecucion.MANUAL);
        assertThat(x.getScriptEjecutado()).isEqualTo(SCRIPT);
        assertThat(x.getTamanoTotalBytes()).isEqualTo(12_000_000L);
        assertThat(x.getFechaFin()).isNotNull();
        assertThat(x.getSalidaRman()).contains("Finished backup");
        assertThat(x.getSalidaVerificacion()).contains("== Script de verificacion ==").contains("AVAILABLE");
        assertThat(x.getArchivos()).extracting(EvidenciaArchivo::getTipo)
                .containsExactly(TipoEvidencia.PIEZA_RESPALDO, TipoEvidencia.AUTORESPALDO_CONTROLFILE);
        assertThat(x.getArchivos()).allMatch(EvidenciaArchivo::isExiste);
        assertThat(alertas.findByEstrategiaIdOrderByIdDesc(estrategia.getId())).isEmpty();
    }

    @Test
    void sinArchivoElRespaldoEsFallidoAunqueRmanNoDieraError() {
        aprobar();
        when(cliente.ejecutarRman(eq("oracle-xe"), anyString())).thenReturn(proceso(0, EXITOSA), proceso(0, VERIFICACION_OK));
        when(cliente.tamanoArchivo("oracle-xe", PIEZA)).thenReturn(Optional.empty());
        when(cliente.tamanoArchivo("oracle-xe", AUTORESPALDO)).thenReturn(Optional.of(11_000_000L));

        Long id = servicio.ejecutarManual(estrategia.getId()).id();
        servicio.ejecutar(id);

        Ejecucion x = ejecuciones.findById(id).orElseThrow();
        assertThat(x.getEstado()).isEqualTo(EstadoEjecucion.FALLIDO);
        assertThat(x.getVerificacion()).isEqualTo(EstadoVerificacion.FALLIDA);
        assertThat(x.getMensajeError()).startsWith("El script termino sin errores, pero la verificacion fallo");
        assertThat(alertas.findByEstrategiaIdOrderByIdDesc(estrategia.getId())).singleElement()
                .satisfies(a -> {
                    assertThat(a.getCodigo()).isEqualTo("EJECUCION_FALLIDA");
                    assertThat(a.getEjecucion().getId()).isEqualTo(id);
                });
    }

    @Test
    void errorDeRmanEsFallidoYNoSeCorreLaVerificacion() {
        aprobar();
        when(cliente.ejecutarRman(eq("oracle-xe"), anyString())).thenReturn(proceso(1, CON_ERROR));

        Long id = servicio.ejecutarManual(estrategia.getId()).id();
        servicio.ejecutar(id);

        verify(cliente, times(1)).ejecutarRman(anyString(), anyString());
        verify(cliente, never()).tamanoArchivo(anyString(), anyString());
        Ejecucion x = ejecuciones.findById(id).orElseThrow();
        assertThat(x.getEstado()).isEqualTo(EstadoEjecucion.FALLIDO);
        assertThat(x.getVerificacion()).isEqualTo(EstadoVerificacion.NO_APLICA);
        assertThat(x.getCodigoSalida()).isEqualTo(1);
        assertThat(x.getMensajeError()).contains("ORA-19504");
    }

    @Test
    void unaFallaInesperadaQuedaRegistradaComoFallida() {
        aprobar();
        when(cliente.ejecutarRman(anyString(), anyString())).thenThrow(new IllegalStateException("docker no existe"));

        Long id = servicio.ejecutarManual(estrategia.getId()).id();
        servicio.ejecutar(id);

        Ejecucion x = ejecuciones.findById(id).orElseThrow();
        assertThat(x.getEstado()).isEqualTo(EstadoEjecucion.FALLIDO);
        assertThat(x.getMensajeError()).contains("docker no existe");
    }

    @Test
    void sinScriptAprobadoNoSeEjecuta() {
        assertThatThrownBy(() -> servicio.ejecutarManual(estrategia.getId()))
                .isInstanceOf(NoEncontradoException.class);
        verifyNoInteractions(cliente);
    }

    @Test
    void siLaValidacionFallaNoSeEjecuta() {
        aprobar();
        when(validador.validar(any(Estrategia.class))).thenReturn(new ResultadoValidacion(estrategia.getId(), false,
                LocalDateTime.now(), List.of(Hallazgo.bloqueante("DESTINO_INEXISTENTE", "No existe el destino"))));

        assertThatThrownBy(() -> servicio.ejecutarManual(estrategia.getId()))
                .isInstanceOf(ValidacionFallidaException.class);
        assertThat(servicio.iniciarProgramada(estrategia.getId(), aprobado().getId(), LocalDateTime.now())).isEmpty();
        assertThat(alertas.findByEstrategiaIdOrderByIdDesc(estrategia.getId()))
                .extracting(Alerta::getCodigo).containsExactly("VALIDACION_FALLIDA");
        verifyNoInteractions(cliente);
    }

    @Test
    void noSeEjecutaDosVecesALaVezSobreLaMismaBase() {
        aprobar();
        servicio.ejecutarManual(estrategia.getId());

        assertThatThrownBy(() -> servicio.ejecutarManual(estrategia.getId()))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("en curso");
        assertThat(servicio.iniciarProgramada(estrategia.getId(), aprobado().getId(), LocalDateTime.now())).isEmpty();
    }

    @Test
    void laEjecucionProgramadaGuardaSuFechaYOrigen() {
        aprobar();
        LocalDateTime programada = LocalDateTime.of(2026, 10, 1, 23, 0);

        Long id = servicio.iniciarProgramada(estrategia.getId(), aprobado().getId(), programada).orElseThrow();

        Ejecucion x = ejecuciones.findById(id).orElseThrow();
        assertThat(x.getOrigen()).isEqualTo(OrigenEjecucion.PROGRAMADA);
        assertThat(x.getFechaProgramada()).isEqualTo(programada);
    }

    @Test
    void historialYDetallePorLaApi() throws Exception {
        aprobar();
        when(cliente.ejecutarRman(eq("oracle-xe"), anyString())).thenReturn(proceso(1, CON_ERROR));
        Long id = servicio.ejecutarManual(estrategia.getId()).id();
        servicio.ejecutar(id);

        mvc.perform(get("/api/ejecuciones").param("estrategiaId", estrategia.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("FALLIDO"))
                .andExpect(jsonPath("$[0].tipoRespaldo").value("INCREMENTAL_NIVEL_0"));
        mvc.perform(get("/api/ejecuciones/{id}", id))
                .andExpect(jsonPath("$.scriptEjecutado").value(SCRIPT))
                .andExpect(jsonPath("$.salidaRman").value(CON_ERROR))
                .andExpect(jsonPath("$.verificacion").value("NO_APLICA"));
        mvc.perform(get("/api/ejecuciones/{id}", 999_999_999)).andExpect(status().isNotFound());
    }

    // --- utilidades ---

    private static ResultadoProceso proceso(int codigo, String salida) {
        LocalDateTime t = LocalDateTime.now();
        return new ResultadoProceso(codigo, salida, t, t.plusSeconds(40), false);
    }

    private ScriptRman aprobado() {
        return scripts.findByEstrategiaIdAndEstado(estrategia.getId(), EstadoScript.APROBADO).getFirst();
    }

    private void aprobar() {
        ScriptRman s = new ScriptRman();
        s.setEstrategia(estrategia);
        s.setVersion(1);
        s.setContenido(SCRIPT);
        s.setHashContenido(HuellaConfiguracion.sha256(SCRIPT));
        s.setHashConfiguracion(HuellaConfiguracion.de(estrategia));
        s.setEstado(EstadoScript.APROBADO);
        s.setAprobadoPor("admin");
        s.setFechaAprobacion(LocalDateTime.now());
        scripts.saveAndFlush(s);
    }

    private Estrategia estrategia() {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE ejecucion pruebas");
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        bases.save(base);

        Estrategia e = new Estrategia();
        e.setNombre("Ejecucion pruebas");
        e.setBaseDatos(base);
        e.setResponsable("admin");
        e.setPrioridad(Prioridad.ALTA);
        e.setActiva(true);
        e.setTipoRespaldo(TipoRespaldo.INCREMENTAL_NIVEL_0);
        e.setRutaDestino("/opt/oracle/oradata/respaldos");
        e.agregarElemento(new EstrategiaElemento(TipoElemento.BASE_DATOS, null));
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.of(2026, 10, 1));
        p.setHora(LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        e.asignarProgramacion(p);
        return estrategias.saveAndFlush(e);
    }
}
