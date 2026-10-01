package com.example.respaldos.modelo;

import java.time.DayOfWeek;

/** Dias de ejecucion de una programacion SEMANAL. */
public enum DiaSemana {
    LUN(DayOfWeek.MONDAY),
    MAR(DayOfWeek.TUESDAY),
    MIE(DayOfWeek.WEDNESDAY),
    JUE(DayOfWeek.THURSDAY),
    VIE(DayOfWeek.FRIDAY),
    SAB(DayOfWeek.SATURDAY),
    DOM(DayOfWeek.SUNDAY);

    private final DayOfWeek dia;

    DiaSemana(DayOfWeek dia) {
        this.dia = dia;
    }

    public DayOfWeek aDayOfWeek() { return dia; }
}
