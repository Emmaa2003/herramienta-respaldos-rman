package com.example.respaldos.comun;

import com.example.respaldos.modelo.TipoMensaje;

/**
 * Mensaje para el administrador. El tipo distingue informativo, advertencia y
 * recomendacion; una recomendacion nunca se aplica sola.
 *
 * @param codigo identificador estable de la condicion (p. ej. NOARCHIVELOG)
 */
public record Mensaje(TipoMensaje tipo, String codigo, String texto) {

    public static Mensaje informativo(String codigo, String texto) {
        return new Mensaje(TipoMensaje.INFORMATIVO, codigo, texto);
    }

    public static Mensaje advertencia(String codigo, String texto) {
        return new Mensaje(TipoMensaje.ADVERTENCIA, codigo, texto);
    }

    public static Mensaje recomendacion(String codigo, String texto) {
        return new Mensaje(TipoMensaje.RECOMENDACION, codigo, texto);
    }
}
