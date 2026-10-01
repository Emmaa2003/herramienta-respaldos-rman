package com.example.respaldos.generador;

import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Generacion contra el ambiente real: valida, genera y pasa por rman checksyntax.
 * No ejecuta ningun respaldo. Cada prueba hace rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScriptApiTest {

    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;
    @Autowired EntityManager em;

    @Test
    void generaValidaVerificaSintaxisYGuardaComoGenerado() throws Exception {
        Estrategia e = guardar("Generable", TipoElemento.BASE_DATOS, TipoElemento.ARCHIVELOG, TipoElemento.CONTROLFILE);

        mvc.perform(post("/api/estrategias/{id}/scripts", e.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.script.version").value(1))
                .andExpect(jsonPath("$.script.estado").value("GENERADO"))
                .andExpect(jsonPath("$.script.vigente").value(true))
                .andExpect(jsonPath("$.script.contenido", containsString(
                        "BACKUP INCREMENTAL LEVEL 0 TAG 'EST" + e.getId() + "_N0'")))
                .andExpect(jsonPath("$.pasos", hasSize(5)))
                .andExpect(jsonPath("$.verificacionSintaxis", containsString("no tiene errores")))
                .andExpect(jsonPath("$.hallazgos[*].codigo", hasItem("DESTINO_MISMO_DISCO")));
    }

    @Test
    void unaVersionNuevaInvalidaLaAnteriorNoAprobada() throws Exception {
        Estrategia e = guardar("Versiones", TipoElemento.BASE_DATOS);

        Long primera = idDelScript(mvc.perform(post("/api/estrategias/{id}/scripts", e.getId()))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/estrategias/{id}/scripts", e.getId()))
                .andExpect(jsonPath("$.script.version").value(2));

        mvc.perform(get("/api/scripts/{id}", primera)).andExpect(jsonPath("$.estado").value("INVALIDADO"));
        mvc.perform(get("/api/estrategias/{id}/scripts", e.getId()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].version").value(2));
    }

    @Test
    void siLaEstrategiaCambiaElScriptDejaDeEstarVigente() throws Exception {
        Estrategia e = guardar("Cambiante", TipoElemento.BASE_DATOS);
        Long script = idDelScript(mvc.perform(post("/api/estrategias/{id}/scripts", e.getId()))
                .andReturn().getResponse().getContentAsString());

        e.setTipoRespaldo(TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL);
        em.flush();

        mvc.perform(get("/api/scripts/{id}", script)).andExpect(jsonPath("$.vigente").value(false));
    }

    @Test
    void unaEstrategiaInvalidaNoGeneraScript() throws Exception {
        Estrategia e = guardar("No generable", TipoElemento.CONTROLFILE);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "NO_EXISTE_XYZ"));
        em.flush();

        mvc.perform(post("/api/estrategias/{id}/scripts", e.getId()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("no paso la validacion")))
                .andExpect(jsonPath("$.hallazgos[0].codigo").value("TABLESPACE_INEXISTENTE"));

        assertThat(scripts.existsByEstrategiaId(e.getId())).isFalse();
    }

    private Estrategia guardar(String nombre, TipoElemento... elementos) {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE scripts " + nombre);
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        bases.save(base);

        Estrategia e = new Estrategia();
        e.setNombre(nombre);
        e.setBaseDatos(base);
        e.setResponsable("admin");
        e.setPrioridad(Prioridad.ALTA);
        e.setActiva(true);
        e.setTipoRespaldo(TipoRespaldo.INCREMENTAL_NIVEL_0);
        e.setDiasRetencion(7);
        e.setRutaDestino("/opt/oracle/oradata/respaldos");
        for (TipoElemento t : elementos) {
            e.agregarElemento(new EstrategiaElemento(t, null));
        }
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.now());
        p.setHora(LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        e.asignarProgramacion(p);
        return estrategias.saveAndFlush(e);
    }

    private static Long idDelScript(String json) {
        return ((Number) JsonPath.read(json, "$.script.id")).longValue();
    }
}
