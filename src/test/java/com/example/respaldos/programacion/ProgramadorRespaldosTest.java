package com.example.respaldos.programacion;

import com.example.respaldos.generador.HuellaConfiguracion;
import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.EstadoScript;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.ScriptRman;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import com.example.respaldos.programacion.ProgramadorRespaldos.Decision;
import com.example.respaldos.programacion.ProgramadorRespaldos.Resultado;
import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Programador con horas fijas. El disparador es un simulacro: no se ejecuta RMAN.
 * Cada prueba hace rollback.
 */
@SpringBootTest
@Transactional
class ProgramadorRespaldosTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 1, 23, 0);

    @Autowired ProgramadorRespaldos programador;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;
    @Autowired AlertaRepository alertas;
    @MockitoBean DisparadorEjecucion disparador;

    @Test
    void calculaLaPrimeraEjecucionYNoHaceNadaAntesDeTiempo() {
        Estrategia e = estrategia("Inicial", true);
        e.getProgramacion().setProximaEjecucion(null);

        assertThat(decision(e, "2026-10-01T12:00").resultado()).isEqualTo(Resultado.INICIALIZADA);
        assertThat(e.getProgramacion().getProximaEjecucion()).isEqualTo(INICIO);
        assertThat(decision(e, "2026-10-01T22:59").resultado()).isEqualTo(Resultado.NO_CORRESPONDE);
        verify(disparador, never()).disparar(anyLong(), anyLong(), any());
    }

    @Test
    void cuandoLlegaLaHoraDisparaConElScriptAprobadoYAvanza() {
        Estrategia e = estrategia("Disparable", true);
        ScriptRman script = scriptAprobado(e);

        Decision d = decision(e, "2026-10-01T23:00:40");

        assertThat(d.resultado()).isEqualTo(Resultado.DISPARADA);
        verify(disparador).disparar(e.getId(), script.getId(), INICIO);
        assertThat(e.getProgramacion().getProximaEjecucion()).isEqualTo(INICIO.plusDays(1));

        // La misma revision repetida no vuelve a disparar.
        assertThat(decision(e, "2026-10-01T23:01").resultado()).isEqualTo(Resultado.NO_CORRESPONDE);
    }

    @Test
    void sinScriptAprobadoNoEjecutaYAlertaUnaSolaVez() {
        Estrategia e = estrategia("Sin script", true);

        assertThat(decision(e, "2026-10-01T23:05").resultado()).isEqualTo(Resultado.SIN_SCRIPT);
        assertThat(decision(e, "2026-10-02T23:05").resultado()).isEqualTo(Resultado.SIN_SCRIPT);

        verify(disparador, never()).disparar(anyLong(), anyLong(), any());
        assertThat(alertas.findByEstrategiaIdOrderByIdDesc(e.getId())).singleElement()
                .satisfies(a -> {
                    assertThat(a.getCodigo()).isEqualTo("SIN_SCRIPT_EJECUTABLE");
                    assertThat(a.getMensaje()).contains("no tiene un script aprobado");
                });
    }

    @Test
    void unaEjecucionMuyAtrasadaNoSeHaceTardeSeRegistraComoOmitida() {
        Estrategia e = estrategia("Atrasada", true);
        scriptAprobado(e);

        Decision d = decision(e, "2026-10-03T10:00");

        assertThat(d.resultado()).isEqualTo(Resultado.OMITIDA);
        assertThat(d.fechaProgramada()).isEqualTo(INICIO);
        assertThat(e.getProgramacion().getProximaEjecucion()).isEqualTo(LocalDateTime.of(2026, 10, 3, 23, 0));
        verify(disparador, never()).disparar(anyLong(), anyLong(), any());
        assertThat(alertas.findByEstrategiaIdOrderByIdDesc(e.getId())).singleElement()
                .satisfies(a -> assertThat(a.getCodigo()).isEqualTo("EJECUCION_OMITIDA"));
    }

    @Test
    void dentroDeLaToleranciaTodaviaSeEjecuta() {
        Estrategia e = estrategia("Con retraso", true);
        scriptAprobado(e);

        assertThat(decision(e, "2026-10-01T23:25").resultado()).isEqualTo(Resultado.DISPARADA);
    }

    @Test
    void unaEstrategiaInactivaNoSeRevisa() {
        Estrategia e = estrategia("Inactiva", false);
        scriptAprobado(e);

        assertThat(programador.revisar(LocalDateTime.parse("2026-10-01T23:05")))
                .noneMatch(d -> d.estrategiaId().equals(e.getId()));
        verify(disparador, never()).disparar(anyLong(), anyLong(), any());
    }

    private Decision decision(Estrategia e, String ahora) {
        return programador.revisar(LocalDateTime.parse(ahora)).stream()
                .filter(d -> d.estrategiaId().equals(e.getId())).findFirst().orElseThrow();
    }

    private ScriptRman scriptAprobado(Estrategia e) {
        ScriptRman s = new ScriptRman();
        s.setEstrategia(e);
        s.setVersion(1);
        s.setContenido("RUN {\n  BACKUP DATABASE;\n}\n");
        s.setHashContenido(HuellaConfiguracion.sha256(s.getContenido()));
        s.setHashConfiguracion(HuellaConfiguracion.de(e));
        s.setEstado(EstadoScript.APROBADO);
        s.setAprobadoPor("admin");
        s.setFechaAprobacion(LocalDateTime.now());
        return scripts.saveAndFlush(s);
    }

    private Estrategia estrategia(String nombre, boolean activa) {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE programador " + nombre);
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        bases.save(base);

        Estrategia e = new Estrategia();
        e.setNombre("Programador " + nombre);
        e.setBaseDatos(base);
        e.setResponsable("admin");
        e.setPrioridad(Prioridad.ALTA);
        e.setActiva(activa);
        e.setTipoRespaldo(TipoRespaldo.COMPLETO);
        e.setRutaDestino("/opt/oracle/oradata/respaldos");
        e.agregarElemento(new EstrategiaElemento(TipoElemento.BASE_DATOS, null));
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.of(2026, 10, 1));
        p.setHora(LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        p.setProximaEjecucion(INICIO);
        e.asignarProgramacion(p);
        return estrategias.saveAndFlush(e);
    }
}
