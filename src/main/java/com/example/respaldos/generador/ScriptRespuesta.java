package com.example.respaldos.generador;

import com.example.respaldos.modelo.EstadoScript;
import com.example.respaldos.modelo.ScriptRman;

import java.time.LocalDateTime;

/**
 * @param vigente true si la estrategia no cambio desde que se genero el script
 *                (la huella de configuracion coincide)
 */
public record ScriptRespuesta(
        Long id,
        Long estrategiaId,
        int version,
        String contenido,
        String hashContenido,
        String hashConfiguracion,
        EstadoScript estado,
        boolean vigente,
        LocalDateTime fechaGeneracion,
        String aprobadoPor,
        LocalDateTime fechaAprobacion) {

    static ScriptRespuesta de(ScriptRman s) {
        boolean vigente = s.getHashConfiguracion().equals(HuellaConfiguracion.de(s.getEstrategia()));
        return new ScriptRespuesta(s.getId(), s.getEstrategia().getId(), s.getVersion(), s.getContenido(),
                s.getHashContenido(), s.getHashConfiguracion(), s.getEstado(), vigente, s.getFechaGeneracion(),
                s.getAprobadoPor(), s.getFechaAprobacion());
    }
}
