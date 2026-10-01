package com.example.respaldos.programacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa la revision periodica solo si respaldos.programador.habilitado=true. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "respaldos.programador.habilitado", havingValue = "true")
public class ConfiguracionProgramador {
}
