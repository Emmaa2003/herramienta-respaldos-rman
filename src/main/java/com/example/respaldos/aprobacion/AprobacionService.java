package com.example.respaldos.aprobacion;

import com.example.respaldos.comun.ConflictoException;
import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.generador.HuellaConfiguracion;
import com.example.respaldos.generador.ScriptRespuesta;
import com.example.respaldos.modelo.EstadoScript;
import com.example.respaldos.modelo.ScriptRman;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import com.example.respaldos.validacion.ResultadoValidacion;
import com.example.respaldos.validacion.ValidacionFallidaException;
import com.example.respaldos.validacion.ValidadorEstrategia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aprobacion del administrador (regla 3): ningun script se ejecuta sin que alguien lo
 * haya revisado y aprobado.
 * <p>
 * Una aprobacion es valida solo si:
 * <ul>
 *   <li>el script esta pendiente (GENERADO);</li>
 *   <li>el texto aprobado es el que se mostro (la huella enviada coincide con la guardada);</li>
 *   <li>el texto no fue alterado desde que se genero;</li>
 *   <li>la estrategia no cambio desde que se genero el script (sigue vigente);</li>
 *   <li>la estrategia sigue pasando la validacion.</li>
 * </ul>
 * Por estrategia hay como maximo un script aprobado: aprobar uno nuevo invalida el anterior.
 */
@Service
@Transactional
public class AprobacionService {

    private final ScriptRmanRepository scripts;
    private final EstrategiaRepository estrategias;
    private final ValidadorEstrategia validador;

    public AprobacionService(ScriptRmanRepository scripts, EstrategiaRepository estrategias,
                             ValidadorEstrategia validador) {
        this.scripts = scripts;
        this.estrategias = estrategias;
        this.validador = validador;
    }

    public AprobacionRespuesta aprobar(Long scriptId, AprobacionSolicitud solicitud) {
        ScriptRman script = buscar(scriptId);
        exigirPendiente(script, "aprobar");
        if (!script.getHashContenido().equals(solicitud.hashContenido())) {
            throw new ConflictoException("El script revisado no coincide con la version guardada (la huella es "
                    + "distinta). Vuelva a cargar el script y reviselo antes de aprobarlo.");
        }
        exigirIntegroYVigente(script);

        // Regla 2: se vuelve a validar, porque la base pudo cambiar desde que se genero.
        ResultadoValidacion validacion = validador.validar(script.getEstrategia());
        if (!validacion.valida()) {
            throw new ValidacionFallidaException(validacion);
        }

        for (ScriptRman anterior : scripts.findByEstrategiaIdAndEstado(
                script.getEstrategia().getId(), EstadoScript.APROBADO)) {
            anterior.setEstado(EstadoScript.INVALIDADO);
        }
        script.setEstado(EstadoScript.APROBADO);
        script.setAprobadoPor(solicitud.aprobadoPor().trim());
        script.setFechaAprobacion(LocalDateTime.now());
        script.setComentarioRevision(solicitud.comentario());
        scripts.saveAndFlush(script);

        return new AprobacionRespuesta(ScriptRespuesta.de(script), validacion.hallazgos());
    }

    public ScriptRespuesta rechazar(Long scriptId, RechazoSolicitud solicitud) {
        ScriptRman script = buscar(scriptId);
        exigirPendiente(script, "rechazar");
        script.setEstado(EstadoScript.RECHAZADO);
        script.setRechazadoPor(solicitud.rechazadoPor().trim());
        script.setFechaRechazo(LocalDateTime.now());
        script.setComentarioRevision(solicitud.motivo().trim());
        scripts.saveAndFlush(script);
        return ScriptRespuesta.de(script);
    }

    /**
     * El script que se puede ejecutar para la estrategia: aprobado, vigente e integro.
     * Lo usaran la programacion y la ejecucion; si no hay, explica por que.
     */
    @Transactional(readOnly = true)
    public ScriptRman scriptEjecutable(Long estrategiaId) {
        if (!estrategias.existsById(estrategiaId)) {
            throw new NoEncontradoException("No existe la estrategia con id " + estrategiaId + ".");
        }
        List<ScriptRman> aprobados = scripts.findByEstrategiaIdAndEstado(estrategiaId, EstadoScript.APROBADO);
        if (aprobados.isEmpty()) {
            throw new NoEncontradoException("La estrategia no tiene un script aprobado. Genere el script y "
                    + "apruebelo antes de programarla o ejecutarla.");
        }
        ScriptRman script = aprobados.getFirst();
        exigirIntegroYVigente(script);
        return script;
    }

    private static void exigirPendiente(ScriptRman script, String accion) {
        if (script.getEstado() != EstadoScript.GENERADO) {
            throw new ConflictoException("No se puede " + accion + " el script version " + script.getVersion()
                    + " porque esta " + script.getEstado() + ". Solo se revisan scripts en estado GENERADO.");
        }
    }

    private static void exigirIntegroYVigente(ScriptRman script) {
        if (!script.contenidoIntegro(HuellaConfiguracion.sha256(script.getContenido()))) {
            throw new ConflictoException("El contenido del script version " + script.getVersion()
                    + " fue alterado despues de generarse. No se puede usar; genere uno nuevo.");
        }
        if (!script.getHashConfiguracion().equals(HuellaConfiguracion.de(script.getEstrategia()))) {
            throw new ConflictoException("La estrategia cambio despues de generar el script version "
                    + script.getVersion() + ". Genere el script de nuevo y reviselo.");
        }
    }

    private ScriptRman buscar(Long id) {
        return scripts.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe el script con id " + id + "."));
    }
}
