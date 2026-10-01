package com.example.respaldos.aprobacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazoSolicitud(
        @NotBlank @Size(max = 100) String rechazadoPor,
        @NotBlank @Size(max = 1000) String motivo) {
}
