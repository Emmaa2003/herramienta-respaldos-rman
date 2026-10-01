package com.example.respaldos.aprobacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param hashContenido huella del script que el administrador reviso. Debe coincidir con la
 *                      guardada: asi se aprueba exactamente el texto que se mostro.
 */
public record AprobacionSolicitud(
        @NotBlank @Size(max = 100) String aprobadoPor,
        @NotBlank @Pattern(regexp = "^[0-9a-f]{64}$", message = "debe ser la huella SHA-256 del script")
        String hashContenido,
        @Size(max = 1000) String comentario) {
}
