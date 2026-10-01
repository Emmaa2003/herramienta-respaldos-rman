package com.example.respaldos.programacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Implementacion provisional mientras no existe el modulo de ejecucion: registra en el
 * log que la ejecucion corresponde, pero no ejecuta RMAN. Se reemplaza en el modulo 8.
 */
@Component
public class DisparadorPendiente implements DisparadorEjecucion {

    private static final Logger log = LoggerFactory.getLogger(DisparadorPendiente.class);

    @Override
    public void disparar(Long estrategiaId, Long scriptId, LocalDateTime fechaProgramada) {
        log.warn("Corresponde ejecutar la estrategia {} (script {}, programada para {}), pero el modulo de "
                + "ejecucion todavia no existe: no se ejecuto RMAN.", estrategiaId, scriptId, fechaProgramada);
    }
}
