package com.example.respaldos.infraestructura;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unico punto por el que la app ejecuta algo en el ambiente Oracle: RMAN y los
 * comandos auxiliares (df, stat) corren dentro del contenedor con docker exec.
 * Solo acepta contenedores de respaldos.docker.contenedores-permitidos.
 */
@Component
public class ClienteContenedor {

    private final EjecutorProcesos ejecutor;
    private final PropiedadesDocker propiedades;

    public ClienteContenedor(EjecutorProcesos ejecutor, PropiedadesDocker propiedades) {
        this.ejecutor = ejecutor;
        this.propiedades = propiedades;
    }

    /**
     * Ejecuta un script con {@code rman target /}. El script entra por stdin tal cual,
     * sin agregarle nada, para que lo ejecutado sea exactamente lo aprobado.
     * RMAN termina al llegar al final de la entrada aunque el script no traiga EXIT.
     */
    public ResultadoProceso ejecutarRman(String contenedor, String script) {
        validarContenedor(contenedor);
        List<String> comando = List.of(propiedades.ejecutable(), "exec", "-i", contenedor, "rman", "target", "/");
        return ejecutor.ejecutar(comando, script, propiedades.tiempoMaximoRman());
    }

    /**
     * Revisa la sintaxis de un script con {@code rman checksyntax}: no se conecta a la base
     * ni ejecuta nada. Codigo 0 = "The command has no syntax errors".
     */
    public ResultadoProceso verificarSintaxisRman(String contenedor, String script) {
        validarContenedor(contenedor);
        List<String> comando = List.of(propiedades.ejecutable(), "exec", "-i", contenedor, "rman", "checksyntax");
        return ejecutor.ejecutar(comando, script, propiedades.tiempoMaximoComando());
    }

    /** Tamano en bytes del archivo, o vacio si no existe. Sirve para verificar las piezas de respaldo. */
    public Optional<Long> tamanoArchivo(String contenedor, String ruta) {
        ResultadoProceso r = comandoAuxiliar(contenedor, "stat", "-c", "%s", "--", validarRuta(ruta));
        if (r.terminoSinError()) {
            return Optional.of(Long.parseLong(r.salida().trim()));
        }
        if (r.salida().contains("No such file or directory")) {
            return Optional.empty();
        }
        throw fallo("stat " + ruta, r);
    }

    /** Espacio del sistema de archivos donde esta la ruta (df -Pk: bloques de 1024 bytes). */
    public EspacioDisco espacioDisco(String contenedor, String ruta) {
        ResultadoProceso r = comandoAuxiliar(contenedor, "df", "-Pk", "--", validarRuta(ruta));
        if (!r.terminoSinError()) {
            throw fallo("df " + ruta, r);
        }
        return interpretarDf(r.salida());
    }

    /** true si el contenedor existe y esta corriendo. No falla si Docker responde que no existe. */
    public boolean contenedorEnEjecucion(String contenedor) {
        validarContenedor(contenedor);
        List<String> comando = List.of(propiedades.ejecutable(), "inspect", "-f", "{{.State.Running}}", contenedor);
        ResultadoProceso r = ejecutor.ejecutar(comando, null, propiedades.tiempoMaximoComando());
        return r.terminoSinError() && r.salida().trim().equals("true");
    }

    static EspacioDisco interpretarDf(String salida) {
        // Formato POSIX: encabezado + una linea
        // Filesystem 1024-blocks Used Available Capacity Mounted-on
        String[] lineas = salida.strip().split("\\R");
        if (lineas.length < 2) {
            throw new AmbienteException("Salida de df inesperada: " + salida);
        }
        String[] c = lineas[lineas.length - 1].trim().split("\\s+");
        if (c.length < 6) {
            throw new AmbienteException("Salida de df inesperada: " + salida);
        }
        try {
            return new EspacioDisco(
                    c[0],
                    c[5],
                    Long.parseLong(c[1]) * 1024,
                    Long.parseLong(c[2]) * 1024,
                    Long.parseLong(c[3]) * 1024);
        } catch (NumberFormatException e) {
            throw new AmbienteException("Salida de df inesperada: " + salida, e);
        }
    }

    private ResultadoProceso comandoAuxiliar(String contenedor, String... argumentos) {
        validarContenedor(contenedor);
        List<String> comando = new ArrayList<>(List.of(propiedades.ejecutable(), "exec", contenedor));
        comando.addAll(List.of(argumentos));
        return ejecutor.ejecutar(comando, null, propiedades.tiempoMaximoComando());
    }

    private void validarContenedor(String contenedor) {
        if (contenedor == null || !propiedades.contenedoresPermitidos().contains(contenedor)) {
            throw new AmbienteException("El contenedor '" + contenedor
                    + "' no esta permitido. Permitidos: " + propiedades.contenedoresPermitidos());
        }
    }

    /** Solo rutas absolutas de Linux, sin '..' ni saltos de linea. */
    private static String validarRuta(String ruta) {
        if (ruta == null || !ruta.startsWith("/") || ruta.contains("..")
                || ruta.chars().anyMatch(ch -> ch == '\n' || ch == '\r' || ch == 0)) {
            throw new AmbienteException("Ruta no valida dentro del contenedor: " + ruta);
        }
        return ruta;
    }

    private static AmbienteException fallo(String descripcion, ResultadoProceso r) {
        String motivo = r.agotoTiempo() ? "se agoto el tiempo" : "codigo " + r.codigoSalida();
        return new AmbienteException("Fallo '" + descripcion + "' (" + motivo + "): " + r.salida().strip());
    }
}
