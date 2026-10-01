package com.example.respaldos.aprobacion;

import com.example.respaldos.generador.ScriptRespuesta;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class AprobacionController {

    private final AprobacionService servicio;

    public AprobacionController(AprobacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/api/scripts/{id}/aprobacion")
    public AprobacionRespuesta aprobar(@PathVariable Long id, @Valid @RequestBody AprobacionSolicitud solicitud) {
        return servicio.aprobar(id, solicitud);
    }

    @PostMapping("/api/scripts/{id}/rechazo")
    public ScriptRespuesta rechazar(@PathVariable Long id, @Valid @RequestBody RechazoSolicitud solicitud) {
        return servicio.rechazar(id, solicitud);
    }

    /** Script listo para ejecutar (aprobado, vigente e integro); 404 o 409 explican por que no hay. */
    @GetMapping("/api/estrategias/{id}/script-aprobado")
    public ScriptRespuesta scriptAprobado(@PathVariable Long id) {
        return ScriptRespuesta.de(servicio.scriptEjecutable(id));
    }
}
