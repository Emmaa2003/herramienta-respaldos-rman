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
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API de programacion contra el esquema real. Cada prueba hace rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProgramacionApiTest {

    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;

    @Test
    void muestraLasProximasEjecuciones() throws Exception {
        Estrategia e = estrategia("Proximas");

        mvc.perform(get("/api/estrategias/{id}/programacion/proximas", e.getId()).param("cantidad", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/estrategias/{id}/programacion/proximas", 999_999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void elEstadoExplicaPorQueNoSeEjecutariaYCuandoQuedaLista() throws Exception {
        Estrategia e = estrategia("Estado");

        mvc.perform(get("/api/programacion"))
                .andExpect(jsonPath("$[?(@.estrategiaId == %d)].estado".formatted(e.getId()))
                        .value("SIN_SCRIPT_EJECUTABLE"));

        aprobarScript(e);
        mvc.perform(get("/api/programacion"))
                .andExpect(jsonPath("$[?(@.estrategiaId == %d)].estado".formatted(e.getId())).value("LISTA"));

        mvc.perform(post("/api/estrategias/{id}/desactivar", e.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/programacion"))
                .andExpect(jsonPath("$[?(@.estrategiaId == %d)].estado".formatted(e.getId()))
                        .value("ESTRATEGIA_INACTIVA"));
    }

    @Test
    void activarYDesactivarLaProgramacion() throws Exception {
        Estrategia e = estrategia("Interruptor");
        e.getProgramacion().setProximaEjecucion(LocalDateTime.of(2020, 1, 1, 0, 0));

        mvc.perform(post("/api/estrategias/{id}/programacion/desactivar", e.getId()))
                .andExpect(jsonPath("$.programacionActiva").value(false))
                .andExpect(jsonPath("$.estado").value("PROGRAMACION_INACTIVA"));

        mvc.perform(post("/api/estrategias/{id}/programacion/activar", e.getId()))
                .andExpect(jsonPath("$.programacionActiva").value(true));
        // Al activar se recalcula desde ahora: la fecha vieja de 2020 no queda pendiente.
        assertThat(e.getProgramacion().getProximaEjecucion()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void guardarLaEstrategiaCalculaLaProximaEjecucion() throws Exception {
        BaseDatos base = base("Guardar");
        String cuerpo = """
                {"nombre":"Calculada","baseDatosId":%d,"responsable":"admin","prioridad":"ALTA",
                 "tipoRespaldo":"COMPLETO","rutaDestino":"/opt/oracle/oradata/respaldos",
                 "elementos":[{"tipo":"BASE_DATOS"}],
                 "programacion":{"fechaInicio":"%s","hora":"23:00","frecuencia":"DIARIA"}}
                """.formatted(base.getId(), LocalDate.now());

        mvc.perform(post("/api/estrategias").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programacion.proximaEjecucion").exists());
    }

    private void aprobarScript(Estrategia e) {
        ScriptRman s = new ScriptRman();
        s.setEstrategia(e);
        s.setVersion(1);
        s.setContenido("RUN {\n  BACKUP DATABASE;\n}\n");
        s.setHashContenido(HuellaConfiguracion.sha256(s.getContenido()));
        s.setHashConfiguracion(HuellaConfiguracion.de(e));
        s.setEstado(EstadoScript.APROBADO);
        s.setAprobadoPor("admin");
        s.setFechaAprobacion(LocalDateTime.now());
        scripts.saveAndFlush(s);
    }

    private BaseDatos base(String nombre) {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE programacion " + nombre);
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        return bases.save(base);
    }

    private Estrategia estrategia(String nombre) {
        Estrategia e = new Estrategia();
        e.setNombre("Programacion " + nombre);
        e.setBaseDatos(base(nombre));
        e.setResponsable("admin");
        e.setPrioridad(Prioridad.ALTA);
        e.setActiva(true);
        e.setTipoRespaldo(TipoRespaldo.COMPLETO);
        e.setRutaDestino("/opt/oracle/oradata/respaldos");
        e.agregarElemento(new EstrategiaElemento(TipoElemento.BASE_DATOS, null));
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.now());
        p.setHora(LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        e.asignarProgramacion(p);
        return estrategias.saveAndFlush(e);
    }
}
