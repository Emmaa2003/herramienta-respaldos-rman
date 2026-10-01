package com.example.respaldos.alertas;

import com.example.respaldos.aprobacion.AprobacionService;
import com.example.respaldos.infraestructura.AmbienteException;
import com.example.respaldos.infraestructura.CatalogoOracle;
import com.example.respaldos.infraestructura.ClienteContenedor;
import com.example.respaldos.modelo.Alerta;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.EstadoAlerta;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.Ejecucion;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.ModoArchivado;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.TipoMensaje;
import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EjecucionRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.validacion.Hallazgo;
import com.example.respaldos.validacion.ResultadoValidacion;
import com.example.respaldos.validacion.ValidadorEstrategia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Control preventivo: revisa periodicamente las condiciones que pueden comprometer una
 * estrategia (seccion 11 del PDF) y mantiene las alertas al dia.
 * <ul>
 *   <li>Por base: contenedor detenido, base en NOARCHIVELOG.</li>
 *   <li>Por estrategia: inactiva, sin programacion o con la programacion desactivada, sin
 *       script ejecutable, configuracion incompleta (hallazgos bloqueantes del validador),
 *       falta de espacio, archived redo logs recomendados y sin respaldo reciente segun su
 *       prioridad.</li>
 * </ul>
 * Cada condicion abre una alerta (sin duplicar si ya hay una abierta igual). Cuando la
 * condicion desaparece, la alerta se cierra como ATENDIDA por "sistema". Las alertas de
 * eventos puntuales (ejecucion omitida, fallida...) no se cierran solas, salvo
 * EJECUCION_FALLIDA, que se cierra cuando hay una ejecucion exitosa posterior.
 * <p>
 * Solo lee y registra alertas: no cambia estrategias ni la base de datos.
 */
@Component
public class MonitorPreventivo {

    private static final Logger log = LoggerFactory.getLogger(MonitorPreventivo.class);
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final String SISTEMA = "sistema";

    /** Codigos cuyo ciclo de vida (abrir y cerrar) maneja el monitor. */
    static final Set<String> CODIGOS_MONITOREADOS = Set.of(
            "CONTENEDOR_DETENIDO", "NOARCHIVELOG", "ESTRATEGIA_INACTIVA", "SIN_PROGRAMACION",
            "PROGRAMACION_INACTIVA", "SIN_SCRIPT_EJECUTABLE", "CONFIGURACION_INCOMPLETA", "FALTA_ESPACIO",
            "INCLUIR_ARCHIVELOG", "INCLUIR_CONTROLFILE", "SIN_RESPALDO_RECIENTE");

    private static final List<EstadoEjecucion> CON_RESPALDO =
            List.of(EstadoEjecucion.EXITOSO, EstadoEjecucion.CON_ADVERTENCIAS);

    private final BaseDatosRepository bases;
    private final EstrategiaRepository estrategias;
    private final EjecucionRepository ejecuciones;
    private final AlertaRepository alertas;
    private final ClienteContenedor cliente;
    private final CatalogoOracle catalogo;
    private final ValidadorEstrategia validador;
    private final AprobacionService aprobacion;
    private final TransactionTemplate transaccion;

    public MonitorPreventivo(BaseDatosRepository bases, EstrategiaRepository estrategias,
                             EjecucionRepository ejecuciones, AlertaRepository alertas, ClienteContenedor cliente,
                             CatalogoOracle catalogo, ValidadorEstrategia validador, AprobacionService aprobacion,
                             TransactionTemplate transaccion) {
        this.bases = bases;
        this.estrategias = estrategias;
        this.ejecuciones = ejecuciones;
        this.alertas = alertas;
        this.cliente = cliente;
        this.catalogo = catalogo;
        this.validador = validador;
        this.aprobacion = aprobacion;
        this.transaccion = transaccion;
    }

    /** Resultado de una revision: cuantas alertas se abrieron, se cerraron y siguen abiertas. */
    public record ResumenRevision(LocalDateTime fecha, int abiertas, int cerradas, int vigentes) {
    }

    private record Condicion(TipoMensaje tipo, String codigo, BaseDatos base, Estrategia estrategia, String mensaje) {

        String clave() {
            return clave(codigo, base == null ? null : base.getId(), estrategia == null ? null : estrategia.getId());
        }

        static String clave(String codigo, Long baseId, Long estrategiaId) {
            return codigo + "|" + baseId + "|" + estrategiaId;
        }
    }

    @Scheduled(fixedDelayString = "${respaldos.monitor.intervalo}",
            initialDelayString = "${respaldos.monitor.retraso-inicial}")
    public void revisarPeriodicamente() {
        ResumenRevision r = revisar(LocalDateTime.now());
        if (r.abiertas() > 0 || r.cerradas() > 0) {
            log.info("Monitor preventivo: {} alerta(s) nueva(s), {} cerrada(s), {} abierta(s) vigente(s)",
                    r.abiertas(), r.cerradas(), r.vigentes());
        }
    }

    public ResumenRevision revisar(LocalDateTime ahora) {
        return transaccion.execute(estado -> revisarEnTransaccion(ahora));
    }

    private ResumenRevision revisarEnTransaccion(LocalDateTime ahora) {
        List<Condicion> condiciones = new ArrayList<>();
        bases.findAll().forEach(b -> revisarBase(b, ahora, condiciones));
        estrategias.findAll().forEach(e -> revisarEstrategia(e, ahora, condiciones));

        Map<String, Condicion> deseadas = new LinkedHashMap<>();
        condiciones.forEach(c -> deseadas.putIfAbsent(c.clave(), c));

        int abiertas = 0;
        int cerradas = 0;
        List<Alerta> existentes = alertas.findByEstado(EstadoAlerta.ABIERTA);
        Set<String> clavesExistentes = existentes.stream()
                .filter(a -> CODIGOS_MONITOREADOS.contains(a.getCodigo()))
                .map(MonitorPreventivo::clave)
                .collect(Collectors.toSet());

        for (Condicion c : deseadas.values()) {
            if (!clavesExistentes.contains(c.clave())) {
                Alerta a = new Alerta(c.tipo(), c.codigo(), recortar(c.mensaje()));
                a.setBaseDatos(c.base() != null ? c.base() : c.estrategia().getBaseDatos());
                a.setEstrategia(c.estrategia());
                alertas.save(a);
                abiertas++;
            }
        }
        for (Alerta a : existentes) {
            if (CODIGOS_MONITOREADOS.contains(a.getCodigo()) && !deseadas.containsKey(clave(a))) {
                cerrarPorSistema(a, ahora, "La condicion ya no se presenta (revision automatica del "
                        + ahora.format(FORMATO) + ").");
                cerradas++;
            } else if ("EJECUCION_FALLIDA".equals(a.getCodigo()) && hayExitoPosterior(a)) {
                cerrarPorSistema(a, ahora, "Hubo una ejecucion exitosa posterior de la misma estrategia.");
                cerradas++;
            }
        }
        int vigentes = (int) alertas.findByEstado(EstadoAlerta.ABIERTA).stream().count();
        return new ResumenRevision(ahora, abiertas, cerradas, vigentes);
    }

    // --- Condiciones por base ------------------------------------------------------------

    private void revisarBase(BaseDatos b, LocalDateTime ahora, List<Condicion> c) {
        boolean activo;
        try {
            activo = cliente.contenedorEnEjecucion(b.getContenedor());
        } catch (AmbienteException ex) {
            activo = false;
        }
        if (!activo) {
            c.add(new Condicion(TipoMensaje.ADVERTENCIA, "CONTENEDOR_DETENIDO", b, null, "El contenedor '"
                    + b.getContenedor() + "' de la base '" + b.getNombre() + "' no esta en ejecucion: no se "
                    + "pueden hacer ni verificar respaldos."));
            return;
        }
        try {
            CatalogoOracle.InfoBaseDatos info = catalogo.informacionBase();
            if (!info.pdb().equalsIgnoreCase(b.getServicio())) {
                return; // El catalogo disponible no describe a esta base.
            }
            b.setModoArchivado(info.modoArchivado());
            b.setFechaInspeccion(ahora);
            if (info.modoArchivado() == ModoArchivado.NOARCHIVELOG) {
                c.add(new Condicion(TipoMensaje.ADVERTENCIA, "NOARCHIVELOG", b, null, "La base de datos '"
                        + b.getNombre() + "' se encuentra en modo NOARCHIVELOG. Las posibilidades de recuperacion "
                        + "son mas limitadas. Revise la estrategia de respaldo y los requerimientos de "
                        + "recuperacion."));
            }
        } catch (AmbienteException ex) {
            log.warn("Monitor preventivo: no se pudo leer el catalogo de la base {}: {}", b.getNombre(), ex.getMessage());
        }
    }

    // --- Condiciones por estrategia -------------------------------------------------------

    private void revisarEstrategia(Estrategia e, LocalDateTime ahora, List<Condicion> c) {
        if (!e.isActiva()) {
            c.add(new Condicion(TipoMensaje.ADVERTENCIA, "ESTRATEGIA_INACTIVA", null, e, "La estrategia '"
                    + e.getNombre() + "' esta inactiva: no protege la informacion hasta que se active."));
            return;
        }
        Programacion p = e.getProgramacion();
        if (p == null) {
            c.add(new Condicion(TipoMensaje.ADVERTENCIA, "SIN_PROGRAMACION", null, e, "La estrategia '"
                    + e.getNombre() + "' no tiene programacion: no se ejecutara automaticamente."));
        } else if (!p.isActiva()) {
            c.add(new Condicion(TipoMensaje.ADVERTENCIA, "PROGRAMACION_INACTIVA", null, e, "La programacion de '"
                    + e.getNombre() + "' esta desactivada: no se ejecutara automaticamente."));
        }

        AprobacionService.Ejecutable ejecutable = aprobacion.evaluarEjecutable(e.getId());
        if (ejecutable.script() == null) {
            c.add(new Condicion(TipoMensaje.ADVERTENCIA, "SIN_SCRIPT_EJECUTABLE", null, e,
                    "La estrategia '" + e.getNombre() + "' no se puede ejecutar: " + ejecutable.motivo()));
        }

        ResultadoValidacion v = validador.validar(e);
        if (!v.valida()) {
            c.add(new Condicion(TipoMensaje.ADVERTENCIA, "CONFIGURACION_INCOMPLETA", null, e,
                    "La configuracion de '" + e.getNombre() + "' tiene problemas que impiden ejecutarla: "
                            + v.bloqueantes().stream().map(Hallazgo::texto).collect(Collectors.joining(" "))));
        }
        for (Hallazgo h : v.hallazgos()) {
            switch (h.codigo()) {
                case "ESPACIO_BAJO", "ESPACIO_INSUFICIENTE" ->
                        c.add(new Condicion(TipoMensaje.ADVERTENCIA, "FALTA_ESPACIO", null, e, h.texto()));
                case "INCLUIR_ARCHIVELOG", "INCLUIR_CONTROLFILE" ->
                        c.add(new Condicion(TipoMensaje.RECOMENDACION, h.codigo(), null, e,
                                "Estrategia '" + e.getNombre() + "': " + h.texto()));
                default -> {
                }
            }
        }

        LocalDateTime ultimo = ejecuciones
                .findFirstByEstrategiaIdAndEstadoInOrderByFechaInicioDesc(e.getId(), CON_RESPALDO)
                .map(Ejecucion::getFechaInicio).orElse(null);
        LocalDateTime referencia = ultimo != null ? ultimo : e.getFechaCreacion();
        int horas = e.getPrioridad().getHorasMaximasSinRespaldo();
        if (referencia != null && referencia.isBefore(ahora.minusHours(horas))) {
            c.add(new Condicion(TipoMensaje.ADVERTENCIA, "SIN_RESPALDO_RECIENTE", null, e,
                    (ultimo == null
                            ? "La estrategia '" + e.getNombre() + "' no tiene ningun respaldo exitoso desde que se creo ("
                            : "El ultimo respaldo exitoso de '" + e.getNombre() + "' fue el ")
                            + referencia.format(FORMATO) + (ultimo == null ? ")" : "")
                            + ". Para prioridad " + e.getPrioridad() + " se espera al menos uno cada " + horas + " h."));
        }
    }

    // --- utilidades ------------------------------------------------------------------------

    private boolean hayExitoPosterior(Alerta a) {
        if (a.getEstrategia() == null || a.getEjecucion() == null) {
            return false;
        }
        return ejecuciones.findFirstByEstrategiaIdAndEstadoInOrderByFechaInicioDesc(a.getEstrategia().getId(), CON_RESPALDO)
                .filter(x -> x.getFechaInicio().isAfter(a.getEjecucion().getFechaInicio()))
                .isPresent();
    }

    private static void cerrarPorSistema(Alerta a, LocalDateTime ahora, String comentario) {
        a.setEstado(EstadoAlerta.ATENDIDA);
        a.setAtendidaPor(SISTEMA);
        a.setFechaAtencion(ahora);
        a.setComentario(comentario);
    }

    private static String clave(Alerta a) {
        Long base = a.getEstrategia() != null ? null : a.getBaseDatos() == null ? null : a.getBaseDatos().getId();
        return Condicion.clave(a.getCodigo(), base, a.getEstrategia() == null ? null : a.getEstrategia().getId());
    }

    private static String recortar(String texto) {
        return Objects.requireNonNull(texto).length() <= 1000 ? texto : texto.substring(0, 997) + "...";
    }
}
