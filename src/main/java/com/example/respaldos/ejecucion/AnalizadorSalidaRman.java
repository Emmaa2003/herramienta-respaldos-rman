package com.example.respaldos.ejecucion;

import com.example.respaldos.modelo.TipoEvidencia;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee la salida de RMAN. Reglas del ambiente (CLAUDE.md):
 * <ul>
 *   <li>El exito de un respaldo se reconoce por "Finished backup".</li>
 *   <li>Los errores son las lineas RMAN-nnnnn / ORA-nnnnn.</li>
 *   <li>"skipping datafile ... has not changed" y "backup cancelled because all files were
 *       skipped" NO son errores: se registran como advertencias.</li>
 * </ul>
 * Tambien extrae las piezas creadas (piece handle=...) y distingue las del autorespaldo
 * del control file, que RMAN hace por su cuenta (CONTROLFILE AUTOBACKUP ON).
 */
@Component
public class AnalizadorSalidaRman {

    private static final Pattern ERROR = Pattern.compile("^(RMAN|ORA)-\\d{5}:.*");
    private static final Pattern PIEZA = Pattern.compile("piece handle=(\\S+)(?:\\s+tag=(\\S+))?");

    /** Lo que se encontro en la salida de un script de respaldo. */
    public record Analisis(boolean terminoBackup, List<String> errores, List<String> advertencias,
                           List<Pieza> piezas) {

        /** Errores sin las lineas decorativas de la pila de RMAN ("=====", "ERROR MESSAGE STACK"). */
        public List<String> erroresRelevantes() {
            return errores.stream()
                    .filter(e -> !e.contains("=====") && !e.contains("ERROR MESSAGE STACK FOLLOWS"))
                    .toList();
        }
    }

    public record Pieza(String ruta, TipoEvidencia tipo, String tag) {
    }

    /** Lo que se encontro en la salida del script de verificacion. */
    public record AnalisisVerificacion(List<String> errores, int disponibles, int expiradas) {
    }

    public Analisis analizarRespaldo(String salida) {
        boolean termino = false;
        boolean enAutorespaldo = false;
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        List<Pieza> piezas = new ArrayList<>();

        for (String linea : salida.lines().map(String::strip).toList()) {
            if (ERROR.matcher(linea).matches()) {
                errores.add(linea);
            } else if (linea.startsWith("Finished backup")) {
                termino = true;
            } else if (linea.contains("Starting Control File and SPFILE Autobackup")) {
                enAutorespaldo = true;
            } else if (linea.contains("Finished Control File and SPFILE Autobackup")) {
                enAutorespaldo = false;
            } else if (linea.startsWith("skipping") || linea.contains("backup cancelled because all files were skipped")) {
                advertencias.add(linea);
            }
            Matcher m = PIEZA.matcher(linea);
            if (m.find()) {
                piezas.add(new Pieza(m.group(1),
                        enAutorespaldo ? TipoEvidencia.AUTORESPALDO_CONTROLFILE : TipoEvidencia.PIEZA_RESPALDO,
                        m.group(2)));
            }
        }
        return new Analisis(termino, List.copyOf(errores), List.copyOf(advertencias), List.copyOf(piezas));
    }

    public AnalisisVerificacion analizarVerificacion(String salida) {
        List<String> errores = new ArrayList<>();
        int disponibles = 0;
        int expiradas = 0;
        for (String linea : salida.lines().map(String::strip).toList()) {
            if (ERROR.matcher(linea).matches()) {
                errores.add(linea);
            } else if (linea.contains("found to be 'AVAILABLE'")) {
                disponibles++;
            } else if (linea.contains("found to be 'EXPIRED'")) {
                expiradas++;
            }
        }
        return new AnalisisVerificacion(List.copyOf(errores), disponibles, expiradas);
    }
}
