package com.example.respaldos.estrategia;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/estrategias")
public class EstrategiaController {

    private final EstrategiaService servicio;

    public EstrategiaController(EstrategiaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<EstrategiaRespuesta> listar(@RequestParam(required = false) Long baseDatosId,
                                            @RequestParam(required = false) Boolean activa) {
        return servicio.listar(baseDatosId, activa);
    }

    @GetMapping("/{id}")
    public EstrategiaRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<EstrategiaRespuesta> crear(@Valid @RequestBody EstrategiaSolicitud solicitud) {
        EstrategiaRespuesta creada = servicio.crear(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    public EstrategiaRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody EstrategiaSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    @PostMapping("/{id}/activar")
    public EstrategiaRespuesta activar(@PathVariable Long id) {
        return servicio.activar(id);
    }

    @PostMapping("/{id}/desactivar")
    public EstrategiaRespuesta desactivar(@PathVariable Long id) {
        return servicio.desactivar(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
