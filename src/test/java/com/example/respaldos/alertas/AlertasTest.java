package com.example.respaldos.alertas;

import com.example.respaldos.generador.HuellaConfiguracion;
import com.example.respaldos.infraestructura.CatalogoOracle;
import com.example.respaldos.infraestructura.ClienteContenedor;
import com.example.respaldos.modelo.*;
import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EjecucionRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import com.example.respaldos.validacion.Hallazgo;
import com.example.respaldos.validacion.ResultadoValidacion;
import com.example.respaldos.validacion.ValidadorEstrategia;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Monitor preventivo y atencion de alertas. El contenedor, el catalogo y el validador se
 * simulan para controlar las condiciones; no se ejecuta RMAN. Cada prueba hace rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertasTest {

    @Autowired MonitorPreventivo monitor;
    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;
    @Autowired EjecucionRepository ejecuciones;
    @Autowired AlertaRepository alertas;
    @Autowired EntityManager em;
    @MockitoBean ClienteContenedor cliente;
    @MockitoBean CatalogoOracle catalogo;
    @MockitoBean ValidadorEstrategia validador;

    private BaseDatos base;
    private Estrategia estrategia;

    @BeforeEach
    void preparar() {
        base = new BaseDatos();
        base.setNombre("XE alertas");
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        bases.save(base);

        estrategia = new Estrategia();
        estrategia.setNombre("Alertas pruebas");
        estrategia.setBaseDatos(base);
        estrategia.setResponsable("admin");
        estrategia.setPrioridad(Prioridad.ALTA);
        estrategia.setActiva(true);
        estrategia.setTipoRespaldo(TipoRespaldo.INCREMENTAL_NIVEL_0);
        estrategia.setRutaDestino("/opt/oracle/oradata/respaldos");
        estrategia.agregarElemento(new EstrategiaElemento(TipoElemento.BASE_DATOS, null));
        estrategias.saveAndFlush(estrategia);

        when(cliente.contenedorEnEjecucion(anyString())).thenReturn(true);
        when(catalogo.informacionBase()).thenReturn(
                new CatalogoOracle.InfoBaseDatos("XE", ModoArchivado.NOARCHIVELOG, "READ WRITE", "XEPDB1"));
        when(validador.validar(any(Estrategia.class))).thenReturn(new ResultadoValidacion(null, false,
                LocalDateTime.now(), List.of(
                Hallazgo.bloqueante("DESTINO_INEXISTENTE", "No existe el destino."),
                Hallazgo.advertencia("ESPACIO_BAJO", "El destino tiene menos del 10% libre."),
                Hallazgo.recomendacion("INCLUIR_ARCHIVELOG", "Considere incorporar los archived redo logs."))));
    }

    @Test
    void detectaLasCondicionesDelControlPreventivoSinDuplicar() {
        monitor.revisar(LocalDateTime.now().plusDays(2));

        assertThat(alertasDeLaEstrategia()).extracting(Alerta::getTipo, Alerta::getCodigo).containsExactlyInAnyOrder(
                tuple(TipoMensaje.ADVERTENCIA, "SIN_PROGRAMACION"),
                tuple(TipoMensaje.ADVERTENCIA, "SIN_SCRIPT_EJECUTABLE"),
                tuple(TipoMensaje.ADVERTENCIA, "CONFIGURACION_INCOMPLETA"),
                tuple(TipoMensaje.ADVERTENCIA, "FALTA_ESPACIO"),
                tuple(TipoMensaje.RECOMENDACION, "INCLUIR_ARCHIVELOG"),
                tuple(TipoMensaje.ADVERTENCIA, "SIN_RESPALDO_RECIENTE"));
        assertThat(alertasDeLaBase()).extracting(Alerta::getCodigo).containsExactly("NOARCHIVELOG");
        assertThat(base.getModoArchivado()).isEqualTo(ModoArchivado.NOARCHIVELOG);

        int antes = alertas.findAll().size();
        monitor.revisar(LocalDateTime.now().plusDays(2));
        assertThat(alertas.findAll()).hasSize(antes);
    }

    @Test
    void cierraSolaUnaAlertaCuandoLaCondicionDesaparece() {
        monitor.revisar(LocalDateTime.now());

        when(validador.validar(any(Estrategia.class)))
                .thenReturn(new ResultadoValidacion(null, true, LocalDateTime.now(), List.of()));
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.now());
        p.setHora(LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        estrategia.asignarProgramacion(p);
        aprobarScript();
        em.flush();

        monitor.revisar(LocalDateTime.now());

        assertThat(alertasDeLaEstrategia())
                .filteredOn(a -> List.of("SIN_PROGRAMACION", "SIN_SCRIPT_EJECUTABLE", "CONFIGURACION_INCOMPLETA",
                        "FALTA_ESPACIO", "INCLUIR_ARCHIVELOG").contains(a.getCodigo()))
                .hasSize(5)
                .allSatisfy(a -> {
                    assertThat(a.getEstado()).isEqualTo(EstadoAlerta.ATENDIDA);
                    assertThat(a.getAtendidaPor()).isEqualTo("sistema");
                });
    }

    @Test
    void contenedorDetenidoEInactivaSonAdvertencias() {
        when(cliente.contenedorEnEjecucion(anyString())).thenReturn(false);
        estrategia.setActiva(false);
        em.flush();

        monitor.revisar(LocalDateTime.now());

        assertThat(alertasDeLaBase()).extracting(Alerta::getCodigo).containsExactly("CONTENEDOR_DETENIDO");
        assertThat(alertasDeLaEstrategia()).extracting(Alerta::getCodigo).containsExactly("ESTRATEGIA_INACTIVA");
    }

    @Test
    void unaEjecucionFallidaSeCierraConUnExitoPosterior() {
        ScriptRman script = aprobarScript();
        Ejecucion fallida = ejecucion(script, EstadoEjecucion.FALLIDO, LocalDateTime.now().minusHours(2));
        Alerta alerta = new Alerta(TipoMensaje.ADVERTENCIA, "EJECUCION_FALLIDA", "Fallo");
        alerta.setEstrategia(estrategia);
        alerta.setEjecucion(fallida);
        alertas.save(alerta);

        monitor.revisar(LocalDateTime.now());
        assertThat(alerta.getEstado()).isEqualTo(EstadoAlerta.ABIERTA);

        ejecucion(script, EstadoEjecucion.EXITOSO, LocalDateTime.now().minusHours(1));
        monitor.revisar(LocalDateTime.now());
        assertThat(alerta.getEstado()).isEqualTo(EstadoAlerta.ATENDIDA);
        assertThat(alerta.getComentario()).contains("exitosa posterior");
    }

    @Test
    void aplicarLaRecomendacionDeArchivelogLaAgregaEInvalidaElScript() throws Exception {
        ScriptRman script = aprobarScript();
        monitor.revisar(LocalDateTime.now());
        Alerta recomendacion = alertasDeLaEstrategia().stream()
                .filter(a -> a.getCodigo().equals("INCLUIR_ARCHIVELOG")).findFirst().orElseThrow();

        mvc.perform(get("/api/alertas").param("estrategiaId", estrategia.getId().toString()).param("tipo", "RECOMENDACION"))
                .andExpect(jsonPath("$[0].aplicable").value(true));

        accion(recomendacion.getId(), "aplicar", "{\"atendidaPor\":\"Emmanuel Rodriguez\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADA"))
                .andExpect(jsonPath("$.comentario", containsString("Se agrego ARCHIVELOG")))
                .andExpect(jsonPath("$.comentario", containsString("Se invalidaron 1 script")));

        assertThat(estrategia.getElementos()).extracting(EstrategiaElemento::getTipoElemento)
                .contains(TipoElemento.ARCHIVELOG);
        assertThat(script.getEstado()).isEqualTo(EstadoScript.INVALIDADO);
    }

    @Test
    void soloLasRecomendacionesSeAplicanYLasDemasPidenComentario() throws Exception {
        Alerta advertencia = guardarAlerta(TipoMensaje.ADVERTENCIA, "FALTA_ESPACIO");
        accion(advertencia.getId(), "aplicar", "{\"atendidaPor\":\"admin\"}").andExpect(status().isConflict());

        Alerta otra = guardarAlerta(TipoMensaje.RECOMENDACION, "DEFINIR_RETENCION");
        accion(otra.getId(), "aplicar", "{\"atendidaPor\":\"admin\"}").andExpect(status().isBadRequest());
        accion(otra.getId(), "aplicar", "{\"atendidaPor\":\"admin\",\"comentario\":\"Se definieron 7 dias\"}")
                .andExpect(jsonPath("$.estado").value("APLICADA"))
                .andExpect(jsonPath("$.comentario").value("Se definieron 7 dias"));
    }

    @Test
    void atenderDescartarYResumen() throws Exception {
        Alerta a = guardarAlerta(TipoMensaje.ADVERTENCIA, "EJECUCION_OMITIDA");
        Alerta b = guardarAlerta(TipoMensaje.INFORMATIVO, "PRUEBA");

        mvc.perform(get("/api/alertas/resumen")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ADVERTENCIA").isNumber());

        accion(a.getId(), "atender", "{\"atendidaPor\":\"admin\",\"comentario\":\"Revisado\"}")
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));
        accion(b.getId(), "descartar", "{\"atendidaPor\":\"admin\"}")
                .andExpect(jsonPath("$.estado").value("DESCARTADA"));
        accion(a.getId(), "atender", "{\"atendidaPor\":\"admin\"}").andExpect(status().isConflict());
        accion(a.getId(), "atender", "{}").andExpect(status().isBadRequest());
    }

    // --- utilidades ---

    private List<Alerta> alertasDeLaEstrategia() {
        return alertas.findByEstrategiaIdOrderByIdDesc(estrategia.getId());
    }

    private List<Alerta> alertasDeLaBase() {
        return alertas.findAll().stream()
                .filter(a -> a.getEstrategia() == null && a.getBaseDatos() != null
                        && a.getBaseDatos().getId().equals(base.getId()))
                .toList();
    }

    private Alerta guardarAlerta(TipoMensaje tipo, String codigo) {
        Alerta a = new Alerta(tipo, codigo, "Prueba " + codigo);
        a.setEstrategia(estrategia);
        a.setBaseDatos(base);
        return alertas.saveAndFlush(a);
    }

    private ResultActions accion(Long id, String accion, String cuerpo) throws Exception {
        return mvc.perform(post("/api/alertas/{id}/" + accion, id).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ScriptRman aprobarScript() {
        ScriptRman s = new ScriptRman();
        s.setEstrategia(estrategia);
        s.setVersion(1);
        s.setContenido("RUN {\n  BACKUP DATABASE;\n}\n");
        s.setHashContenido(HuellaConfiguracion.sha256(s.getContenido()));
        s.setHashConfiguracion(HuellaConfiguracion.de(estrategia));
        s.setEstado(EstadoScript.APROBADO);
        s.setAprobadoPor("admin");
        s.setFechaAprobacion(LocalDateTime.now());
        return scripts.saveAndFlush(s);
    }

    private Ejecucion ejecucion(ScriptRman script, EstadoEjecucion estado, LocalDateTime inicio) {
        Ejecucion x = new Ejecucion();
        x.setEstrategia(estrategia);
        x.setScript(script);
        x.setBaseDatos(base);
        x.setOrigen(OrigenEjecucion.MANUAL);
        x.setEstado(estado);
        x.setTipoRespaldo(estrategia.getTipoRespaldo());
        x.setScriptEjecutado(script.getContenido());
        x.setRutaDestino(estrategia.getRutaDestino());
        x.setFechaInicio(inicio);
        x.setFechaFin(inicio.plusMinutes(2));
        x.setVerificacion(EstadoVerificacion.NO_APLICA);
        return ejecuciones.saveAndFlush(x);
    }
}
