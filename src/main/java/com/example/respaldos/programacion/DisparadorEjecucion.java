package com.example.respaldos.programacion;

import java.time.LocalDateTime;

/**
 * Lo que el programador llama cuando a una estrategia le toca ejecutarse. Solo recibe
 * estrategias activas con un script aprobado, vigente e integro.
 * La implementacion real (ejecutar RMAN y guardar la evidencia) es del modulo de ejecucion.
 */
public interface DisparadorEjecucion {

    /**
     * @param fechaProgramada ocurrencia de la programacion que se esta cumpliendo
     */
    void disparar(Long estrategiaId, Long scriptId, LocalDateTime fechaProgramada);
}
