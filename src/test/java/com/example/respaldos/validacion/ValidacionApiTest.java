package com.example.respaldos.validacion;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validacion contra el contenedor oracle-xe real (solo lectura). Cada prueba hace rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ValidacionApiTest {

    @Autowired MockMvc mvc;
    @Autowired BaseDatosRepository bases;
    @Autowired EstrategiaRepository estrategias;

    @Test
    void estrategiaCorrectaEsValidaEnElAmbienteReal() throws Exception {
        Estrategia e = guardar("Valida", TipoRespaldo.INCREMENTAL_NIVEL_0, "/opt/oracle/oradata/respaldos",
                TipoElemento.BASE_DATOS, TipoElemento.ARCHIVELOG);

        mvc.perform(post("/api/estrategias/{id}/validacion", e.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.hallazgos[*].codigo", hasItems("ESPACIO_DISPONIBLE", "DESTINO_MISMO_DISCO")))
                .andExpect(jsonPath("$.hallazgos[?(@.codigo == 'DESTINO_MISMO_DISCO')].tipo").value("RECOMENDACION"))
                .andExpect(jsonPath("$.hallazgos[*].codigo", not(hasItem("INCLUIR_ARCHIVELOG"))));
    }

    @Test
    void tablespaceDeXepdb1SeReconoceConYSinPrefijo() throws Exception {
        Estrategia e = guardar("Tablespaces", TipoRespaldo.COMPLETO, "/opt/oracle/oradata/respaldos",
                TipoElemento.CONTROLFILE);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "USERS"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "XEPDB1:SYSAUX"));

        mvc.perform(post("/api/estrategias/{id}/validacion", e.getId()))
                .andExpect(jsonPath("$.valida").value(true));
    }

    @Test
    void objetosYDestinoInexistentesBloquean() throws Exception {
        Estrategia e = guardar("Invalida", TipoRespaldo.COMPLETO, "/opt/oracle/oradata/no_existe",
                TipoElemento.CONTROLFILE);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "NO_EXISTE_XYZ"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "TEMP"));

        mvc.perform(post("/api/estrategias/{id}/validacion", e.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.hallazgos[?(@.bloqueante == true)].codigo",
                        hasItems("TABLESPACE_INEXISTENTE", "TABLESPACE_TEMPORAL", "DESTINO_INEXISTENTE")))
                .andExpect(jsonPath("$.hallazgos[0].bloqueante").value(true));
    }

    @Test
    void estrategiaInexistenteDevuelve404() throws Exception {
        mvc.perform(post("/api/estrategias/{id}/validacion", 999_999_999)).andExpect(status().isNotFound());
    }

    private Estrategia guardar(String nombre, TipoRespaldo tipo, String destino, TipoElemento... elementos) {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE validacion " + nombre);
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
        e.setTipoRespaldo(tipo);
        e.setDiasRetencion(7);
        e.setRutaDestino(destino);
        for (TipoElemento t : elementos) {
            e.agregarElemento(new EstrategiaElemento(t, null));
        }
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.now());
        p.setHora(LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        e.asignarProgramacion(p);
        return estrategias.save(e);
    }
}
