package com.example.respaldos.comun;

/** Los datos enviados no cumplen una regla que no cubre la validacion de campos (HTTP 400). */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
