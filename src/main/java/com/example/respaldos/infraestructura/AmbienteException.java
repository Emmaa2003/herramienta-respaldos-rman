package com.example.respaldos.infraestructura;

/** Falla del ambiente: Docker no responde, un comando no se pudo ejecutar, falta un permiso, etc. */
public class AmbienteException extends RuntimeException {

    public AmbienteException(String mensaje) {
        super(mensaje);
    }

    public AmbienteException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
