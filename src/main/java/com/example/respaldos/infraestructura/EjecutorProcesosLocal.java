package com.example.respaldos.infraestructura;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implementacion con ProcessBuilder. Los argumentos van como lista (sin shell),
 * asi que rutas o nombres no pueden inyectar comandos.
 */
@Component
public class EjecutorProcesosLocal implements EjecutorProcesos {

    @Override
    public ResultadoProceso ejecutar(List<String> comando, String entrada, Duration tiempoMaximo) {
        LocalDateTime inicio = LocalDateTime.now();
        Process proceso;
        try {
            proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new AmbienteException("No se pudo iniciar el comando: " + String.join(" ", comando), e);
        }

        // La salida se lee en paralelo: si nadie la consume, el buffer se llena y el proceso se bloquea.
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Thread lector = Thread.ofVirtual().start(() -> {
            try (InputStream in = proceso.getInputStream()) {
                in.transferTo(salida);
            } catch (IOException ignorada) {
                // El proceso termino o se detuvo; lo leido hasta aqui se conserva.
            }
        });

        try (OutputStream stdin = proceso.getOutputStream()) {
            if (entrada != null) {
                stdin.write(entrada.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // El proceso cerro stdin antes de tiempo (p. ej. docker fallo al arrancar); su salida lo explicara.
        }

        try {
            boolean termino = proceso.waitFor(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS);
            if (!termino) {
                proceso.destroyForcibly();
                proceso.waitFor();
            }
            lector.join();
            return new ResultadoProceso(
                    termino ? proceso.exitValue() : null,
                    salida.toString(StandardCharsets.UTF_8),
                    inicio,
                    LocalDateTime.now(),
                    !termino);
        } catch (InterruptedException e) {
            proceso.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new AmbienteException("Se interrumpio la espera del comando: " + String.join(" ", comando), e);
        }
    }
}
