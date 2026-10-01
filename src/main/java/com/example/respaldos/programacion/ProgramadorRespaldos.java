package com.example.respaldos.programacion;

import com.example.respaldos.aprobacion.AprobacionService;
import com.example.respaldos.modelo.Alerta;
import com.example.respaldos.modelo.EstadoAlerta;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.TipoMensaje;
import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.ProgramacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Programador propio de la aplicacion (mecanismo elegido para la automatizacion).
 * <p>
 * En cada revision, para cada programacion activa de una estrategia activa:
 * <ol>
 *   <li>Si no tiene proxima ejecucion calculada, la calcula y no hace nada mas.</li>
 *   <li>Si su proxima ejecucion todavia no llega, no hace nada.</li>
 *   <li>Si ya llego, primero avanza la proxima ejecucion (asi no se dispara dos veces) y luego:
 *     <ul>
 *       <li>si se paso por mas de la tolerancia (la app no estaba activa), no la ejecuta tarde:
 *           registra una advertencia de ejecucion omitida;</li>
 *       <li>si no hay un script aprobado, vigente e integro, no ejecuta y registra una advertencia;</li>
 *       <li>si todo esta bien, dispara la ejecucion con el script aprobado.</li>
 *     </ul>
 *   </li>
 * </ol>
 * Cada programacion se procesa en su propia transaccion: una falla no afecta a las demas.
 */
@Component
public class ProgramadorRespaldos {

    private static final Logger log = LoggerFactory.getLogger(ProgramadorRespaldos.class);
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProgramacionRepository programaciones;
    private final AlertaRepository alertas;
    private final AprobacionService aprobacion;
    private final CalculadoraEjecuciones calculadora;
    private final DisparadorEjecucion disparador;
    private final PropiedadesProgramador propiedades;
    private final TransactionTemplate transaccion;

    public ProgramadorRespaldos(ProgramacionRepository programaciones, AlertaRepository alertas,
                                AprobacionService aprobacion, CalculadoraEjecuciones calculadora,
                                DisparadorEjecucion disparador, PropiedadesProgramador propiedades,
                                TransactionTemplate transaccion) {
        this.programaciones = programaciones;
        this.alertas = alertas;
        this.aprobacion = aprobacion;
        this.calculadora = calculadora;
        this.disparador = disparador;
        this.propiedades = propiedades;
        this.transaccion = transaccion;
    }

    public enum Resultado { INICIALIZADA, NO_CORRESPONDE, DISPARADA, OMITIDA, SIN_SCRIPT }

    /** Lo que se decidio para una programacion en una revision. */
    public record Decision(Long estrategiaId, Resultado resultado, LocalDateTime fechaProgramada,
                           LocalDateTime proximaEjecucion) {
    }

    @Scheduled(fixedDelayString = "${respaldos.programador.intervalo}",
            initialDelayString = "${respaldos.programador.intervalo}")
    public void revisarPeriodicamente() {
        revisar(LocalDateTime.now()).stream()
                .filter(d -> d.resultado() != Resultado.NO_CORRESPONDE)
                .forEach(d -> log.info("Programador: estrategia {} -> {} (programada {}, proxima {})",
                        d.estrategiaId(), d.resultado(), d.fechaProgramada(), d.proximaEjecucion()));
    }

    /** Una revision completa con la hora indicada. Publico para poder probarlo con horas fijas. */
    public List<Decision> revisar(LocalDateTime ahora) {
        List<Decision> decisiones = new ArrayList<>();
        for (Long id : programaciones.idsActivas()) {
            try {
                decisiones.add(transaccion.execute(estado -> revisarUna(id, ahora)));
            } catch (RuntimeException e) {
                log.error("Programador: fallo al revisar la programacion {}", id, e);
            }
        }
        return decisiones;
    }

    private Decision revisarUna(Long programacionId, LocalDateTime ahora) {
        Programacion p = programaciones.findById(programacionId).orElseThrow();
        Estrategia e = p.getEstrategia();

        if (p.getProximaEjecucion() == null) {
            p.setProximaEjecucion(calculadora.siguienteDesde(p, ahora));
            return new Decision(e.getId(), Resultado.INICIALIZADA, null, p.getProximaEjecucion());
        }
        LocalDateTime debida = p.getProximaEjecucion();
        if (debida.isAfter(ahora)) {
            return new Decision(e.getId(), Resultado.NO_CORRESPONDE, null, debida);
        }

        // Se avanza antes de disparar: aunque la ejecucion tarde, la siguiente revision no la repite.
        p.setProximaEjecucion(calculadora.siguienteDespuesDe(p, ahora));
        String proxima = p.getProximaEjecucion() == null
                ? "no hay mas ejecuciones programadas" : "proxima ejecucion: " + p.getProximaEjecucion().format(FORMATO);

        if (debida.isBefore(ahora.minus(propiedades.tolerancia()))) {
            registrar(e, "EJECUCION_OMITIDA", "La ejecucion programada para " + debida.format(FORMATO)
                    + " no se realizo: la aplicacion no estaba revisando la programacion a esa hora. "
                    + "No se ejecuta tarde para no salirse de la ventana de respaldo; " + proxima + ".", false);
            return new Decision(e.getId(), Resultado.OMITIDA, debida, p.getProximaEjecucion());
        }

        AprobacionService.Ejecutable ejecutable = aprobacion.evaluarEjecutable(e.getId());
        if (ejecutable.script() == null) {
            registrar(e, "SIN_SCRIPT_EJECUTABLE", "A la estrategia le correspondia ejecutarse el "
                    + debida.format(FORMATO) + ", pero no se ejecuto: " + ejecutable.motivo(), true);
            return new Decision(e.getId(), Resultado.SIN_SCRIPT, debida, p.getProximaEjecucion());
        }

        disparador.disparar(e.getId(), ejecutable.script().getId(), debida);
        return new Decision(e.getId(), Resultado.DISPARADA, debida, p.getProximaEjecucion());
    }

    /** @param unica si true, no se repite mientras haya una igual abierta para la estrategia */
    private void registrar(Estrategia e, String codigo, String mensaje, boolean unica) {
        if (unica && alertas.existsByEstrategiaIdAndCodigoAndEstado(e.getId(), codigo, EstadoAlerta.ABIERTA)) {
            return;
        }
        Alerta alerta = new Alerta(TipoMensaje.ADVERTENCIA, codigo, mensaje);
        alerta.setEstrategia(e);
        alerta.setBaseDatos(e.getBaseDatos());
        alertas.save(alerta);
    }
}
