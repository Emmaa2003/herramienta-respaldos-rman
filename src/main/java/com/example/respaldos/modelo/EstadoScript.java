package com.example.respaldos.modelo;

/** Ciclo de vida de un script: solo uno APROBADO y vigente se puede ejecutar. */
public enum EstadoScript {
    GENERADO,
    APROBADO,
    RECHAZADO,
    INVALIDADO
}
