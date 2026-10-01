package com.example.respaldos.ejecucion;

import com.example.respaldos.aprobacion.AprobacionService;
import com.example.respaldos.comun.ConflictoException;
import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.ejecucion.AnalizadorSalidaRman.Analisis;
import com.example.respaldos.ejecucion.AnalizadorSalidaRman.AnalisisVerificacion;
import com.example.respaldos.ejecucion.AnalizadorSalidaRman.Pieza;
import com.example.respaldos.ejecucion.ClasificadorResultado.Resultado;
import com.example.respaldos.ejecucion.VerificadorRespaldo.ObjetosRespaldados;
import com.example.respaldos.infraestructura.AmbienteException;
import com.example.respaldos.infraestructura.ClienteContenedor;
import com.example.respaldos.infraestructura.ResultadoProceso;
import com.example.respaldos.modelo.Alerta;
import com.example.respaldos.modelo.EstadoAlerta;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.EstadoVerificacion;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.Ejecucion;
import com.example.respaldos.modelo.EvidenciaArchivo;
import com.example.respaldos.modelo.OrigenEjecucion;
import com.example.respaldos.modelo.ScriptRman;
import com.example.respaldos.modelo.TipoMensaje;
import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.EjecucionRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import com.example.respaldos.validacion.Hallazgo;
import com.example.respaldos.validacion.ResultadoValidacion;
import com.example.respaldos.validacion.ValidacionFallidaException;
import com.example.respaldos.validacion.ValidadorEstrategia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Ejecucion de un script aprobado y registro de su evidencia.
 * <p>
 * Antes de ejecutar: script aprobado, vigente e integro (regla 3); nueva validacion de la
 * estrategia (regla 2); y ninguna otra ejecucion en curso sobre la misma base.
 * <p>
 * Al ejecutar: se envia a RMAN el texto exacto del script aprobado, se analiza la salida,
 * se comprueba que cada pieza exista en el contenedor y se corre la verificacion
 * (CROSSCHECK y RESTORE ... VALIDATE). Con todo eso se decide Exitoso / Con advertencias /
 * Fallido. Una ejecucion fallida genera una alerta.
 * <p>
 * La ejecucion corre en segundo plano: la API responde enseguida con la ejecucion EN_CURSO
 * y el resultado se consulta despues.
 */
@Service
public class EjecucionService {

    private static final Logger log = LoggerFactory.getLogger(EjecucionService.class);
    private static final int MAXIMO_MENSAJE = 4000;
    private static final int MAXIMO_ALERTA = 1000;

    private final EstrategiaRepository estrategias;
    private final ScriptRmanRepository scripts;
    private final EjecucionRepository ejecuciones;
    private final AlertaRepository alertas;
    private final AprobacionService aprobacion;
    private final ValidadorEstrategia validador;
    private final ClienteContenedor cliente;
    private final AnalizadorSalidaRman analizador;
    private final VerificadorRespaldo verificador;
    private final TransactionTemplate transaccion;
    private final Executor ejecutor;

    public EjecucionService(EstrategiaRepository estrategias, ScriptRmanRepository scripts,
                            EjecucionRepository ejecuciones, AlertaRepository alertas, AprobacionService aprobacion,
                            ValidadorEstrategia validador, ClienteContenedor cliente,
                            AnalizadorSalidaRman analizador, VerificadorRespaldo verificador,
                            TransactionTemplate transaccion, @Qualifier("ejecutorRespaldos") Executor ejecutor) {
        this.estrategias = estrategias;
        this.scripts = scripts;
        this.ejecuciones = ejecuciones;
        this.alertas = alertas;
        this.aprobacion = aprobacion;
        this.validador = validador;
        this.cliente = cliente;
        this.analizador = analizador;
        this.verificador = verificador;
        this.transaccion = transaccion;
        this.ejecutor = ejecutor;
    }

    // --- Inicio ------------------------------------------------------------------------------

    /** Ejecucion pedida por el administrador. Lanza excepcion si no se puede ejecutar. */
    @Transactional
    public EjecucionRespuesta ejecutarManual(Long estrategiaId) {
        Estrategia e = estrategias.findById(estrategiaId)
                .orElseThrow(() -> new NoEncontradoException("No existe la estrategia con id " + estrategiaId + "."));
        ScriptRman script = aprobacion.scriptEjecutable(estrategiaId);
        ResultadoValidacion validacion = validador.validar(e);
        if (!validacion.valida()) {
            throw new ValidacionFallidaException(validacion);
        }
        if (ejecuciones.existsByBaseDatosIdAndEstado(e.getBaseDatos().getId(), EstadoEjecucion.EN_CURSO)) {
            throw new ConflictoException("Ya hay una ejecucion en curso sobre la base '"
                    + e.getBaseDatos().getNombre() + "'. Espere a que termine.");
        }
        Ejecucion x = crear(e, script, OrigenEjecucion.MANUAL, null);
        lanzarAlConfirmar(x.getId());
        return EjecucionRespuesta.de(x);
    }

    /**
     * Ejecucion pedida por el programador. Corre dentro de su transaccion, asi que no lanza
     * excepciones por condiciones esperadas: las registra como alerta y no ejecuta.
     */
    @Transactional
    public Optional<Long> iniciarProgramada(Long estrategiaId, Long scriptId, LocalDateTime fechaProgramada) {
        Estrategia e = estrategias.findById(estrategiaId).orElseThrow();
        ScriptRman script = scripts.findById(scriptId).orElseThrow();

        ResultadoValidacion validacion = validador.validar(e);
        if (!validacion.valida()) {
            alertar(e, null, "VALIDACION_FALLIDA", "La ejecucion programada para " + fechaProgramada
                    + " no se realizo porque la estrategia ya no pasa la validacion: "
                    + validacion.bloqueantes().stream().map(Hallazgo::texto).collect(Collectors.joining(" ")), true);
            return Optional.empty();
        }
        if (ejecuciones.existsByBaseDatosIdAndEstado(e.getBaseDatos().getId(), EstadoEjecucion.EN_CURSO)) {
            alertar(e, null, "EJECUCION_EN_CURSO", "La ejecucion programada para " + fechaProgramada
                    + " no se realizo porque habia otra ejecucion en curso sobre la misma base.", false);
            return Optional.empty();
        }
        Ejecucion x = crear(e, script, OrigenEjecucion.PROGRAMADA, fechaProgramada);
        lanzarAlConfirmar(x.getId());
        return Optional.of(x.getId());
    }

    /** Registra la ejecucion EN_CURSO con copia del script y del destino (para que el historial no cambie). */
    private Ejecucion crear(Estrategia e, ScriptRman script, OrigenEjecucion origen, LocalDateTime fechaProgramada) {
        Ejecucion x = new Ejecucion();
        x.setEstrategia(e);
        x.setScript(script);
        x.setBaseDatos(e.getBaseDatos());
        x.setOrigen(origen);
        x.setEstado(EstadoEjecucion.EN_CURSO);
        x.setTipoRespaldo(e.getTipoRespaldo());
        x.setScriptEjecutado(script.getContenido());
        x.setRutaDestino(e.getRutaDestino());
        x.setFechaProgramada(fechaProgramada);
        x.setFechaInicio(LocalDateTime.now());
        return ejecuciones.saveAndFlush(x);
    }

    /** El proceso en segundo plano empieza cuando la ejecucion EN_CURSO ya esta guardada. */
    private void lanzarAlConfirmar(Long ejecucionId) {
        Runnable tarea = () -> ejecutar(ejecucionId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ejecutor.execute(tarea);
                }
            });
        } else {
            ejecutor.execute(tarea);
        }
    }

    // --- Ejecucion -----------------------------------------------------------------------

    private record Preparacion(String contenedor, String script, ObjetosRespaldados objetos) {
    }

    /** Corre RMAN, verifica y guarda la evidencia. Publico para poder probarlo de forma sincronica. */
    public void ejecutar(Long ejecucionId) {
        try {
            Preparacion prep = transaccion.execute(estado -> {
                Ejecucion x = ejecuciones.findById(ejecucionId).orElseThrow();
                return new Preparacion(x.getBaseDatos().getContenedor(), x.getScriptEjecutado(),
                        VerificadorRespaldo.objetosDe(x.getEstrategia()));
            });

            ResultadoProceso proceso = cliente.ejecutarRman(prep.contenedor(), prep.script());
            Analisis analisis = analizador.analizarRespaldo(proceso.salida());
            Resultado inicial = ClasificadorResultado.delRespaldo(proceso, analisis);

            List<EvidenciaArchivo> archivos = new ArrayList<>();
            List<String> faltantes = new ArrayList<>();
            AnalisisVerificacion verificacion = null;
            String salidaVerificacion = null;
            String errorVerificacion = null;
            long tamano = 0;

            if (inicial == null) {
                for (Pieza p : analisis.piezas()) {
                    EvidenciaArchivo a = new EvidenciaArchivo(p.ruta(), p.tipo());
                    try {
                        Optional<Long> bytes = cliente.tamanoArchivo(prep.contenedor(), p.ruta());
                        a.setExiste(bytes.isPresent());
                        a.setTamanoBytes(bytes.orElse(null));
                        tamano += bytes.orElse(0L);
                        if (bytes.isEmpty()) {
                            faltantes.add(p.ruta());
                        }
                    } catch (AmbienteException ex) {
                        errorVerificacion = ex.getMessage();
                    }
                    a.setFechaVerificacion(LocalDateTime.now());
                    archivos.add(a);
                }
                try {
                    String scriptVerificacion = verificador.script(ejecucionId, analisis.piezas(), prep.objetos());
                    ResultadoProceso rv = cliente.ejecutarRman(prep.contenedor(), scriptVerificacion);
                    verificacion = analizador.analizarVerificacion(rv.salida());
                    salidaVerificacion = "== Script de verificacion ==\n" + scriptVerificacion
                            + "\n== Salida de RMAN ==\n" + rv.salida();
                } catch (AmbienteException | IllegalArgumentException ex) {
                    errorVerificacion = ex.getMessage();
                }
            }

            Resultado resultado = inicial != null ? inicial : ClasificadorResultado.finalizar(
                    new ClasificadorResultado.Entrada(proceso, analisis, faltantes, verificacion,
                            analisis.piezas().size(), errorVerificacion));
            long tamanoFinal = tamano;
            String salidaFinalVerificacion = salidaVerificacion;

            transaccion.executeWithoutResult(estado -> {
                Ejecucion x = ejecuciones.findById(ejecucionId).orElseThrow();
                x.setCodigoSalida(proceso.codigoSalida());
                x.setSalidaRman(proceso.salida());
                archivos.forEach(x::agregarArchivo);
                x.setTamanoTotalBytes(archivos.isEmpty() ? null : tamanoFinal);
                x.setSalidaVerificacion(salidaFinalVerificacion);
                terminar(x, resultado.estado(), resultado.verificacion(), resultado.mensaje());
            });
        } catch (RuntimeException ex) {
            log.error("Fallo la ejecucion {}", ejecucionId, ex);
            transaccion.executeWithoutResult(estado -> ejecuciones.findById(ejecucionId).ifPresent(x ->
                    terminar(x, EstadoEjecucion.FALLIDO, EstadoVerificacion.NO_APLICA,
                            "Error de la aplicacion durante la ejecucion: " + ex.getMessage())));
        }
    }

    /** Cierra una ejecucion; si fallo, deja una alerta asociada. */
    void terminar(Ejecucion x, EstadoEjecucion estado, EstadoVerificacion verificacion, String mensaje) {
        x.setEstado(estado);
        x.setVerificacion(verificacion);
        x.setMensajeError(mensaje == null ? null : recortar(mensaje, MAXIMO_MENSAJE));
        x.setFechaFin(LocalDateTime.now());
        x.setDuracionSegundos(Duration.between(x.getFechaInicio(), x.getFechaFin()).toSeconds());
        if (estado == EstadoEjecucion.FALLIDO) {
            alertar(x.getEstrategia(), x, "EJECUCION_FALLIDA",
                    ("La ejecucion " + x.getId() + " de la estrategia '" + x.getEstrategia().getNombre()
                            + "' fallo: " + mensaje), false);
        }
    }

    // --- Consultas -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EjecucionResumen> historial(Long estrategiaId, EstadoEjecucion estado) {
        return ejecuciones.findAllByOrderByFechaInicioDesc().stream()
                .filter(x -> estrategiaId == null || x.getEstrategia().getId().equals(estrategiaId))
                .filter(x -> estado == null || x.getEstado() == estado)
                .map(EjecucionResumen::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public EjecucionRespuesta obtener(Long id) {
        return ejecuciones.findById(id).map(EjecucionRespuesta::de)
                .orElseThrow(() -> new NoEncontradoException("No existe la ejecucion con id " + id + "."));
    }

    private void alertar(Estrategia e, Ejecucion x, String codigo, String mensaje, boolean unica) {
        if (unica && alertas.existsByEstrategiaIdAndCodigoAndEstado(e.getId(), codigo, EstadoAlerta.ABIERTA)) {
            return;
        }
        Alerta alerta = new Alerta(TipoMensaje.ADVERTENCIA, codigo, recortar(mensaje, MAXIMO_ALERTA));
        alerta.setEstrategia(e);
        alerta.setBaseDatos(e.getBaseDatos());
        alerta.setEjecucion(x);
        alertas.save(alerta);
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }
}
