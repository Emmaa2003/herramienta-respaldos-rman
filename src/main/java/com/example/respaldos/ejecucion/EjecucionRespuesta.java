package com.example.respaldos.ejecucion;

import com.example.respaldos.modelo.Ejecucion;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.EstadoVerificacion;
import com.example.respaldos.modelo.EvidenciaArchivo;
import com.example.respaldos.modelo.OrigenEjecucion;
import com.example.respaldos.modelo.TipoEvidencia;
import com.example.respaldos.modelo.TipoRespaldo;

import java.time.LocalDateTime;
import java.util.List;

/** Evidencia completa de una ejecucion. */
public record EjecucionRespuesta(
        Long id,
        Long estrategiaId,
        String estrategia,
        Long baseDatosId,
        String baseDatos,
        Long scriptId,
        int scriptVersion,
        OrigenEjecucion origen,
        EstadoEjecucion estado,
        TipoRespaldo tipoRespaldo,
        LocalDateTime fechaProgramada,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        Long duracionSegundos,
        Integer codigoSalida,
        String rutaDestino,
        Long tamanoTotalBytes,
        String mensajeError,
        EstadoVerificacion verificacion,
        List<Archivo> archivos,
        String scriptEjecutado,
        String salidaRman,
        String salidaVerificacion) {

    public record Archivo(String ruta, TipoEvidencia tipo, Long tamanoBytes, boolean existe,
                          LocalDateTime fechaVerificacion) {

        static Archivo de(EvidenciaArchivo a) {
            return new Archivo(a.getRuta(), a.getTipo(), a.getTamanoBytes(), a.isExiste(), a.getFechaVerificacion());
        }
    }

    public static EjecucionRespuesta de(Ejecucion x) {
        return new EjecucionRespuesta(x.getId(), x.getEstrategia().getId(), x.getEstrategia().getNombre(),
                x.getBaseDatos().getId(), x.getBaseDatos().getNombre(), x.getScript().getId(),
                x.getScript().getVersion(), x.getOrigen(), x.getEstado(), x.getTipoRespaldo(),
                x.getFechaProgramada(), x.getFechaInicio(), x.getFechaFin(), x.getDuracionSegundos(),
                x.getCodigoSalida(), x.getRutaDestino(), x.getTamanoTotalBytes(), x.getMensajeError(),
                x.getVerificacion(), x.getArchivos().stream().map(Archivo::de).toList(),
                x.getScriptEjecutado(), x.getSalidaRman(), x.getSalidaVerificacion());
    }
}
