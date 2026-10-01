package com.example.respaldos.infraestructura;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuracion del ambiente Docker donde corre RMAN (prefijo respaldos.docker).
 *
 * @param ejecutable             comando docker (en el PATH o ruta completa)
 * @param contenedoresPermitidos unicos contenedores sobre los que la app puede ejecutar comandos
 * @param tiempoMaximoRman       limite para un script RMAN
 * @param tiempoMaximoComando    limite para comandos auxiliares (df, stat, inspect)
 */
@ConfigurationProperties("respaldos.docker")
public record PropiedadesDocker(
        String ejecutable,
        List<String> contenedoresPermitidos,
        Duration tiempoMaximoRman,
        Duration tiempoMaximoComando) {
}
