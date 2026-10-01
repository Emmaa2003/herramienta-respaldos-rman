package com.example.respaldos.basedatos;

import com.example.respaldos.modelo.Ambiente;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos para registrar o modificar una base de datos. */
public record BaseDatosSolicitud(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 500) String descripcion,
        @NotBlank @Size(max = 100) String contenedor,
        @NotBlank @Size(max = 100) String servicio,
        @NotNull Ambiente ambiente) {
}
