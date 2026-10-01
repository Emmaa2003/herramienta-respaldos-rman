package com.example.respaldos.generador;

import java.util.List;

/**
 * Script RMAN y la explicacion de como se obtuvo.
 *
 * @param pasos una entrada por cada decision de la estrategia que se tradujo a RMAN
 */
public record ScriptGenerado(String contenido, List<Paso> pasos) {

    /**
     * @param origen      que parte de la estrategia produjo la instruccion (QUE / COMO / destino)
     * @param instruccion texto RMAN resultante
     * @param explicacion por que se genera asi
     */
    public record Paso(String origen, String instruccion, String explicacion) {
    }
}
