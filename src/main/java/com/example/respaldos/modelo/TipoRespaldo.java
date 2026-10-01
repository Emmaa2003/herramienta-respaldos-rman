package com.example.respaldos.modelo;

/** COMO respaldar: tipo de respaldo RMAN. */
public enum TipoRespaldo {
    COMPLETO,
    INCREMENTAL_NIVEL_0,
    INCREMENTAL_NIVEL_1_DIFERENCIAL,
    INCREMENTAL_NIVEL_1_ACUMULATIVO
}
