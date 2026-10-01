package com.example.respaldos.estrategia;

import com.example.respaldos.modelo.DiaSemana;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Estrategia completa: informacion general, QUE, COMO, CUANDO y destino.
 * En una modificacion reemplaza todo (elementos y programacion incluidos);
 * programacion null quita la programacion.
 * <p>
 * Aqui solo se valida la forma de los datos. Si la estrategia tiene sentido
 * (tablespaces que existan, nivel 1 sin nivel 0, espacio, modo de archivado...)
 * lo revisa el validador antes de generar el script.
 */
public record EstrategiaSolicitud(
        // Informacion general
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 1000) String descripcion,
        @NotNull Long baseDatosId,
        @NotBlank @Size(max = 100) String responsable,
        @NotNull Prioridad prioridad,
        // QUE
        @NotNull List<@Valid @NotNull Elemento> elementos,
        // COMO
        @NotNull TipoRespaldo tipoRespaldo,
        Boolean comprimido,
        @Min(1) @Max(36500) Integer diasRetencion,
        // Destino: va dentro de FORMAT '...' en el script, por eso no admite comillas ni espacios.
        @NotBlank @Size(max = 500)
        @Pattern(regexp = "^/[A-Za-z0-9_./-]*$",
                message = "debe ser una ruta absoluta del contenedor, solo con letras, numeros, _ . / -")
        String rutaDestino,
        // CUANDO
        @Valid ProgramacionDatos programacion) {

    /** @param nombreObjeto nombre del tablespace, o numero o ruta del datafile; null para los demas tipos */
    public record Elemento(@NotNull TipoElemento tipo, @Size(max = 500) String nombreObjeto) {
    }

    /** @param intervalo cada cuantas horas/dias/semanas/meses (1 si se omite) */
    public record ProgramacionDatos(
            @NotNull LocalDate fechaInicio,
            @NotNull LocalTime hora,
            @NotNull Frecuencia frecuencia,
            Set<DiaSemana> diasSemana,
            @Min(1) @Max(999) Integer intervalo,
            LocalTime ventanaInicio,
            LocalTime ventanaFin) {
    }
}
