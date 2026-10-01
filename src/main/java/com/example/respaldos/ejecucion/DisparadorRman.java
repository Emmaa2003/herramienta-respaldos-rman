package com.example.respaldos.ejecucion;

import com.example.respaldos.programacion.DisparadorEjecucion;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Conecta el programador con la ejecucion real (reemplaza al disparador provisional del modulo 7). */
@Component
public class DisparadorRman implements DisparadorEjecucion {

    private final EjecucionService ejecucion;

    public DisparadorRman(EjecucionService ejecucion) {
        this.ejecucion = ejecucion;
    }

    @Override
    public void disparar(Long estrategiaId, Long scriptId, LocalDateTime fechaProgramada) {
        ejecucion.iniciarProgramada(estrategiaId, scriptId, fechaProgramada);
    }
}
