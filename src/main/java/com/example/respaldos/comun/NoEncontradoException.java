package com.example.respaldos.comun;

/** El recurso pedido no existe (HTTP 404). */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
