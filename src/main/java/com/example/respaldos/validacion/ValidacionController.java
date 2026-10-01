package com.example.respaldos.validacion;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidacionController {

    private final ValidadorEstrategia validador;

    public ValidacionController(ValidadorEstrategia validador) {
        this.validador = validador;
    }

    /** Valida la estrategia guardada contra la base real. Solo lectura. */
    @PostMapping("/api/estrategias/{id}/validacion")
    public ResultadoValidacion validar(@PathVariable Long id) {
        return validador.validar(id);
    }
}
