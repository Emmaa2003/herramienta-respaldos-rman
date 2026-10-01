package com.example.respaldos.estrategia;

import com.example.respaldos.aprobacion.InvalidadorScripts;
import com.example.respaldos.comun.ConflictoException;
import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.comun.SolicitudInvalidaException;
import com.example.respaldos.estrategia.EstrategiaSolicitud.Elemento;
import com.example.respaldos.estrategia.EstrategiaSolicitud.ProgramacionDatos;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.programacion.CalculadoraEjecuciones;
import com.example.respaldos.repositorio.AlertaRepository;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import com.example.respaldos.repositorio.ScriptRmanRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Administracion de estrategias: crear, modificar, activar, desactivar, consultar y eliminar.
 * Solo revisa que los datos esten bien formados; la validacion de la estrategia
 * contra la base real es un paso aparte, previo a generar el script.
 */
@Service
@Transactional
public class EstrategiaService {

    /** Nombre de tablespace de Oracle, opcionalmente con el PDB: USERS o XEPDB1:USERS. */
    private static final Pattern TABLESPACE = Pattern.compile("^([A-Z][A-Z0-9_$#]*:)?[A-Z][A-Z0-9_$#]*$");
    /** Datafile por numero (12) o por ruta absoluta sin comillas ni espacios. */
    private static final Pattern DATAFILE = Pattern.compile("^([1-9][0-9]*|/[A-Za-z0-9_./-]+)$");

    private final EstrategiaRepository estrategias;
    private final BaseDatosRepository bases;
    private final ScriptRmanRepository scripts;
    private final AlertaRepository alertas;
    private final InvalidadorScripts invalidador;
    private final CalculadoraEjecuciones calculadora;

    public EstrategiaService(EstrategiaRepository estrategias, BaseDatosRepository bases,
                             ScriptRmanRepository scripts, AlertaRepository alertas,
                             InvalidadorScripts invalidador, CalculadoraEjecuciones calculadora) {
        this.estrategias = estrategias;
        this.bases = bases;
        this.scripts = scripts;
        this.alertas = alertas;
        this.invalidador = invalidador;
        this.calculadora = calculadora;
    }

    @Transactional(readOnly = true)
    public List<EstrategiaRespuesta> listar(Long baseDatosId, Boolean activa) {
        return estrategias.findAll(Sort.by("nombre")).stream()
                .filter(e -> baseDatosId == null || e.getBaseDatos().getId().equals(baseDatosId))
                .filter(e -> activa == null || e.isActiva() == activa)
                .map(EstrategiaRespuesta::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public EstrategiaRespuesta obtener(Long id) {
        return EstrategiaRespuesta.de(buscar(id));
    }

    /** Las estrategias nacen inactivas: se activan explicitamente. */
    public EstrategiaRespuesta crear(EstrategiaSolicitud solicitud) {
        if (estrategias.existsByNombreIgnoreCase(solicitud.nombre().trim())) {
            throw new ConflictoException("Ya existe una estrategia con el nombre '" + solicitud.nombre() + "'.");
        }
        Estrategia estrategia = new Estrategia();
        aplicar(solicitud, estrategia);
        return EstrategiaRespuesta.de(estrategias.save(estrategia));
    }

    public EstrategiaRespuesta actualizar(Long id, EstrategiaSolicitud solicitud) {
        Estrategia estrategia = buscar(id);
        if (estrategias.existsByNombreIgnoreCaseAndIdNot(solicitud.nombre().trim(), id)) {
            throw new ConflictoException("Ya existe una estrategia con el nombre '" + solicitud.nombre() + "'.");
        }
        aplicar(solicitud, estrategia);
        invalidador.invalidarSiCambio(estrategia);
        return EstrategiaRespuesta.de(estrategias.saveAndFlush(estrategia));
    }

    /** Al activar se recalcula la proxima ejecucion desde ahora (no se arrastran las de cuando estaba inactiva). */
    public EstrategiaRespuesta activar(Long id) {
        Estrategia estrategia = buscar(id);
        estrategia.setActiva(true);
        Programacion p = estrategia.getProgramacion();
        if (p != null) {
            p.setProximaEjecucion(calculadora.siguienteDesde(p, LocalDateTime.now()));
        }
        return EstrategiaRespuesta.de(estrategias.saveAndFlush(estrategia));
    }

    public EstrategiaRespuesta desactivar(Long id) {
        Estrategia estrategia = buscar(id);
        estrategia.setActiva(false);
        return EstrategiaRespuesta.de(estrategias.saveAndFlush(estrategia));
    }

    /** Solo si no tiene historial (scripts, ejecuciones o alertas); si lo tiene, se desactiva. */
    public void eliminar(Long id) {
        Estrategia estrategia = buscar(id);
        if (scripts.existsByEstrategiaId(id) || alertas.existsByEstrategiaId(id)) {
            throw new ConflictoException("La estrategia '" + estrategia.getNombre() + "' tiene scripts, "
                    + "ejecuciones o alertas registradas. Desactivela para conservar la evidencia.");
        }
        estrategias.delete(estrategia);
    }

    private Estrategia buscar(Long id) {
        return estrategias.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la estrategia con id " + id + "."));
    }

    private void aplicar(EstrategiaSolicitud s, Estrategia estrategia) {
        List<String> errores = new ArrayList<>();
        List<EstrategiaElemento> elementos = normalizarElementos(s.elementos(), errores);
        validarProgramacion(s.programacion(), errores);
        if (!errores.isEmpty()) {
            throw new SolicitudInvalidaException(String.join(" ", errores));
        }
        BaseDatos base = bases.findById(s.baseDatosId())
                .orElseThrow(() -> new SolicitudInvalidaException(
                        "No existe la base de datos con id " + s.baseDatosId() + "."));

        estrategia.setNombre(s.nombre().trim());
        estrategia.setDescripcion(s.descripcion());
        estrategia.setBaseDatos(base);
        estrategia.setResponsable(s.responsable().trim());
        estrategia.setPrioridad(s.prioridad());
        estrategia.setTipoRespaldo(s.tipoRespaldo());
        estrategia.setComprimido(Boolean.TRUE.equals(s.comprimido()));
        estrategia.setDiasRetencion(s.diasRetencion());
        estrategia.setRutaDestino(quitarBarraFinal(s.rutaDestino()));
        estrategia.reemplazarElementos(elementos);
        aplicarProgramacion(s.programacion(), estrategia);
    }

    /** Normaliza nombres (mayusculas para tablespaces) y detecta errores de forma y duplicados. */
    private static List<EstrategiaElemento> normalizarElementos(List<Elemento> elementos, List<String> errores) {
        List<EstrategiaElemento> resultado = new ArrayList<>();
        for (Elemento e : elementos) {
            String nombre = e.nombreObjeto() == null || e.nombreObjeto().isBlank() ? null : e.nombreObjeto().trim();
            TipoElemento tipo = e.tipo();
            if (tipo.requiereNombre() && nombre == null) {
                errores.add("El elemento " + tipo + " necesita el nombre del objeto.");
                continue;
            }
            if (!tipo.requiereNombre() && nombre != null) {
                errores.add("El elemento " + tipo + " no lleva nombre de objeto.");
                continue;
            }
            if (tipo == TipoElemento.TABLESPACE) {
                nombre = nombre.toUpperCase(Locale.ROOT);
                if (!TABLESPACE.matcher(nombre).matches()) {
                    errores.add("'" + e.nombreObjeto() + "' no es un nombre de tablespace valido.");
                    continue;
                }
            }
            if (tipo == TipoElemento.DATAFILE && (!DATAFILE.matcher(nombre).matches() || nombre.contains(".."))) {
                errores.add("'" + e.nombreObjeto() + "' no es un datafile valido: use su numero o su ruta absoluta.");
                continue;
            }
            EstrategiaElemento elemento = new EstrategiaElemento(tipo, nombre);
            if (resultado.stream().anyMatch(elemento::mismoObjeto)) {
                errores.add("El elemento " + tipo + (nombre == null ? "" : " " + nombre) + " esta repetido.");
                continue;
            }
            resultado.add(elemento);
        }
        return resultado;
    }

    private static void validarProgramacion(ProgramacionDatos p, List<String> errores) {
        if (p == null) {
            return;
        }
        boolean tieneDias = p.diasSemana() != null && !p.diasSemana().isEmpty();
        if (p.frecuencia() == Frecuencia.SEMANAL && !tieneDias) {
            errores.add("La frecuencia SEMANAL necesita al menos un dia de ejecucion.");
        }
        if (p.frecuencia() != Frecuencia.SEMANAL && tieneDias) {
            errores.add("Los dias de ejecucion solo aplican a la frecuencia SEMANAL.");
        }
        if (p.frecuencia() == Frecuencia.UNA_VEZ && p.intervalo() != null && p.intervalo() != 1) {
            errores.add("La frecuencia UNA_VEZ no lleva intervalo.");
        }
        if ((p.ventanaInicio() == null) != (p.ventanaFin() == null)) {
            errores.add("La ventana de respaldo necesita hora de inicio y de fin.");
        }
        if (p.ventanaInicio() != null && p.ventanaInicio().equals(p.ventanaFin())) {
            errores.add("La ventana de respaldo no puede empezar y terminar a la misma hora.");
        }
    }

    /**
     * Modifica la programacion existente en lugar de reemplazarla: la tabla admite una
     * sola por estrategia y Hibernate insertaria la nueva antes de borrar la anterior.
     */
    private void aplicarProgramacion(ProgramacionDatos datos, Estrategia estrategia) {
        if (datos == null) {
            estrategia.asignarProgramacion(null);
            return;
        }
        Programacion p = estrategia.getProgramacion();
        if (p == null) {
            p = new Programacion();
            estrategia.asignarProgramacion(p);
        }
        p.setFechaInicio(datos.fechaInicio());
        p.setHora(datos.hora().withSecond(0).withNano(0));
        p.setFrecuencia(datos.frecuencia());
        p.setDiasSemana(datos.diasSemana());
        p.setIntervalo(datos.intervalo() == null ? 1 : datos.intervalo());
        p.setVentanaInicio(datos.ventanaInicio() == null ? null : datos.ventanaInicio().withSecond(0).withNano(0));
        p.setVentanaFin(datos.ventanaFin() == null ? null : datos.ventanaFin().withSecond(0).withNano(0));
        // Un cambio de horario se refleja de inmediato en la proxima ejecucion.
        p.setProximaEjecucion(calculadora.siguienteDesde(p, LocalDateTime.now()));
    }

    private static String quitarBarraFinal(String ruta) {
        String r = ruta.trim();
        return r.length() > 1 && r.endsWith("/") ? r.substring(0, r.length() - 1) : r;
    }
}
