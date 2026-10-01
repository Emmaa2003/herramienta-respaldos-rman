package com.example.respaldos.aprobacion;

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
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revision del administrador contra el ambiente real (la aprobacion vuelve a validar).
 * No se ejecuta ningun script. Cada prueba hace rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AprobacionApiTest {

    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;
    @Autowired EntityManager em;

    @Test
    void apruebaElScriptRevisadoYQuedaComoEjecutable() throws Exception {
        Estrategia e = guardar("Aprobable");
        Generado g = generar(e);

        aprobar(g.id(), "Admin Pruebas", g.hash())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.script.estado").value("APROBADO"))
                .andExpect(jsonPath("$.script.aprobadoPor").value("Admin Pruebas"))
                .andExpect(jsonPath("$.script.fechaAprobacion").exists())
                .andExpect(jsonPath("$.script.comentarioRevision").value("Revisado"));

        mvc.perform(get("/api/estrategias/{id}/script-aprobado", e.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(g.id()));
    }

    @Test
    void sinScriptAprobadoNoHayNadaQueEjecutar() throws Exception {
        Estrategia e = guardar("Sin aprobar");
        generar(e);

        mvc.perform(get("/api/estrategias/{id}/script-aprobado", e.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("no tiene un script aprobado")));
    }

    @Test
    void laHuellaDebeSerLaDelScriptMostrado() throws Exception {
        Estrategia e = guardar("Huella");
        Generado g = generar(e);

        aprobar(g.id(), "admin", "0".repeat(64))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("no coincide")));
        aprobar(g.id(), "admin", "no-es-un-hash").andExpect(status().isBadRequest());
        aprobar(g.id(), " ", g.hash()).andExpect(status().isBadRequest());

        assertThat(scripts.findById(g.id()).orElseThrow().getEstado()).isEqualTo(EstadoScript.GENERADO);
    }

    @Test
    void soloSeRevisaUnScriptPendiente() throws Exception {
        Estrategia e = guardar("Doble");
        Generado g = generar(e);
        aprobar(g.id(), "admin", g.hash()).andExpect(status().isOk());

        aprobar(g.id(), "admin", g.hash())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("esta APROBADO")));
        rechazar(g.id()).andExpect(status().isConflict());
    }

    @Test
    void rechazarGuardaQuienYPorQueYYaNoSePuedeAprobar() throws Exception {
        Estrategia e = guardar("Rechazable");
        Generado g = generar(e);

        rechazar(g.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.rechazadoPor").value("Admin Pruebas"))
                .andExpect(jsonPath("$.comentarioRevision").value("Falta incluir archivelogs"));

        aprobar(g.id(), "admin", g.hash()).andExpect(status().isConflict());
        mvc.perform(post("/api/scripts/{id}/rechazo", g.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rechazadoPor\":\"admin\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void aprobarUnaVersionNuevaInvalidaLaAprobadaAnterior() throws Exception {
        Estrategia e = guardar("Versiones");
        Generado v1 = generar(e);
        aprobar(v1.id(), "admin", v1.hash()).andExpect(status().isOk());

        Generado v2 = generar(e);
        aprobar(v2.id(), "admin", v2.hash()).andExpect(status().isOk());

        assertThat(scripts.findById(v1.id()).orElseThrow().getEstado()).isEqualTo(EstadoScript.INVALIDADO);
        mvc.perform(get("/api/estrategias/{id}/script-aprobado", e.getId()))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void cambiarElComoDeLaEstrategiaInvalidaElScriptAprobado() throws Exception {
        Estrategia e = guardar("Cambia como");
        Generado g = generar(e);
        aprobar(g.id(), "admin", g.hash()).andExpect(status().isOk());

        mvc.perform(put("/api/estrategias/{id}", e.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(e, "INCREMENTAL_NIVEL_1_DIFERENCIAL", "otra persona")))
                .andExpect(status().isOk());

        assertThat(scripts.findById(g.id()).orElseThrow().getEstado()).isEqualTo(EstadoScript.INVALIDADO);
        mvc.perform(get("/api/estrategias/{id}/script-aprobado", e.getId())).andExpect(status().isNotFound());
    }

    @Test
    void cambiosQueNoAfectanAlScriptConservanLaAprobacion() throws Exception {
        Estrategia e = guardar("Cambia responsable");
        Generado g = generar(e);
        aprobar(g.id(), "admin", g.hash()).andExpect(status().isOk());

        mvc.perform(put("/api/estrategias/{id}", e.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(e, "INCREMENTAL_NIVEL_0", "otra persona")))
                .andExpect(status().isOk());

        assertThat(scripts.findById(g.id()).orElseThrow().getEstado()).isEqualTo(EstadoScript.APROBADO);
    }

    @Test
    void cambiarElServicioDeLaBaseInvalidaSusScripts() throws Exception {
        Estrategia e = guardar("Cambia base");
        Generado g = generar(e);
        aprobar(g.id(), "admin", g.hash()).andExpect(status().isOk());

        String base = """
                {"nombre":"%s","contenedor":"oracle-xe","servicio":"OTRAPDB","ambiente":"PRUEBAS"}"""
                .formatted(e.getBaseDatos().getNombre());
        mvc.perform(put("/api/bases-datos/{id}", e.getBaseDatos().getId())
                .contentType(MediaType.APPLICATION_JSON).content(base)).andExpect(status().isOk());

        assertThat(scripts.findById(g.id()).orElseThrow().getEstado()).isEqualTo(EstadoScript.INVALIDADO);
    }

    @Test
    void unScriptAlteradoDespuesDeAprobadoNoSePuedeEjecutar() throws Exception {
        Estrategia e = guardar("Alterado");
        Generado g = generar(e);
        aprobar(g.id(), "admin", g.hash()).andExpect(status().isOk());

        ScriptRman script = scripts.findById(g.id()).orElseThrow();
        script.setContenido(script.getContenido().replace("BACKUP", "DELETE NOPROMPT BACKUP"));
        em.flush();

        mvc.perform(get("/api/estrategias/{id}/script-aprobado", e.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("fue alterado")));
    }

    // --- utilidades ---

    private record Generado(Long id, String hash) {
    }

    private Generado generar(Estrategia e) throws Exception {
        String json = mvc.perform(post("/api/estrategias/{id}/scripts", e.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Generado(((Number) JsonPath.read(json, "$.script.id")).longValue(),
                JsonPath.read(json, "$.script.hashContenido"));
    }

    private ResultActions aprobar(Long id, String quien, String hash) throws Exception {
        String cuerpo = """
                {"aprobadoPor":"%s","hashContenido":"%s","comentario":"Revisado"}""".formatted(quien, hash);
        return mvc.perform(post("/api/scripts/{id}/aprobacion", id)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions rechazar(Long id) throws Exception {
        return mvc.perform(post("/api/scripts/{id}/rechazo", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rechazadoPor\":\"Admin Pruebas\",\"motivo\":\"Falta incluir archivelogs\"}"));
    }

    private static String solicitud(Estrategia e, String tipo, String responsable) {
        return """
                {"nombre":"%s","baseDatosId":%d,"responsable":"%s","prioridad":"ALTA",
                 "tipoRespaldo":"%s","rutaDestino":"/opt/oracle/oradata/respaldos","diasRetencion":7,
                 "elementos":[{"tipo":"BASE_DATOS"},{"tipo":"ARCHIVELOG"}],
                 "programacion":{"fechaInicio":"%s","hora":"23:00","frecuencia":"DIARIA"}}
                """.formatted(e.getNombre(), e.getBaseDatos().getId(), responsable, tipo, LocalDate.now());
    }

    private Estrategia guardar(String nombre) {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE aprobacion " + nombre);
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
        e.agregarElemento(new EstrategiaElemento(TipoElemento.BASE_DATOS, null));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.ARCHIVELOG, null));
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.now());
        p.setHora(LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        e.asignarProgramacion(p);
        return estrategias.saveAndFlush(e);
    }
}
