package com.example.respaldos.basedatos;

import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.ModoArchivado;

import java.time.LocalDateTime;

/** Base de datos registrada. modoArchivado es null hasta la primera inspeccion. */
public record BaseDatosRespuesta(
        Long id,
        String nombre,
        String descripcion,
        String contenedor,
        String servicio,
        Ambiente ambiente,
        ModoArchivado modoArchivado,
        LocalDateTime fechaInspeccion,
        LocalDateTime fechaRegistro) {

    static BaseDatosRespuesta de(BaseDatos b) {
        return new BaseDatosRespuesta(b.getId(), b.getNombre(), b.getDescripcion(), b.getContenedor(),
                b.getServicio(), b.getAmbiente(), b.getModoArchivado(), b.getFechaInspeccion(),
                b.getFechaRegistro());
    }
}
