package com.example.respaldos.estrategia;

import com.example.respaldos.modelo.DiaSemana;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record EstrategiaRespuesta(
        Long id,
        String nombre,
        String descripcion,
        BaseDatosResumen baseDatos,
        String responsable,
        Prioridad prioridad,
        boolean activa,
        List<Elemento> elementos,
        TipoRespaldo tipoRespaldo,
        boolean comprimido,
        Integer diasRetencion,
        String rutaDestino,
        String dispositivo,
        ProgramacionRespuesta programacion,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaModificacion) {

    public record BaseDatosResumen(Long id, String nombre) {
    }

    public record Elemento(TipoElemento tipo, String nombreObjeto) {
    }

    public record ProgramacionRespuesta(
            LocalDate fechaInicio,
            LocalTime hora,
            Frecuencia frecuencia,
            Set<DiaSemana> diasSemana,
            int intervalo,
            LocalTime ventanaInicio,
            LocalTime ventanaFin,
            boolean activa,
            LocalDateTime proximaEjecucion) {

        static ProgramacionRespuesta de(Programacion p) {
            return p == null ? null : new ProgramacionRespuesta(p.getFechaInicio(), p.getHora(),
                    p.getFrecuencia(), p.getDiasSemana(), p.getIntervalo(), p.getVentanaInicio(),
                    p.getVentanaFin(), p.isActiva(), p.getProximaEjecucion());
        }
    }

    static EstrategiaRespuesta de(Estrategia e) {
        List<Elemento> elementos = e.getElementos().stream()
                .map(el -> new Elemento(el.getTipoElemento(), el.getNombreObjeto()))
                .sorted(Comparator.comparing(Elemento::tipo)
                        .thenComparing(Elemento::nombreObjeto, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        return new EstrategiaRespuesta(e.getId(), e.getNombre(), e.getDescripcion(),
                new BaseDatosResumen(e.getBaseDatos().getId(), e.getBaseDatos().getNombre()),
                e.getResponsable(), e.getPrioridad(), e.isActiva(), elementos, e.getTipoRespaldo(),
                e.isComprimido(), e.getDiasRetencion(), e.getRutaDestino(), e.getDispositivo(),
                ProgramacionRespuesta.de(e.getProgramacion()), e.getFechaCreacion(), e.getFechaModificacion());
    }
}
