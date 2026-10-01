package com.example.respaldos.programacion;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class ProgramacionController {

    private final ProgramacionService servicio;

    public ProgramacionController(ProgramacionService servicio) {
        this.servicio = servicio;
    }

    /** Todas las programaciones, con su proxima ejecucion y si realmente se ejecutaran. */
    @GetMapping("/api/programacion")
    public List<EstadoProgramacion> listar() {
        return servicio.listar();
    }

    /** Las proximas fechas en que se ejecutara la estrategia (para revisar el CUANDO). */
    @GetMapping("/api/estrategias/{id}/programacion/proximas")
    public List<LocalDateTime> proximas(@PathVariable Long id, @RequestParam(defaultValue = "5") int cantidad) {
        return servicio.proximas(id, cantidad);
    }

    @PostMapping("/api/estrategias/{id}/programacion/activar")
    public EstadoProgramacion activar(@PathVariable Long id) {
        return servicio.activar(id);
    }

    @PostMapping("/api/estrategias/{id}/programacion/desactivar")
    public EstadoProgramacion desactivar(@PathVariable Long id) {
        return servicio.desactivar(id);
    }
}
