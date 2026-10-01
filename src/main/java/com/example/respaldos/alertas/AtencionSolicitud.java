package com.example.respaldos.alertas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Quien atiende, descarta o aplica una alerta, y por que. */
public record AtencionSolicitud(
        @NotBlank @Size(max = 100) String atendidaPor,
        @Size(max = 1000) String comentario) {
}
