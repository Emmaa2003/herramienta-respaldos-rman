package com.example.respaldos.ejecucion;

import com.example.respaldos.ejecucion.AnalizadorSalidaRman.Analisis;
import com.example.respaldos.ejecucion.AnalizadorSalidaRman.AnalisisVerificacion;
import com.example.respaldos.infraestructura.ResultadoProceso;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.EstadoVerificacion;

import java.util.ArrayList;
import java.util.List;

/**
 * Decide el resultado de una ejecucion (regla 7: Exitoso / Con advertencias / Fallido).
 * <ol>
 *   <li>FALLIDO si RMAN se corto por tiempo, devolvio error, mostro lineas RMAN-/ORA- o no
 *       llego a "Finished backup".</li>
 *   <li>FALLIDO si RMAN termino bien pero la verificacion encontro un problema: falta un
 *       archivo, una pieza no esta disponible o RESTORE VALIDATE fallo (regla 6).</li>
 *   <li>CON_ADVERTENCIAS si todo funciono pero RMAN salto archivos, o si no se pudo
 *       completar la verificacion por un problema del ambiente.</li>
 *   <li>EXITOSO en otro caso.</li>
 * </ol>
 */
public final class ClasificadorResultado {

    private ClasificadorResultado() {
    }

    /**
     * @param archivosFaltantes piezas que RMAN informo pero que no existen en el contenedor
     * @param verificacion      null si no se llego a verificar
     * @param errorVerificacion problema del ambiente al verificar (null si no hubo)
     */
    public record Entrada(ResultadoProceso proceso, Analisis respaldo, List<String> archivosFaltantes,
                          AnalisisVerificacion verificacion, int piezasVerificadas, String errorVerificacion) {
    }

    public record Resultado(EstadoEjecucion estado, EstadoVerificacion verificacion, String mensaje) {
    }

    /** Primera parte: solo con la salida del respaldo. Si no es FALLIDO, hay que verificar. */
    public static Resultado delRespaldo(ResultadoProceso proceso, Analisis a) {
        if (proceso.agotoTiempo()) {
            return fallido("Se agoto el tiempo maximo de ejecucion y se detuvo el cliente. RMAN puede seguir "
                    + "ejecutandose dentro del contenedor: revise antes de volver a ejecutar.");
        }
        if (!a.errores().isEmpty()) {
            return fallido("RMAN informo errores: " + String.join(" | ", a.erroresRelevantes()));
        }
        if (proceso.codigoSalida() == null || proceso.codigoSalida() != 0) {
            return fallido("RMAN termino con codigo " + proceso.codigoSalida() + ".");
        }
        if (!a.terminoBackup()) {
            boolean todoSaltado = a.advertencias().stream()
                    .anyMatch(x -> x.contains("backup cancelled because all files were skipped"));
            return todoSaltado
                    ? new Resultado(EstadoEjecucion.CON_ADVERTENCIAS, EstadoVerificacion.NO_APLICA,
                    "RMAN no copio archivos: todos se saltaron porque no habian cambiado.")
                    : fallido("RMAN termino sin el mensaje 'Finished backup': no hay constancia de que el "
                    + "respaldo se haya completado.");
        }
        if (a.piezas().isEmpty()) {
            return fallido("RMAN no informo ninguna pieza de respaldo: no hay archivo que sirva de evidencia.");
        }
        return null;
    }

    /** Resultado final, una vez verificado el respaldo. */
    public static Resultado finalizar(Entrada e) {
        Resultado delRespaldo = delRespaldo(e.proceso(), e.respaldo());
        if (delRespaldo != null) {
            return delRespaldo;
        }
        List<String> problemas = new ArrayList<>();
        if (!e.archivosFaltantes().isEmpty()) {
            problemas.add("no se encontraron los archivos " + String.join(", ", e.archivosFaltantes()));
        }
        if (e.verificacion() != null) {
            if (!e.verificacion().errores().isEmpty()) {
                problemas.add("la verificacion de RMAN informo errores: "
                        + String.join(" | ", e.verificacion().errores().stream()
                        .filter(x -> !x.contains("=====") && !x.contains("ERROR MESSAGE STACK")).toList()));
            }
            if (e.verificacion().expiradas() > 0) {
                problemas.add(e.verificacion().expiradas() + " pieza(s) quedaron EXPIRED en el CROSSCHECK");
            }
            if (e.verificacion().disponibles() < e.piezasVerificadas()) {
                problemas.add("el CROSSCHECK confirmo " + e.verificacion().disponibles() + " de "
                        + e.piezasVerificadas() + " piezas");
            }
        }
        if (!problemas.isEmpty()) {
            return new Resultado(EstadoEjecucion.FALLIDO, EstadoVerificacion.FALLIDA,
                    "El script termino sin errores, pero la verificacion fallo: " + String.join("; ", problemas) + ".");
        }
        if (e.errorVerificacion() != null) {
            return new Resultado(EstadoEjecucion.CON_ADVERTENCIAS, EstadoVerificacion.FALLIDA,
                    "El respaldo termino, pero no se pudo completar la verificacion: " + e.errorVerificacion());
        }
        if (!e.respaldo().advertencias().isEmpty()) {
            return new Resultado(EstadoEjecucion.CON_ADVERTENCIAS, EstadoVerificacion.VERIFICADO,
                    "Respaldo verificado, con advertencias de RMAN: " + String.join(" | ", e.respaldo().advertencias()));
        }
        return new Resultado(EstadoEjecucion.EXITOSO, EstadoVerificacion.VERIFICADO, null);
    }

    private static Resultado fallido(String mensaje) {
        return new Resultado(EstadoEjecucion.FALLIDO, EstadoVerificacion.NO_APLICA, mensaje);
    }
}
