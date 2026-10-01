package com.example.respaldos.basedatos;

import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.ModoArchivado;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API de bases de datos contra el esquema real y el contenedor oracle-xe.
 * Cada prueba hace rollback. La inspeccion solo lee de Oracle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BaseDatosApiTest {

    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;

    @Test
    void registraYConsultaUnaBase() throws Exception {
        mvc.perform(post("/api/bases-datos").contentType(MediaType.APPLICATION_JSON).content(json("XE pruebas")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nombre").value("XE pruebas"))
                .andExpect(jsonPath("$.modoArchivado").doesNotExist());

        mvc.perform(get("/api/bases-datos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem("XE pruebas")));
    }

    @Test
    void nombreDuplicadoDevuelve409() throws Exception {
        mvc.perform(post("/api/bases-datos").contentType(MediaType.APPLICATION_JSON).content(json("XE dup")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/bases-datos").contentType(MediaType.APPLICATION_JSON).content(json("xe DUP")))
                .andExpect(status().isConflict());
    }

    @Test
    void contenedorNoPermitidoDevuelve400() throws Exception {
        String cuerpo = """
                {"nombre":"Local","contenedor":"oracle-local","servicio":"ORCL","ambiente":"PRUEBAS"}""";
        mvc.perform(post("/api/bases-datos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("no esta permitido")));
    }

    @Test
    void camposObligatoriosDevuelven400ConElDetalle() throws Exception {
        mvc.perform(post("/api/bases-datos").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.contenedor").exists())
                .andExpect(jsonPath("$.campos.ambiente").exists());
    }

    @Test
    void baseInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/bases-datos/{id}", 999_999_999)).andExpect(status().isNotFound());
    }

    @Test
    void cambiarDeServicioBorraLaInspeccionAnterior() throws Exception {
        BaseDatos base = guardar("XE cambio");
        base.setModoArchivado(ModoArchivado.ARCHIVELOG);

        String cuerpo = """
                {"nombre":"XE cambio","contenedor":"oracle-xe","servicio":"OTRAPDB","ambiente":"PRUEBAS"}""";
        mvc.perform(put("/api/bases-datos/{id}", base.getId()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio").value("OTRAPDB"))
                .andExpect(jsonPath("$.modoArchivado").doesNotExist());
    }

    @Test
    void noSePuedeEliminarUnaBaseConEstrategias() throws Exception {
        BaseDatos base = guardar("XE con estrategia");
        estrategias.save(estrategia(base, false));

        mvc.perform(delete("/api/bases-datos/{id}", base.getId())).andExpect(status().isConflict());

        BaseDatos sinEstrategias = guardar("XE sin estrategia");
        mvc.perform(delete("/api/bases-datos/{id}", sinEstrategias.getId())).andExpect(status().isNoContent());
    }

    @Test
    void inspeccionLeeElModoDeArchivadoYRecomiendaArchivelog() throws Exception {
        BaseDatos base = guardar("XE inspeccion");

        mvc.perform(post("/api/bases-datos/{id}/inspeccion", base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenedorActivo").value(true))
                .andExpect(jsonPath("$.pdb").value("XEPDB1"))
                .andExpect(jsonPath("$.modoArchivado").value("ARCHIVELOG"))
                .andExpect(jsonPath("$.tablespaces[*].nombre", hasItem("USERS")))
                .andExpect(jsonPath("$.mensajes[?(@.tipo == 'RECOMENDACION')].codigo", hasItem("INCLUIR_ARCHIVELOG")))
                .andExpect(jsonPath("$.mensajes[?(@.tipo == 'INFORMATIVO')].codigo", hasItem("MODO_ARCHIVELOG")));

        BaseDatos leida = bases.findById(base.getId()).orElseThrow();
        assertThat(leida.getModoArchivado()).isEqualTo(ModoArchivado.ARCHIVELOG);
        assertThat(leida.getFechaInspeccion()).isNotNull();
    }

    @Test
    void conUnaEstrategiaActivaQueRespaldaArchivelogNoSeRecomienda() throws Exception {
        BaseDatos base = guardar("XE cubierta");
        estrategias.save(estrategia(base, true));

        mvc.perform(post("/api/bases-datos/{id}/inspeccion", base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes[*].codigo", hasItem("ARCHIVELOG_CUBIERTO")))
                .andExpect(jsonPath("$.mensajes[*].codigo", not(hasItem("INCLUIR_ARCHIVELOG"))));
    }

    private BaseDatos guardar(String nombre) {
        BaseDatos base = new BaseDatos();
        base.setNombre(nombre);
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        return bases.save(base);
    }

    private static Estrategia estrategia(BaseDatos base, boolean conArchivelog) {
        Estrategia e = new Estrategia();
        e.setNombre("Estrategia de " + base.getNombre());
        e.setBaseDatos(base);
        e.setResponsable("admin");
        e.setPrioridad(Prioridad.ALTA);
        e.setActiva(true);
        e.setTipoRespaldo(TipoRespaldo.COMPLETO);
        e.setRutaDestino("/opt/oracle/oradata/respaldos");
        e.agregarElemento(new EstrategiaElemento(TipoElemento.BASE_DATOS, null));
        if (conArchivelog) {
            e.agregarElemento(new EstrategiaElemento(TipoElemento.ARCHIVELOG, null));
        }
        return e;
    }

    private static String json(String nombre) {
        return """
                {"nombre":"%s","descripcion":"Contenedor de pruebas","contenedor":"oracle-xe",
                 "servicio":"XEPDB1","ambiente":"PRUEBAS"}""".formatted(nombre);
    }
}
