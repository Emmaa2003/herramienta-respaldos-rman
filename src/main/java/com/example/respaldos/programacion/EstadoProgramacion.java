package com.example.respaldos.programacion;

import com.example.respaldos.modelo.Frecuencia;

import java.time.LocalDateTime;

/**
 * Situacion de la programacion de una estrategia, tal como la veria el programador.
 *
 * @param estado  LISTA si se ejecutara a su hora; si no, por que no
 * @param detalle explicacion para el administrador
 */
public record EstadoProgramacion(
        Long estrategiaId,
        String estrategia,
        Frecuencia frecuencia,
        boolean programacionActiva,
        LocalDateTime proximaEjecucion,
        Estado estado,
        String detalle) {

    public enum Estado { LISTA, PROGRAMACION_INACTIVA, ESTRATEGIA_INACTIVA, SIN_SCRIPT_EJECUTABLE, SIN_PROXIMA }
}
