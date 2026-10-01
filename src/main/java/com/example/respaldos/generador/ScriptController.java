package com.example.respaldos.generador;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ScriptController {

    private final ScriptService servicio;

    public ScriptController(ScriptService servicio) {
        this.servicio = servicio;
    }

    /** Valida la estrategia y genera una version nueva de su script. No ejecuta nada. */
    @PostMapping("/api/estrategias/{id}/scripts")
    @ResponseStatus(HttpStatus.CREATED)
    public GeneracionRespuesta generar(@PathVariable Long id) {
        return servicio.generar(id);
    }

    @GetMapping("/api/estrategias/{id}/scripts")
    public List<ScriptRespuesta> listar(@PathVariable Long id) {
        return servicio.listar(id);
    }

    @GetMapping("/api/scripts/{id}")
    public ScriptRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }
}
