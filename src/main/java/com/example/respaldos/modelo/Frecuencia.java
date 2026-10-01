package com.example.respaldos.modelo;

/** CUANDO respaldar: frecuencia de la programacion. El intervalo indica cada cuantas unidades. */
public enum Frecuencia {
    UNA_VEZ("Una sola vez, en la fecha de inicio"),
    CADA_N_HORAS("Cada N horas a partir de la hora indicada"),
    DIARIA("Cada N dias"),
    SEMANAL("Los dias de la semana indicados, cada N semanas"),
    MENSUAL("Cada N meses, el mismo dia del mes que la fecha de inicio");

    private final String descripcion;

    Frecuencia(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() { return descripcion; }
}
