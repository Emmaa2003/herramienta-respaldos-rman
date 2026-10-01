package com.example.respaldos.programacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param habilitado si el programador revisa solo (en pruebas se apaga)
 * @param intervalo  cada cuanto revisa las programaciones
 * @param tolerancia retraso maximo con el que una ejecucion todavia se hace; mas alla se omite
 */
@ConfigurationProperties("respaldos.programador")
public record PropiedadesProgramador(boolean habilitado, Duration intervalo, Duration tolerancia) {
}
