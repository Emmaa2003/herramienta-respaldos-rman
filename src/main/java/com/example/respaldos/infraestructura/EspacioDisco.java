package com.example.respaldos.infraestructura;

/** Espacio del sistema de archivos que contiene una ruta (segun df dentro del contenedor). */
public record EspacioDisco(
        String sistemaArchivos,
        String puntoMontaje,
        long totalBytes,
        long usadosBytes,
        long disponiblesBytes) {
}
