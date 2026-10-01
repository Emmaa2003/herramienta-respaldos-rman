package com.example.respaldos.estrategia;

import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.ScriptRman;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API de estrategias contra el esquema real. Cada prueba hace rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstrategiaApiTest {

    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;
    @Autowired ScriptRmanRepository scripts;
    @Autowired EntityManager em;

    private Long baseId;

    @BeforeEach
    void registrarBase() {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE estrategias");
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        baseId = bases.save(base).getId();
    }

    @Test
    void creaUnaEstrategiaCompletaInactivaYNormalizada() throws Exception {
        crear(semanal("Diaria XE"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.baseDatos.nombre").value("XE estrategias"))
                .andExpect(jsonPath("$.tipoRespaldo").value("INCREMENTAL_NIVEL_1_ACUMULATIVO"))
                .andExpect(jsonPath("$.rutaDestino").value("/opt/oracle/oradata/respaldos"))
                .andExpect(jsonPath("$.dispositivo").value("DISK"))
                .andExpect(jsonPath("$.elementos", hasSize(3)))
                .andExpect(jsonPath("$.elementos[?(@.tipo == 'TABLESPACE')].nombreObjeto").value("USERS"))
                .andExpect(jsonPath("$.programacion.frecuencia").value("SEMANAL"))
                .andExpect(jsonPath("$.programacion.diasSemana[0]").value("LUN"))
                .andExpect(jsonPath("$.programacion.diasSemana[1]").value("VIE"))
                .andExpect(jsonPath("$.programacion.hora").value("23:00:00"))
                .andExpect(jsonPath("$.programacion.intervalo").value(1));
    }

    @Test
    void listaYFiltraPorBaseYEstado() throws Exception {
        Long id = idDe(crear(semanal("Filtro A")));
        crear(semanal("Filtro B"));
        mvc.perform(post("/api/estrategias/{id}/activar", id)).andExpect(status().isOk());

        mvc.perform(get("/api/estrategias").param("baseDatosId", baseId.toString()))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/estrategias").param("baseDatosId", baseId.toString()).param("activa", "true"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Filtro A"));
    }

    @Test
    void erroresDeFormaSeInformanTodosJuntos() throws Exception {
        String cuerpo = """
                {"nombre":"Mala","baseDatosId":%d,"responsable":"admin","prioridad":"MEDIA",
                 "tipoRespaldo":"COMPLETO","rutaDestino":"/opt/oracle/oradata/respaldos",
                 "elementos":[{"tipo":"TABLESPACE"},{"tipo":"CONTROLFILE","nombreObjeto":"x"},
                              {"tipo":"DATAFILE","nombreObjeto":"users01.dbf"},
                              {"tipo":"SPFILE"},{"tipo":"SPFILE"}],
                 "programacion":{"fechaInicio":"2026-10-01","hora":"23:00","frecuencia":"SEMANAL",
                                 "ventanaInicio":"22:00"}}""".formatted(baseId);

        crearCon(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", allOf(
                        containsString("TABLESPACE necesita el nombre"),
                        containsString("CONTROLFILE no lleva nombre"),
                        containsString("no es un datafile valido"),
                        containsString("SPFILE esta repetido"),
                        containsString("SEMANAL necesita al menos un dia"),
                        containsString("ventana de respaldo necesita hora de inicio y de fin"))));
    }

    @Test
    void tipoDeRespaldoInexistenteDevuelve400ConDetalle() throws Exception {
        crearCon(semanal("Tipo malo").replace("INCREMENTAL_NIVEL_1_ACUMULATIVO", "INCREMENTAL_NIVEL_2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("INCREMENTAL_NIVEL_2")));
    }

    @Test
    void rutaDestinoConComillasEsRechazada() throws Exception {
        String cuerpo = semanal("Ruta mala").replace("/opt/oracle/oradata/respaldos/", "/tmp/x' ; DELETE");
        crearCon(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.rutaDestino").exists());
    }

    @Test
    void baseInexistenteYNombreDuplicado() throws Exception {
        crearCon(semanal("Sin base").replace("\"baseDatosId\":" + baseId, "\"baseDatosId\":999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("No existe la base de datos")));

        crear(semanal("Repetida")).andExpect(status().isCreated());
        crear(semanal("REPETIDA")).andExpect(status().isConflict());
    }

    @Test
    void modificarReemplazaElementosYConservaLaProgramacion() throws Exception {
        Long id = idDe(crear(semanal("Modificable")));
        Long programacionId = estrategias.findById(id).orElseThrow().getProgramacion().getId();

        // Se conserva BASE_DATOS, se quitan TABLESPACE y ARCHIVELOG y se agregan CONTROLFILE y SPFILE.
        String cambio = """
                {"nombre":"Modificable","baseDatosId":%d,"responsable":"otra persona","prioridad":"BAJA",
                 "tipoRespaldo":"COMPLETO","comprimido":false,"rutaDestino":"/opt/oracle/oradata/respaldos",
                 "elementos":[{"tipo":"BASE_DATOS"},{"tipo":"CONTROLFILE"},{"tipo":"SPFILE"}],
                 "programacion":{"fechaInicio":"2026-10-05","hora":"02:30","frecuencia":"DIARIA","intervalo":2}}
                """.formatted(baseId);
        mvc.perform(put("/api/estrategias/{id}", id).contentType(MediaType.APPLICATION_JSON).content(cambio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsable").value("otra persona"))
                .andExpect(jsonPath("$.elementos[*].tipo").value(
                        contains("BASE_DATOS", "CONTROLFILE", "SPFILE")))
                .andExpect(jsonPath("$.programacion.frecuencia").value("DIARIA"))
                .andExpect(jsonPath("$.programacion.diasSemana", hasSize(0)));

        em.clear();
        assertThat(estrategias.findById(id).orElseThrow().getProgramacion().getId()).isEqualTo(programacionId);

        // Sin programacion: se elimina.
        mvc.perform(put("/api/estrategias/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(cambio.replaceAll(",\\s*\"programacion\":\\{[^}]*}", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programacion").doesNotExist());
    }

    @Test
    void activarYDesactivar() throws Exception {
        Long id = idDe(crear(semanal("Activable")));

        mvc.perform(post("/api/estrategias/{id}/activar", id)).andExpect(jsonPath("$.activa").value(true));
        mvc.perform(post("/api/estrategias/{id}/desactivar", id)).andExpect(jsonPath("$.activa").value(false));
        mvc.perform(post("/api/estrategias/{id}/activar", 999_999_999)).andExpect(status().isNotFound());
    }

    @Test
    void soloSeEliminaUnaEstrategiaSinHistorial() throws Exception {
        Long sinHistorial = idDe(crear(semanal("Borrable")));
        mvc.perform(delete("/api/estrategias/{id}", sinHistorial)).andExpect(status().isNoContent());

        Long conScript = idDe(crear(semanal("Con script")));
        Estrategia estrategia = estrategias.findById(conScript).orElseThrow();
        ScriptRman script = new ScriptRman();
        script.setEstrategia(estrategia);
        script.setVersion(1);
        script.setContenido("BACKUP DATABASE;");
        script.setHashContenido("a".repeat(64));
        script.setHashConfiguracion("b".repeat(64));
        scripts.save(script);

        mvc.perform(delete("/api/estrategias/{id}", conScript))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Desactivela")));
    }

    @Test
    void opcionesIncluyenLosCriteriosDePrioridad() throws Exception {
        mvc.perform(get("/api/opciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridades[0].valor").value("ALTA"))
                .andExpect(jsonPath("$.prioridades[0].horasMaximasSinRespaldo").value(24))
                .andExpect(jsonPath("$.tiposRespaldo", hasSize(4)))
                .andExpect(jsonPath("$.elementos[?(@.valor == 'TABLESPACE')].requiereNombre").value(true))
                .andExpect(jsonPath("$.diasSemana", hasSize(7)));
    }

    private String semanal(String nombre) {
        return """
                {"nombre":"%s","descripcion":"Prueba","baseDatosId":%d,"responsable":"admin","prioridad":"ALTA",
                 "elementos":[{"tipo":"BASE_DATOS"},{"tipo":"TABLESPACE","nombreObjeto":" users "},
                              {"tipo":"ARCHIVELOG"}],
                 "tipoRespaldo":"INCREMENTAL_NIVEL_1_ACUMULATIVO","comprimido":true,"diasRetencion":14,
                 "rutaDestino":"/opt/oracle/oradata/respaldos/",
                 "programacion":{"fechaInicio":"2026-10-01","hora":"23:00","frecuencia":"SEMANAL",
                                 "diasSemana":["VIE","LUN"],"ventanaInicio":"22:00","ventanaFin":"05:00"}}
                """.formatted(nombre, baseId);
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return crearCon(cuerpo);
    }

    private ResultActions crearCon(String cuerpo) throws Exception {
        return mvc.perform(post("/api/estrategias").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static Long idDe(ResultActions resultado) throws Exception {
        String json = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
