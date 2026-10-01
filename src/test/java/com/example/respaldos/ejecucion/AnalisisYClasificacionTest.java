package com.example.respaldos.ejecucion;

import com.example.respaldos.ejecucion.AnalizadorSalidaRman.Analisis;
import com.example.respaldos.ejecucion.AnalizadorSalidaRman.Pieza;
import com.example.respaldos.ejecucion.ClasificadorResultado.Entrada;
import com.example.respaldos.ejecucion.ClasificadorResultado.Resultado;
import com.example.respaldos.ejecucion.VerificadorRespaldo.ObjetosRespaldados;
import com.example.respaldos.infraestructura.ResultadoProceso;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.EstadoVerificacion;
import com.example.respaldos.modelo.TipoEvidencia;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.respaldos.ejecucion.SalidasRman.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lectura de la salida de RMAN, script de verificacion y decision Exitoso / Con advertencias / Fallido. */
class AnalisisYClasificacionTest {

    private final AnalizadorSalidaRman analizador = new AnalizadorSalidaRman();
    private final VerificadorRespaldo verificador = new VerificadorRespaldo();

    @Test
    void reconoceElExitoYLasPiezasDistinguiendoElAutorespaldo() {
        Analisis a = analizador.analizarRespaldo(EXITOSA);

        assertThat(a.terminoBackup()).isTrue();
        assertThat(a.errores()).isEmpty();
        assertThat(a.piezas()).containsExactly(
                new Pieza(PIEZA, TipoEvidencia.PIEZA_RESPALDO, "EST7_N0"),
                new Pieza(AUTORESPALDO, TipoEvidencia.AUTORESPALDO_CONTROLFILE, null));
    }

    @Test
    void lasLineasRmanYOraSonErrores() {
        Analisis a = analizador.analizarRespaldo(CON_ERROR);

        assertThat(a.terminoBackup()).isFalse();
        assertThat(a.errores()).hasSize(6);
        assertThat(a.erroresRelevantes()).containsExactly(
                "RMAN-03009: failure of backup command on ORA_DISK_1 channel at 10/01/2026 04:00:00",
                "ORA-19504: failed to create file \"/opt/oracle/oradata/respaldos/XE_EST7_1\"",
                "ORA-27040: file create error, unable to create file");
    }

    @Test
    void saltarArchivosNoEsError() {
        Analisis a = analizador.analizarRespaldo(TODO_SALTADO);

        assertThat(a.errores()).isEmpty();
        assertThat(a.advertencias()).hasSize(2);
    }

    @Test
    void exitoVerificadoEsExitoso() {
        Resultado r = ClasificadorResultado.finalizar(entrada(EXITOSA, 0, List.of(), VERIFICACION_OK, null));

        assertThat(r.estado()).isEqualTo(EstadoEjecucion.EXITOSO);
        assertThat(r.verificacion()).isEqualTo(EstadoVerificacion.VERIFICADO);
        assertThat(r.mensaje()).isNull();
    }

    @Test
    void errorDeRmanEsFallidoYNoSeVerifica() {
        Resultado r = ClasificadorResultado.finalizar(entrada(CON_ERROR, 1, List.of(), null, null));

        assertThat(r.estado()).isEqualTo(EstadoEjecucion.FALLIDO);
        assertThat(r.verificacion()).isEqualTo(EstadoVerificacion.NO_APLICA);
        assertThat(r.mensaje()).contains("ORA-19504").doesNotContain("=====");
    }

    @Test
    void scriptSinErroresPeroSinArchivoEsFallido() {
        Resultado r = ClasificadorResultado.finalizar(entrada(EXITOSA, 0, List.of(PIEZA), VERIFICACION_OK, null));

        assertThat(r.estado()).isEqualTo(EstadoEjecucion.FALLIDO);
        assertThat(r.verificacion()).isEqualTo(EstadoVerificacion.FALLIDA);
        assertThat(r.mensaje()).startsWith("El script termino sin errores, pero la verificacion fallo")
                .contains(PIEZA);
    }

    @Test
    void piezaExpiradaEnElCrosscheckEsFallido() {
        Resultado r = ClasificadorResultado.finalizar(entrada(EXITOSA, 0, List.of(), VERIFICACION_EXPIRADA, null));

        assertThat(r.estado()).isEqualTo(EstadoEjecucion.FALLIDO);
        assertThat(r.mensaje()).contains("EXPIRED");
    }

    @Test
    void todoSaltadoOVerificacionIncompletaSonConAdvertencias() {
        Resultado saltado = ClasificadorResultado.finalizar(entrada(SALTADO_SIN_FINISHED, 0, List.of(), null, null));
        assertThat(saltado.estado()).isEqualTo(EstadoEjecucion.CON_ADVERTENCIAS);

        Resultado sinVerificar = ClasificadorResultado.finalizar(
                entrada(EXITOSA, 0, List.of(), null, "Docker no respondio"));
        assertThat(sinVerificar.estado()).isEqualTo(EstadoEjecucion.CON_ADVERTENCIAS);
        assertThat(sinVerificar.verificacion()).isEqualTo(EstadoVerificacion.FALLIDA);
    }

    @Test
    void tiempoAgotadoEsFallidoYAvisaQueRmanPuedeSeguir() {
        LocalDateTime t = LocalDateTime.now();
        ResultadoProceso cortado = new ResultadoProceso(null, "Starting backup at 01-OCT-26", t, t, true);

        Resultado r = ClasificadorResultado.delRespaldo(cortado, analizador.analizarRespaldo(cortado.salida()));

        assertThat(r.estado()).isEqualTo(EstadoEjecucion.FALLIDO);
        assertThat(r.mensaje()).contains("RMAN puede seguir");
    }

    @Test
    void scriptDeVerificacionUsaLasPiezasExactasYSoloLectura() {
        List<Pieza> piezas = analizador.analizarRespaldo(EXITOSA).piezas();

        String completo = verificador.script(15L, piezas, new ObjetosRespaldados(true, List.of(), List.of()));
        assertThat(completo).isEqualTo("""
                # Verificacion de la ejecucion 15: comprueba que las piezas existan y que se puedan restaurar.
                # Solo lectura: no crea, restaura ni borra respaldos.
                CROSSCHECK BACKUPPIECE '%s', '%s';
                RESTORE DATABASE VALIDATE;
                """.formatted(PIEZA, AUTORESPALDO));

        String parcial = verificador.script(16L, piezas,
                new ObjetosRespaldados(false, List.of("XEPDB1:USERS"), List.of("12")));
        assertThat(parcial).contains("RESTORE TABLESPACE XEPDB1:USERS VALIDATE;")
                .contains("RESTORE DATAFILE 12 VALIDATE;").doesNotContain("RESTORE DATABASE");
    }

    @Test
    void unaRutaDePiezaSospechosaNoEntraAlScript() {
        List<Pieza> piezas = List.of(new Pieza("/tmp/x'; DELETE NOPROMPT BACKUP; #", TipoEvidencia.PIEZA_RESPALDO, null));

        assertThatThrownBy(() -> verificador.script(1L, piezas, new ObjetosRespaldados(true, List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Entrada entrada(String salida, int codigo, List<String> faltantes, String verificacion,
                            String errorVerificacion) {
        LocalDateTime t = LocalDateTime.now();
        Analisis a = analizador.analizarRespaldo(salida);
        return new Entrada(new ResultadoProceso(codigo, salida, t, t, false), a, faltantes,
                verificacion == null ? null : analizador.analizarVerificacion(verificacion),
                a.piezas().size(), errorVerificacion);
    }
}
