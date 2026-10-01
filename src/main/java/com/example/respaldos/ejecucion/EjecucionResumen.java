package com.example.respaldos.ejecucion;

import com.example.respaldos.modelo.Ejecucion;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.EstadoVerificacion;
import com.example.respaldos.modelo.OrigenEjecucion;
import com.example.respaldos.modelo.TipoRespaldo;

import java.time.LocalDateTime;

/** Una fila del historial (fecha, estrategia, tipo, inicio, fin, resultado), como el ejemplo del PDF. */
public record EjecucionResumen(
        Long id,
        Long estrategiaId,
        String estrategia,
        String baseDatos,
        TipoRespaldo tipoRespaldo,
        OrigenEjecucion origen,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        Long duracionSegundos,
        EstadoEjecucion estado,
        EstadoVerificacion verificacion,
        Long tamanoTotalBytes,
        String mensajeError) {

    static EjecucionResumen de(Ejecucion x) {
        return new EjecucionResumen(x.getId(), x.getEstrategia().getId(), x.getEstrategia().getNombre(),
                x.getBaseDatos().getNombre(), x.getTipoRespaldo(), x.getOrigen(), x.getFechaInicio(),
                x.getFechaFin(), x.getDuracionSegundos(), x.getEstado(), x.getVerificacion(),
                x.getTamanoTotalBytes(), x.getMensajeError());
    }
}
