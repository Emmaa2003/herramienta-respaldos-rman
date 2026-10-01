package com.example.respaldos.ejecucion;

import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.EstadoVerificacion;
import com.example.respaldos.repositorio.EjecucionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ConfiguracionEjecucion {

    /** Hilos para correr RMAN en segundo plano sin bloquear la API ni el programador. */
    @Bean(name = "ejecutorRespaldos", destroyMethod = "close")
    ExecutorService ejecutorRespaldos() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Si la aplicacion se detuvo con ejecuciones EN_CURSO, nadie las va a terminar: al
     * arrancar se cierran como fallidas para que el historial no quede con estados falsos.
     */
    @EventListener(ApplicationReadyEvent.class)
    void cerrarEjecucionesInterrumpidas(ApplicationReadyEvent evento) {
        EjecucionRepository ejecuciones = evento.getApplicationContext().getBean(EjecucionRepository.class);
        EjecucionService servicio = evento.getApplicationContext().getBean(EjecucionService.class);
        TransactionTemplate tx = evento.getApplicationContext().getBean(TransactionTemplate.class);
        tx.executeWithoutResult(estado -> ejecuciones.findByEstado(EstadoEjecucion.EN_CURSO).forEach(x ->
                servicio.terminar(x, EstadoEjecucion.FALLIDO, EstadoVerificacion.NO_APLICA,
                        "La aplicacion se detuvo mientras la ejecucion estaba en curso; no hay constancia del "
                                + "resultado. Revise el contenedor antes de volver a ejecutar.")));
    }
}
