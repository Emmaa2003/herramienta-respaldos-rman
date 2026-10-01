package com.example.respaldos.generador;

import com.example.respaldos.validacion.Hallazgo;

import java.util.List;

/**
 * Resultado de generar un script: el script guardado, como se tradujo cada parte de la
 * estrategia, los hallazgos no bloqueantes de la validacion y la verificacion de sintaxis.
 */
public record GeneracionRespuesta(
        ScriptRespuesta script,
        List<ScriptGenerado.Paso> pasos,
        List<Hallazgo> hallazgos,
        String verificacionSintaxis) {
}
