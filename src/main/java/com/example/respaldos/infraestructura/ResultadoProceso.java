package com.example.respaldos.infraestructura;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Resultado crudo de un proceso externo. No interpreta la salida: decidir si un
 * respaldo fue Exitoso / Con advertencias / Fallido es trabajo del modulo de ejecucion.
 *
 * @param codigoSalida codigo de salida del proceso (null si se corto por tiempo)
 * @param salida       stdout y stderr combinados, en el orden en que llegaron
 * @param agotoTiempo  true si se supero el tiempo maximo y se detuvo el proceso
 */
public record ResultadoProceso(
        Integer codigoSalida,
        String salida,
        LocalDateTime inicio,
        LocalDateTime fin,
        boolean agotoTiempo) {

    public boolean terminoSinError() {
        return !agotoTiempo && codigoSalida != null && codigoSalida == 0;
    }

    public Duration duracion() {
        return Duration.between(inicio, fin);
    }
}
