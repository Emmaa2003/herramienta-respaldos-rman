package com.example.respaldos.comun;

/** La operacion choca con el estado actual de los datos (HTTP 409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
