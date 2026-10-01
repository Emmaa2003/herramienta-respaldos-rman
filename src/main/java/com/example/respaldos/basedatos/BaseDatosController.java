package com.example.respaldos.basedatos;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/bases-datos")
public class BaseDatosController {

    private final BaseDatosService servicio;

    public BaseDatosController(BaseDatosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<BaseDatosRespuesta> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    public BaseDatosRespuesta obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    public ResponseEntity<BaseDatosRespuesta> crear(@Valid @RequestBody BaseDatosSolicitud solicitud) {
        BaseDatosRespuesta creada = servicio.crear(solicitud);
        var ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    public BaseDatosRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody BaseDatosSolicitud solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }

    /** Lee modo de archivado y catalogo y devuelve mensajes. Solo lectura sobre Oracle. */
    @PostMapping("/{id}/inspeccion")
    public ResultadoInspeccion inspeccionar(@PathVariable Long id) {
        return servicio.inspeccionar(id);
    }
}
