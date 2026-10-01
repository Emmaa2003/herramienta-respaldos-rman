package com.example.respaldos.alertas;

import com.example.respaldos.aprobacion.InvalidadorScripts;
import com.example.respaldos.comun.ConflictoException;
import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.comun.SolicitudInvalidaException;
import com.example.respaldos.modelo.Alerta;
import com.example.respaldos.modelo.EstadoAlerta;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoMensaje;
import com.example.respaldos.repositorio.AlertaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta y atencion de alertas.
 * <p>
 * Una recomendacion nunca se aplica sola (regla 5). Solo cuando el administrador pide
 * aplicarla:
 * <ul>
 *   <li>INCLUIR_ARCHIVELOG / INCLUIR_CONTROLFILE: la aplicacion agrega el elemento a la
 *       estrategia. Eso cambia el QUE, asi que el script aprobado se invalida y hay que
 *       generar y aprobar uno nuevo antes de la proxima ejecucion.</li>
 *   <li>Cualquier otra recomendacion: el administrador la aplico por su cuenta; se registra
 *       como aplicada con su comentario (obligatorio).</li>
 * </ul>
 * Nada de esto cambia la base de datos Oracle ni su modo de archivado.
 */
@Service
@Transactional
public class AlertaService {

    private static final Map<String, TipoElemento> ELEMENTO_RECOMENDADO = Map.of(
            "INCLUIR_ARCHIVELOG", TipoElemento.ARCHIVELOG,
            "INCLUIR_CONTROLFILE", TipoElemento.CONTROLFILE);

    private final AlertaRepository alertas;
    private final InvalidadorScripts invalidador;

    public AlertaService(AlertaRepository alertas, InvalidadorScripts invalidador) {
        this.alertas = alertas;
        this.invalidador = invalidador;
    }

    static boolean esAplicable(Alerta a) {
        return a.getTipo() == TipoMensaje.RECOMENDACION && a.getEstrategia() != null
                && ELEMENTO_RECOMENDADO.containsKey(a.getCodigo());
    }

    @Transactional(readOnly = true)
    public List<AlertaRespuesta> listar(EstadoAlerta estado, TipoMensaje tipo, Long estrategiaId, Long baseDatosId) {
        return alertas.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(a -> estado == null || a.getEstado() == estado)
                .filter(a -> tipo == null || a.getTipo() == tipo)
                .filter(a -> estrategiaId == null || (a.getEstrategia() != null && a.getEstrategia().getId().equals(estrategiaId)))
                .filter(a -> baseDatosId == null || (a.getBaseDatos() != null && a.getBaseDatos().getId().equals(baseDatosId)))
                .map(AlertaRespuesta::de)
                .toList();
    }

    /** Alertas abiertas por tipo de mensaje (para el panel). */
    @Transactional(readOnly = true)
    public Map<TipoMensaje, Long> resumenAbiertas() {
        Map<TipoMensaje, Long> resumen = new EnumMap<>(TipoMensaje.class);
        for (TipoMensaje t : TipoMensaje.values()) {
            resumen.put(t, 0L);
        }
        alertas.findAll().stream().filter(a -> a.getEstado() == EstadoAlerta.ABIERTA)
                .forEach(a -> resumen.merge(a.getTipo(), 1L, Long::sum));
        return resumen;
    }

    public AlertaRespuesta atender(Long id, AtencionSolicitud s) {
        return cerrar(id, EstadoAlerta.ATENDIDA, s);
    }

    public AlertaRespuesta descartar(Long id, AtencionSolicitud s) {
        return cerrar(id, EstadoAlerta.DESCARTADA, s);
    }

    public AlertaRespuesta aplicar(Long id, AtencionSolicitud s) {
        Alerta a = buscarAbierta(id);
        if (a.getTipo() != TipoMensaje.RECOMENDACION) {
            throw new ConflictoException("Solo se aplican recomendaciones; esta alerta es " + a.getTipo()
                    + ". Use atender o descartar.");
        }
        String comentario;
        if (esAplicable(a)) {
            Estrategia e = a.getEstrategia();
            TipoElemento elemento = ELEMENTO_RECOMENDADO.get(a.getCodigo());
            boolean yaEsta = e.getElementos().stream().anyMatch(el -> el.getTipoElemento() == elemento);
            if (!yaEsta) {
                e.agregarElemento(new EstrategiaElemento(elemento, null));
            }
            int invalidados = invalidador.invalidarSiCambio(e);
            comentario = (yaEsta ? "La estrategia ya incluia " : "Se agrego " ) + elemento
                    + " a la estrategia '" + e.getNombre() + "' por decision del administrador."
                    + (invalidados > 0 ? " Se invalidaron " + invalidados + " script(s): genere y apruebe uno "
                    + "nuevo antes de la proxima ejecucion." : "")
                    + (s.comentario() == null || s.comentario().isBlank() ? "" : " " + s.comentario().trim());
        } else {
            if (s.comentario() == null || s.comentario().isBlank()) {
                throw new SolicitudInvalidaException("Esta recomendacion no la puede aplicar la aplicacion: "
                        + "indique en el comentario que hizo el administrador para aplicarla.");
            }
            comentario = s.comentario().trim();
        }
        a.setEstado(EstadoAlerta.APLICADA);
        a.setAtendidaPor(s.atendidaPor().trim());
        a.setFechaAtencion(LocalDateTime.now());
        a.setComentario(comentario.length() > 1000 ? comentario.substring(0, 1000) : comentario);
        return AlertaRespuesta.de(a);
    }

    private AlertaRespuesta cerrar(Long id, EstadoAlerta estado, AtencionSolicitud s) {
        Alerta a = buscarAbierta(id);
        a.setEstado(estado);
        a.setAtendidaPor(s.atendidaPor().trim());
        a.setFechaAtencion(LocalDateTime.now());
        a.setComentario(s.comentario());
        return AlertaRespuesta.de(a);
    }

    private Alerta buscarAbierta(Long id) {
        Alerta a = alertas.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la alerta con id " + id + "."));
        if (a.getEstado() != EstadoAlerta.ABIERTA) {
            throw new ConflictoException("La alerta ya fue cerrada (" + a.getEstado() + ").");
        }
        return a;
    }
}
