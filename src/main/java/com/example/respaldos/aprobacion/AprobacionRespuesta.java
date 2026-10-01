package com.example.respaldos.aprobacion;

import com.example.respaldos.generador.ScriptRespuesta;
import com.example.respaldos.validacion.Hallazgo;

import java.util.List;

/** Script aprobado y los hallazgos (no bloqueantes) de la validacion hecha al aprobar. */
public record AprobacionRespuesta(ScriptRespuesta script, List<Hallazgo> hallazgos) {
}
