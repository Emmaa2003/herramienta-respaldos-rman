package com.example.respaldos.generador;

import com.example.respaldos.generador.ScriptGenerado.Paso;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Traduce una estrategia (ya validada) a un script RMAN.
 * <p>
 * Reglas de traduccion:
 * <ol>
 *   <li>Todo va dentro de un bloque RUN { }: los comandos se ejecutan en orden y, si uno
 *       falla, RMAN no ejecuta los siguientes.</li>
 *   <li>Un comando BACKUP por cada grupo del QUE, en este orden: datafiles (base completa,
 *       o tablespaces y datafiles), archived redo logs, control file y SPFILE. El control
 *       file va despues de los datos para que registre los respaldos recien hechos.</li>
 *   <li>El COMO (completo / nivel 0 / nivel 1 diferencial / acumulativo) solo se aplica al
 *       comando de datafiles: los demas elementos siempre se respaldan completos.</li>
 *   <li>La compresion (AS COMPRESSED BACKUPSET) se aplica a todos los comandos.</li>
 *   <li>Cada comando lleva FORMAT con la ruta de destino y un TAG que identifica la
 *       estrategia y la parte respaldada.</li>
 * </ol>
 * No genera comandos que borren respaldos ni que cambien la base.
 */
@Component
public class GeneradorScriptRman {

    // Defensa adicional: los valores ya fueron validados antes, pero aqui se vuelven a
    // revisar porque terminan dentro del script.
    private static final Pattern RUTA = Pattern.compile("^/[A-Za-z0-9_./-]*$");
    private static final Pattern TABLESPACE = Pattern.compile("^[A-Z][A-Z0-9_$#]*(:[A-Z][A-Z0-9_$#]*)?$");
    private static final Pattern NUMERO = Pattern.compile("^[1-9][0-9]*$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ScriptGenerado generar(Estrategia e, int version, LocalDateTime fecha) {
        String ruta = e.getRutaDestino();
        if (!RUTA.matcher(ruta).matches() || ruta.contains("..")) {
            throw new IllegalArgumentException("Ruta de destino no valida para el script: " + ruta);
        }
        String prefijo = "EST" + e.getId();
        String formato = "'" + (ruta.equals("/") ? "" : ruta) + "/%d_" + prefijo + "_%T_%U'";
        String compresion = e.isComprimido() ? "AS COMPRESSED BACKUPSET " : "";
        String comoTexto = e.isComprimido() ? ", comprimido" : "";

        List<String> lineas = new ArrayList<>();
        List<Paso> pasos = new ArrayList<>();

        lineas.add("# Estrategia: " + comentario(e.getNombre()) + " (id " + e.getId() + "), script version " + version);
        lineas.add("# Base: " + comentario(e.getBaseDatos().getNombre()) + " (contenedor "
                + comentario(e.getBaseDatos().getContenedor()) + ", servicio "
                + comentario(e.getBaseDatos().getServicio()) + ")");
        lineas.add("# Generado: " + fecha.format(FECHA) + ". Revise el script antes de aprobarlo.");
        lineas.add("RUN {");
        pasos.add(new Paso("Estructura", "RUN { ... }",
                "Los comandos se ejecutan en orden dentro de un solo bloque. Si uno falla, RMAN no "
                        + "ejecuta los siguientes y el error queda en la salida."));
        pasos.add(new Paso("Destino: " + ruta, "FORMAT " + formato,
                "Cada archivo de respaldo se crea en la ruta de destino. %d es el nombre de la base, "
                        + "%T la fecha (AAAAMMDD) y %U un identificador unico que RMAN garantiza que no se repite."));

        // 1. Datafiles: base completa, o tablespaces y datafiles.
        String objetos = objetosDeDatos(e);
        if (objetos != null) {
            TipoRespaldo tipo = e.getTipoRespaldo();
            String tag = prefijo + "_" + sufijo(tipo);
            String comando = "BACKUP " + compresion + nivel(tipo) + "TAG '" + tag + "' FORMAT " + formato + " " + objetos + ";";
            String que = objetos.equals("DATABASE") ? "base de datos completa" : "tablespaces/datafiles: " + objetos;
            lineas.add("  # QUE: " + que + " | COMO: " + tipo.getNombre().toLowerCase(Locale.ROOT) + comoTexto);
            lineas.add("  " + comando);
            pasos.add(new Paso("QUE: " + que + " + COMO: " + tipo.getNombre() + comoTexto, comando,
                    explicacionDatos(tipo, objetos, e.isComprimido())));
        }

        // 2. Archived redo logs, 3. control file y 4. SPFILE: siempre completos.
        if (incluye(e, TipoElemento.ARCHIVELOG)) {
            agregarCompleto(lineas, pasos, compresion, comoTexto, formato, prefijo + "_ARC", "ARCHIVELOG ALL",
                    "archived redo logs",
                    "Respalda todos los archived redo logs disponibles. Junto con el respaldo de datos permiten "
                            + "recuperar hasta un punto en el tiempo.");
        }
        if (incluye(e, TipoElemento.CONTROLFILE)) {
            agregarCompleto(lineas, pasos, compresion, comoTexto, formato, prefijo + "_CTL", "CURRENT CONTROLFILE",
                    "control file",
                    "Respalda el control file actual. Va despues de los datos para que incluya el registro de "
                            + "los respaldos que se acaban de hacer.");
        }
        if (incluye(e, TipoElemento.SPFILE)) {
            agregarCompleto(lineas, pasos, compresion, comoTexto, formato, prefijo + "_SPF", "SPFILE",
                    "SPFILE",
                    "Respalda el archivo de parametros del servidor, necesario para arrancar la instancia "
                            + "si se pierde.");
        }

        if (lineas.size() == 4) {
            throw new IllegalArgumentException("La estrategia no tiene elementos que respaldar.");
        }
        lineas.add("}");
        return new ScriptGenerado(String.join("\n", lineas) + "\n", List.copyOf(pasos));
    }

    /** "DATABASE", o "TABLESPACE a, b DATAFILE 12, '/ruta'"; null si la estrategia no respalda datafiles. */
    private static String objetosDeDatos(Estrategia e) {
        if (incluye(e, TipoElemento.BASE_DATOS)) {
            return "DATABASE";
        }
        String pdb = e.getBaseDatos().getServicio().toUpperCase(Locale.ROOT);
        List<String> tablespaces = new ArrayList<>();
        List<String> datafiles = new ArrayList<>();
        for (EstrategiaElemento el : ordenados(e)) {
            String nombre = el.getNombreObjeto();
            if (el.getTipoElemento() == TipoElemento.TABLESPACE) {
                // RMAN se conecta a la raiz del CDB: los tablespaces del PDB se nombran PDB:TABLESPACE.
                String completo = nombre.contains(":") ? nombre : pdb + ":" + nombre;
                if (!TABLESPACE.matcher(completo).matches()) {
                    throw new IllegalArgumentException("Nombre de tablespace no valido para el script: " + nombre);
                }
                tablespaces.add(completo);
            } else if (el.getTipoElemento() == TipoElemento.DATAFILE) {
                if (NUMERO.matcher(nombre).matches()) {
                    datafiles.add(nombre);
                } else if (RUTA.matcher(nombre).matches() && !nombre.contains("..")) {
                    datafiles.add("'" + nombre + "'");
                } else {
                    throw new IllegalArgumentException("Datafile no valido para el script: " + nombre);
                }
            }
        }
        List<String> partes = new ArrayList<>();
        if (!tablespaces.isEmpty()) {
            partes.add("TABLESPACE " + String.join(", ", tablespaces));
        }
        if (!datafiles.isEmpty()) {
            partes.add("DATAFILE " + String.join(", ", datafiles));
        }
        return partes.isEmpty() ? null : String.join(" ", partes);
    }

    private static void agregarCompleto(List<String> lineas, List<Paso> pasos, String compresion, String comoTexto,
                                        String formato, String tag, String objeto, String que, String explicacion) {
        String comando = "BACKUP " + compresion + "TAG '" + tag + "' FORMAT " + formato + " " + objeto + ";";
        lineas.add("  # QUE: " + que + " | COMO: completo" + comoTexto);
        lineas.add("  " + comando);
        pasos.add(new Paso("QUE: " + que, comando, explicacion
                + " El tipo incremental no aplica a este elemento: siempre se respalda completo."));
    }

    private static String nivel(TipoRespaldo tipo) {
        return switch (tipo) {
            case COMPLETO -> "";
            case INCREMENTAL_NIVEL_0 -> "INCREMENTAL LEVEL 0 ";
            case INCREMENTAL_NIVEL_1_DIFERENCIAL -> "INCREMENTAL LEVEL 1 ";
            case INCREMENTAL_NIVEL_1_ACUMULATIVO -> "INCREMENTAL LEVEL 1 CUMULATIVE ";
        };
    }

    private static String sufijo(TipoRespaldo tipo) {
        return switch (tipo) {
            case COMPLETO -> "FULL";
            case INCREMENTAL_NIVEL_0 -> "N0";
            case INCREMENTAL_NIVEL_1_DIFERENCIAL -> "N1D";
            case INCREMENTAL_NIVEL_1_ACUMULATIVO -> "N1A";
        };
    }

    private static String explicacionDatos(TipoRespaldo tipo, String objetos, boolean comprimido) {
        String que = objetos.equals("DATABASE")
                ? "Respalda todos los datafiles de la base (en este ambiente, el CDB completo con sus PDB)."
                : "Respalda solo los tablespaces y datafiles elegidos; los tablespaces del PDB se escriben "
                + "como PDB:TABLESPACE porque RMAN se conecta a la raiz del CDB.";
        String como = switch (tipo) {
            case COMPLETO -> " BACKUP sin INCREMENTAL hace una copia completa independiente; no sirve como "
                    + "base para incrementales.";
            case INCREMENTAL_NIVEL_0 -> " INCREMENTAL LEVEL 0 copia todos los bloques usados y queda como "
                    + "punto de partida de los nivel 1.";
            case INCREMENTAL_NIVEL_1_DIFERENCIAL -> " INCREMENTAL LEVEL 1 (diferencial) copia solo los bloques "
                    + "cambiados desde el ultimo incremental, sea nivel 0 o nivel 1.";
            case INCREMENTAL_NIVEL_1_ACUMULATIVO -> " INCREMENTAL LEVEL 1 CUMULATIVE copia los bloques cambiados "
                    + "desde el ultimo nivel 0. En LIST BACKUP se ve igual que un diferencial: la aplicacion "
                    + "guarda el comando exacto para distinguirlos.";
        };
        String compresion = comprimido
                ? " AS COMPRESSED BACKUPSET reduce el tamano a cambio de mas uso de CPU."
                : "";
        return que + como + compresion;
    }

    private static boolean incluye(Estrategia e, TipoElemento tipo) {
        return e.getElementos().stream().anyMatch(el -> el.getTipoElemento() == tipo);
    }

    /** Orden estable para que la misma configuracion produzca siempre el mismo script. */
    private static List<EstrategiaElemento> ordenados(Estrategia e) {
        return e.getElementos().stream()
                .sorted((a, b) -> {
                    int t = a.getTipoElemento().compareTo(b.getTipoElemento());
                    return t != 0 ? t : String.valueOf(a.getNombreObjeto()).compareTo(String.valueOf(b.getNombreObjeto()));
                })
                .toList();
    }

    /** Un texto libre dentro de un comentario no debe poder abrir una linea nueva de comandos. */
    private static String comentario(String texto) {
        return texto == null ? "" : texto.replaceAll("[\\p{Cntrl}]", " ");
    }
}
