package com.example.respaldos.modelo;

/** QUE respaldar: elementos que puede incluir una estrategia. */
public enum TipoElemento {
    BASE_DATOS("Base de datos completa", false),
    TABLESPACE("Tablespace", true),
    DATAFILE("Datafile (numero o ruta)", true),
    CONTROLFILE("Control file", false),
    SPFILE("SPFILE", false),
    ARCHIVELOG("Archived redo logs", false);

    private final String descripcion;
    private final boolean requiereNombre;

    TipoElemento(String descripcion, boolean requiereNombre) {
        this.descripcion = descripcion;
        this.requiereNombre = requiereNombre;
    }

    public String getDescripcion() { return descripcion; }

    /** true si el elemento necesita indicar cual objeto (nombre de tablespace o datafile). */
    public boolean requiereNombre() { return requiereNombre; }
}
