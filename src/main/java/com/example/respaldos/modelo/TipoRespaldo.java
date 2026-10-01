package com.example.respaldos.modelo;

/** COMO respaldar: tipo de respaldo RMAN. */
public enum TipoRespaldo {
    COMPLETO("Respaldo completo",
            "Copia de todos los bloques usados. Es independiente: no sirve como base para incrementales."),
    INCREMENTAL_NIVEL_0("Incremental nivel 0",
            "Copia completa que sirve como punto de partida de una estrategia incremental."),
    INCREMENTAL_NIVEL_1_DIFERENCIAL("Incremental nivel 1 diferencial",
            "Solo los bloques cambiados desde el ultimo incremental (nivel 0 o 1). Respaldos mas pequenos; "
                    + "para recuperar se aplican varios."),
    INCREMENTAL_NIVEL_1_ACUMULATIVO("Incremental nivel 1 acumulativo",
            "Los bloques cambiados desde el ultimo nivel 0. Respaldos mas grandes; para recuperar basta "
                    + "el nivel 0 y el ultimo acumulativo.");

    private final String nombre;
    private final String descripcion;

    TipoRespaldo(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() { return nombre; }

    public String getDescripcion() { return descripcion; }

    public boolean esIncremental() {
        return this != COMPLETO;
    }
}
