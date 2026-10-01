package com.example.respaldos.ejecucion;

import com.example.respaldos.ejecucion.AnalizadorSalidaRman.Pieza;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.TipoElemento;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Arma el script con el que se verifica un respaldo recien hecho (regla 6: que el script
 * termine sin errores no prueba que el respaldo sirva).
 * <ul>
 *   <li>CROSSCHECK BACKUPPIECE de las piezas de esta ejecucion: RMAN confirma que existen
 *       y quedan AVAILABLE. Se usan las piezas exactas, no el TAG, para no mezclar
 *       respaldos anteriores de la misma estrategia.</li>
 *   <li>RESTORE ... VALIDATE de lo que respalda la estrategia: RMAN lee los respaldos que
 *       usaria para restaurar y comprueba que esten completos y sin bloques danados. No
 *       restaura nada.</li>
 * </ul>
 * No genera comandos que creen, restauren o borren datos.
 */
@Component
public class VerificadorRespaldo {

    private static final Pattern RUTA_SEGURA = Pattern.compile("^/[A-Za-z0-9_./+-]*$");
    private static final Pattern TABLESPACE = Pattern.compile("^[A-Z][A-Z0-9_$#]*(:[A-Z][A-Z0-9_$#]*)?$");
    private static final Pattern NUMERO = Pattern.compile("^[1-9][0-9]*$");

    /** Lo que la estrategia respalda, tal como quedo en el script aprobado. */
    public record ObjetosRespaldados(boolean baseCompleta, List<String> tablespaces, List<String> datafiles) {

        public boolean incluyeDatos() {
            return baseCompleta || !tablespaces.isEmpty() || !datafiles.isEmpty();
        }
    }

    public String script(Long ejecucionId, List<Pieza> piezas, ObjetosRespaldados objetos) {
        List<String> lineas = new ArrayList<>();
        lineas.add("# Verificacion de la ejecucion " + ejecucionId
                + ": comprueba que las piezas existan y que se puedan restaurar.");
        lineas.add("# Solo lectura: no crea, restaura ni borra respaldos.");

        List<String> rutas = piezas.stream().map(Pieza::ruta).map(VerificadorRespaldo::rutaSegura).toList();
        if (!rutas.isEmpty()) {
            lineas.add("CROSSCHECK BACKUPPIECE " + String.join(", ", rutas.stream().map(r -> "'" + r + "'").toList()) + ";");
        }
        if (objetos.baseCompleta()) {
            lineas.add("RESTORE DATABASE VALIDATE;");
        } else {
            for (String ts : objetos.tablespaces()) {
                lineas.add("RESTORE TABLESPACE " + seguro(ts.toUpperCase(Locale.ROOT), TABLESPACE) + " VALIDATE;");
            }
            for (String df : objetos.datafiles()) {
                lineas.add("RESTORE DATAFILE " + (NUMERO.matcher(df).matches() ? df : "'" + rutaSegura(df) + "'")
                        + " VALIDATE;");
            }
        }
        return String.join("\n", lineas) + "\n";
    }

    /** Toma los objetos de datos de la estrategia, con el prefijo del PDB igual que el generador. */
    public static ObjetosRespaldados objetosDe(Estrategia e) {
        String pdb = e.getBaseDatos().getServicio().toUpperCase(Locale.ROOT);
        boolean completa = false;
        List<String> tablespaces = new ArrayList<>();
        List<String> datafiles = new ArrayList<>();
        for (var el : e.getElementos()) {
            if (el.getTipoElemento() == TipoElemento.BASE_DATOS) {
                completa = true;
            } else if (el.getTipoElemento() == TipoElemento.TABLESPACE) {
                String n = el.getNombreObjeto();
                tablespaces.add(n.contains(":") ? n : pdb + ":" + n);
            } else if (el.getTipoElemento() == TipoElemento.DATAFILE) {
                datafiles.add(el.getNombreObjeto());
            }
        }
        tablespaces.sort(null);
        datafiles.sort(null);
        return new ObjetosRespaldados(completa, List.copyOf(tablespaces), List.copyOf(datafiles));
    }

    /** Las rutas vienen de la salida de RMAN; igual se revisan antes de ponerlas entre comillas. */
    private static String rutaSegura(String ruta) {
        if (!RUTA_SEGURA.matcher(ruta).matches() || ruta.contains("..")) {
            throw new IllegalArgumentException("Ruta de pieza no valida para el script de verificacion: " + ruta);
        }
        return ruta;
    }

    private static String seguro(String valor, Pattern patron) {
        if (!patron.matcher(valor).matches()) {
            throw new IllegalArgumentException("Valor no valido para el script de verificacion: " + valor);
        }
        return valor;
    }
}
