package com.example.respaldos.modelo;

import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EjecucionRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guarda y relee el flujo completo contra el esquema real (Oracle XE en oracle-xe).
 * Cada prueba hace rollback: no deja datos.
 */
@SpringBootTest
@Transactional
class ModeloPersistenciaTest {

    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;
    @Autowired EjecucionRepository ejecuciones;
    @Autowired AlertaRepository alertas;
    @Autowired EntityManager em;

    @Test
    void guardaYRecuperaElFlujoCompleto() {
        BaseDatos base = nuevaBase();
        Estrategia estrategia = nuevaEstrategia(base);
        estrategia.agregarElemento(new EstrategiaElemento(TipoElemento.BASE_DATOS, null));
        estrategia.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "USERS"));
        estrategia.agregarElemento(new EstrategiaElemento(TipoElemento.ARCHIVELOG, null));

        Programacion programacion = new Programacion();
        programacion.setFechaInicio(LocalDate.of(2026, 10, 1));
        programacion.setHora(LocalTime.of(23, 0));
        programacion.setFrecuencia(Frecuencia.SEMANAL);
        programacion.setDiasSemana(EnumSet.of(DiaSemana.LUN, DiaSemana.JUE));
        programacion.setVentanaInicio(LocalTime.of(22, 30));
        programacion.setVentanaFin(LocalTime.of(5, 0));
        estrategia.asignarProgramacion(programacion);
        estrategias.save(estrategia);

        ScriptRman script = new ScriptRman();
        script.setEstrategia(estrategia);
        script.setVersion(1);
        script.setContenido("RUN {\n  BACKUP INCREMENTAL LEVEL 1 CUMULATIVE DATABASE;\n}");
        script.setHashContenido("a".repeat(64));
        script.setHashConfiguracion("b".repeat(64));
        script.setEstado(EstadoScript.APROBADO);
        script.setAprobadoPor("admin");
        script.setFechaAprobacion(LocalDateTime.now());
        scripts.save(script);

        Ejecucion ejecucion = new Ejecucion();
        ejecucion.setEstrategia(estrategia);
        ejecucion.setScript(script);
        ejecucion.setBaseDatos(base);
        ejecucion.setOrigen(OrigenEjecucion.MANUAL);
        ejecucion.setTipoRespaldo(estrategia.getTipoRespaldo());
        ejecucion.setScriptEjecutado(script.getContenido());
        ejecucion.setRutaDestino(estrategia.getRutaDestino());
        ejecucion.setFechaInicio(LocalDateTime.now());
        ejecucion.setEstado(EstadoEjecucion.EXITOSO);
        EvidenciaArchivo pieza = new EvidenciaArchivo(
                "/opt/oracle/oradata/respaldos/prueba_01.bkp", TipoEvidencia.PIEZA_RESPALDO);
        pieza.setExiste(true);
        ejecucion.agregarArchivo(pieza);
        ejecuciones.save(ejecucion);

        Alerta recomendacion = new Alerta(TipoMensaje.RECOMENDACION, "INCLUIR_ARCHIVELOG",
                "Considere incorporar el respaldo periodico de los archived redo logs.");
        recomendacion.setBaseDatos(base);
        recomendacion.setEstado(EstadoAlerta.APLICADA);
        alertas.save(recomendacion);

        em.flush();
        em.clear();

        Estrategia leida = estrategias.findById(estrategia.getId()).orElseThrow();
        assertThat(leida.isActiva()).isTrue();
        assertThat(leida.getTipoRespaldo()).isEqualTo(TipoRespaldo.INCREMENTAL_NIVEL_1_ACUMULATIVO);
        assertThat(leida.getElementos()).hasSize(3);
        assertThat(leida.getProgramacion().getHora()).isEqualTo(LocalTime.of(23, 0));
        assertThat(leida.getProgramacion().getVentanaFin()).isEqualTo(LocalTime.of(5, 0));
        assertThat(leida.getProgramacion().getDiasSemana()).containsExactly(DiaSemana.LUN, DiaSemana.JUE);
        assertThat(leida.getFechaCreacion()).isNotNull();

        Ejecucion ejecucionLeida = ejecuciones.findById(ejecucion.getId()).orElseThrow();
        assertThat(ejecucionLeida.getArchivos()).singleElement()
                .satisfies(a -> assertThat(a.isExiste()).isTrue());
        assertThat(ejecucionLeida.getVerificacion()).isEqualTo(EstadoVerificacion.PENDIENTE);
    }

    @Test
    void tablespaceSinNombreEsRechazadoPorLaBase() {
        Estrategia estrategia = nuevaEstrategia(nuevaBase());
        estrategia.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, null));

        assertThatThrownBy(() -> estrategias.saveAndFlush(estrategia))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void soloLasRecomendacionesPuedenQuedarAplicadas() {
        Alerta advertencia = new Alerta(TipoMensaje.ADVERTENCIA, "NOARCHIVELOG",
                "La base de datos se encuentra en modo NOARCHIVELOG.");
        advertencia.setEstado(EstadoAlerta.APLICADA);

        assertThatThrownBy(() -> alertas.saveAndFlush(advertencia))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private BaseDatos nuevaBase() {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE pruebas modelo");
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        base.setModoArchivado(ModoArchivado.ARCHIVELOG);
        return bases.save(base);
    }

    private Estrategia nuevaEstrategia(BaseDatos base) {
        Estrategia estrategia = new Estrategia();
        estrategia.setNombre("Prueba modelo");
        estrategia.setBaseDatos(base);
        estrategia.setResponsable("admin");
        estrategia.setPrioridad(Prioridad.ALTA);
        estrategia.setActiva(true);
        estrategia.setTipoRespaldo(TipoRespaldo.INCREMENTAL_NIVEL_1_ACUMULATIVO);
        estrategia.setComprimido(true);
        estrategia.setRutaDestino("/opt/oracle/oradata/respaldos");
        return estrategia;
    }
}
