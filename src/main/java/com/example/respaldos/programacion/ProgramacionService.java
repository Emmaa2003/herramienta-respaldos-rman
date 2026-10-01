package com.example.respaldos.programacion;

import com.example.respaldos.aprobacion.AprobacionService;
import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.programacion.EstadoProgramacion.Estado;
import com.example.respaldos.repositorio.ProgramacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Consulta y activacion de programaciones. La ejecucion periodica esta en {@link ProgramadorRespaldos}. */
@Service
@Transactional
public class ProgramacionService {

    private final ProgramacionRepository programaciones;
    private final CalculadoraEjecuciones calculadora;
    private final AprobacionService aprobacion;

    public ProgramacionService(ProgramacionRepository programaciones, CalculadoraEjecuciones calculadora,
                               AprobacionService aprobacion) {
        this.programaciones = programaciones;
        this.calculadora = calculadora;
        this.aprobacion = aprobacion;
    }

    @Transactional(readOnly = true)
    public List<EstadoProgramacion> listar() {
        return programaciones.findAll().stream()
                .map(this::estado)
                .sorted(Comparator.comparing(EstadoProgramacion::proximaEjecucion,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LocalDateTime> proximas(Long estrategiaId, int cantidad) {
        return calculadora.proximas(buscar(estrategiaId), LocalDateTime.now(), Math.clamp(cantidad, 1, 50));
    }

    /** Al activar se recalcula desde ahora: no se arrastran ejecuciones de cuando estaba inactiva. */
    public EstadoProgramacion activar(Long estrategiaId) {
        Programacion p = buscar(estrategiaId);
        p.setActiva(true);
        p.setProximaEjecucion(calculadora.siguienteDesde(p, LocalDateTime.now()));
        return estado(p);
    }

    public EstadoProgramacion desactivar(Long estrategiaId) {
        Programacion p = buscar(estrategiaId);
        p.setActiva(false);
        return estado(p);
    }

    private EstadoProgramacion estado(Programacion p) {
        Estrategia e = p.getEstrategia();
        Estado estado;
        String detalle;
        AprobacionService.Ejecutable ejecutable = aprobacion.evaluarEjecutable(e.getId());
        if (!p.isActiva()) {
            estado = Estado.PROGRAMACION_INACTIVA;
            detalle = "La programacion esta desactivada.";
        } else if (!e.isActiva()) {
            estado = Estado.ESTRATEGIA_INACTIVA;
            detalle = "La estrategia esta inactiva: no se ejecutara hasta activarla.";
        } else if (ejecutable.script() == null) {
            estado = Estado.SIN_SCRIPT_EJECUTABLE;
            detalle = ejecutable.motivo();
        } else if (p.getProximaEjecucion() == null && calculadora.siguienteDesde(p, LocalDateTime.now()) == null) {
            estado = Estado.SIN_PROXIMA;
            detalle = "No hay mas ejecuciones: la fecha ya paso o ninguna ocurrencia cae dentro de la ventana.";
        } else {
            estado = Estado.LISTA;
            detalle = "Se ejecutara con el script aprobado version " + ejecutable.script().getVersion() + ".";
        }
        return new EstadoProgramacion(e.getId(), e.getNombre(), p.getFrecuencia(), p.isActiva(),
                p.getProximaEjecucion(), estado, detalle);
    }

    private Programacion buscar(Long estrategiaId) {
        return programaciones.findByEstrategiaId(estrategiaId).orElseThrow(() -> new NoEncontradoException(
                "La estrategia " + estrategiaId + " no tiene programacion (o no existe)."));
    }
}
