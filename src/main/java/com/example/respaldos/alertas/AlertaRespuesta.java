package com.example.respaldos.alertas;

import com.example.respaldos.modelo.Alerta;
import com.example.respaldos.modelo.EstadoAlerta;
import com.example.respaldos.modelo.TipoMensaje;

import java.time.LocalDateTime;

/**
 * @param aplicable true si es una recomendacion que la aplicacion puede aplicar a pedido del
 *                  administrador (agregar un elemento a la estrategia). Nunca se aplica sola.
 */
public record AlertaRespuesta(
        Long id,
        TipoMensaje tipo,
        String codigo,
        String mensaje,
        EstadoAlerta estado,
        Long baseDatosId,
        String baseDatos,
        Long estrategiaId,
        String estrategia,
        Long ejecucionId,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaAtencion,
        String atendidaPor,
        String comentario,
        boolean aplicable) {

    static AlertaRespuesta de(Alerta a) {
        return new AlertaRespuesta(a.getId(), a.getTipo(), a.getCodigo(), a.getMensaje(), a.getEstado(),
                a.getBaseDatos() == null ? null : a.getBaseDatos().getId(),
                a.getBaseDatos() == null ? null : a.getBaseDatos().getNombre(),
                a.getEstrategia() == null ? null : a.getEstrategia().getId(),
                a.getEstrategia() == null ? null : a.getEstrategia().getNombre(),
                a.getEjecucion() == null ? null : a.getEjecucion().getId(),
                a.getFechaCreacion(), a.getFechaAtencion(), a.getAtendidaPor(), a.getComentario(),
                AlertaService.esAplicable(a));
    }
}
