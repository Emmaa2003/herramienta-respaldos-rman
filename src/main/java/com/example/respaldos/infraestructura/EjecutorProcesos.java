package com.example.respaldos.infraestructura;

import java.time.Duration;
import java.util.List;

/** Ejecuta un proceso del sistema operativo. Existe como interfaz para poder probar sin Docker. */
public interface EjecutorProcesos {

    /**
     * @param comando      programa y argumentos, sin pasar por un shell
     * @param entrada      texto que se envia por stdin (null si no hay)
     * @param tiempoMaximo si se supera, el proceso se detiene y el resultado queda con agotoTiempo
     */
    ResultadoProceso ejecutar(List<String> comando, String entrada, Duration tiempoMaximo);
}
