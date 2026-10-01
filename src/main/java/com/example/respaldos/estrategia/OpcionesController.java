package com.example.respaldos.estrategia;

import com.example.respaldos.modelo.DiaSemana;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Valores permitidos y sus descripciones, para construir el formulario de estrategias. */
@RestController
@RequestMapping("/api/opciones")
public class OpcionesController {

    public record OpcionPrioridad(Prioridad valor, String descripcion, String criterio, int horasMaximasSinRespaldo) {
    }

    public record OpcionTipoRespaldo(TipoRespaldo valor, String nombre, String descripcion, boolean incremental) {
    }

    public record OpcionElemento(TipoElemento valor, String descripcion, boolean requiereNombre) {
    }

    public record OpcionFrecuencia(Frecuencia valor, String descripcion) {
    }

    public record Opciones(
            List<OpcionPrioridad> prioridades,
            List<OpcionTipoRespaldo> tiposRespaldo,
            List<OpcionElemento> elementos,
            List<OpcionFrecuencia> frecuencias,
            List<DiaSemana> diasSemana) {
    }

    @GetMapping
    public Opciones opciones() {
        return new Opciones(
                Arrays.stream(Prioridad.values())
                        .map(p -> new OpcionPrioridad(p, p.getDescripcion(), p.getCriterio(), p.getHorasMaximasSinRespaldo()))
                        .toList(),
                Arrays.stream(TipoRespaldo.values())
                        .map(t -> new OpcionTipoRespaldo(t, t.getNombre(), t.getDescripcion(), t.esIncremental()))
                        .toList(),
                Arrays.stream(TipoElemento.values())
                        .map(e -> new OpcionElemento(e, e.getDescripcion(), e.requiereNombre()))
                        .toList(),
                Arrays.stream(Frecuencia.values())
                        .map(f -> new OpcionFrecuencia(f, f.getDescripcion()))
                        .toList(),
                List.of(DiaSemana.values()));
    }
}
