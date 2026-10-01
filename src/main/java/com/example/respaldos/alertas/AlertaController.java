package com.example.respaldos.alertas;

import com.example.respaldos.modelo.EstadoAlerta;
import com.example.respaldos.modelo.TipoMensaje;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService servicio;
    private final MonitorPreventivo monitor;

    public AlertaController(AlertaService servicio, MonitorPreventivo monitor) {
        this.servicio = servicio;
        this.monitor = monitor;
    }

    @GetMapping
    public List<AlertaRespuesta> listar(@RequestParam(required = false) EstadoAlerta estado,
                                        @RequestParam(required = false) TipoMensaje tipo,
                                        @RequestParam(required = false) Long estrategiaId,
                                        @RequestParam(required = false) Long baseDatosId) {
        return servicio.listar(estado, tipo, estrategiaId, baseDatosId);
    }

    /** Cantidad de alertas abiertas por tipo de mensaje. */
    @GetMapping("/resumen")
    public Map<TipoMensaje, Long> resumen() {
        return servicio.resumenAbiertas();
    }

    @PostMapping("/{id}/atender")
    public AlertaRespuesta atender(@PathVariable Long id, @Valid @RequestBody AtencionSolicitud solicitud) {
        return servicio.atender(id, solicitud);
    }

    @PostMapping("/{id}/descartar")
    public AlertaRespuesta descartar(@PathVariable Long id, @Valid @RequestBody AtencionSolicitud solicitud) {
        return servicio.descartar(id, solicitud);
    }

    /** Aplica una recomendacion por decision del administrador (nunca se aplica sola). */
    @PostMapping("/{id}/aplicar")
    public AlertaRespuesta aplicar(@PathVariable Long id, @Valid @RequestBody AtencionSolicitud solicitud) {
        return servicio.aplicar(id, solicitud);
    }

    /** Corre ahora la revision preventiva (ademas de la periodica). */
    @PostMapping("/revision")
    public MonitorPreventivo.ResumenRevision revisar() {
        return monitor.revisar(LocalDateTime.now());
    }
}
