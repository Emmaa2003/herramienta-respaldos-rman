package com.example.respaldos.validacion;

import com.example.respaldos.modelo.TipoMensaje;

/**
 * Resultado de una regla de validacion. Usa los tres tipos de mensaje del proyecto;
 * bloqueante marca las advertencias que impiden generar el script (la estrategia
 * esta incompleta o el script fallaria).
 */
public record Hallazgo(TipoMensaje tipo, String codigo, String texto, boolean bloqueante) {

    /** Impide generar el script. Siempre es una advertencia. */
    public static Hallazgo bloqueante(String codigo, String texto) {
        return new Hallazgo(TipoMensaje.ADVERTENCIA, codigo, texto, true);
    }

    public static Hallazgo advertencia(String codigo, String texto) {
        return new Hallazgo(TipoMensaje.ADVERTENCIA, codigo, texto, false);
    }

    public static Hallazgo recomendacion(String codigo, String texto) {
        return new Hallazgo(TipoMensaje.RECOMENDACION, codigo, texto, false);
    }

    public static Hallazgo informativo(String codigo, String texto) {
        return new Hallazgo(TipoMensaje.INFORMATIVO, codigo, texto, false);
    }
}
