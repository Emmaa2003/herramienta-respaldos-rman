package com.example.respaldos.ejecucion;

import com.example.respaldos.modelo.EstadoEjecucion;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class EjecucionController {

    private final EjecucionService servicio;

    public EjecucionController(EjecucionService servicio) {
        this.servicio = servicio;
    }

    /**
     * Ejecuta ahora el script aprobado de la estrategia. Responde enseguida con la
     * ejecucion EN_CURSO; el resultado se consulta en /api/ejecuciones/{id}.
     */
    @PostMapping("/api/estrategias/{id}/ejecuciones")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EjecucionRespuesta ejecutar(@PathVariable Long id) {
        return servicio.ejecutarManual(id);
    }

    /** Historial de ejecuciones, de la mas reciente a la mas antigua. */
    @GetMapping("/api/ejecuciones")
    public List<EjecucionResumen> historial(@RequestParam(required = false) Long estrategiaId,
                                            @RequestParam(required = false) EstadoEjecucion estado) {
        return servicio.historial(estrategiaId, estado);
    }

    /** Evidencia completa: script ejecutado, salida de RMAN, archivos y verificacion. */
    @GetMapping("/api/ejecuciones/{id}")
    public EjecucionRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }
}
