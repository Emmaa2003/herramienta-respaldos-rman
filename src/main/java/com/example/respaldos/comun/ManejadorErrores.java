package com.example.respaldos.comun;

import com.example.respaldos.infraestructura.AmbienteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce las excepciones a respuestas ProblemDetail (RFC 9457). */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(NoEncontradoException.class)
    ProblemDetail noEncontrado(NoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail conflicto(ConflictoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    ProblemDetail solicitudInvalida(SolicitudInvalidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** El ambiente (Docker, Oracle) no respondio como se esperaba. */
    @ExceptionHandler(AmbienteException.class)
    ProblemDetail ambiente(AmbienteException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** JSON mal formado o con valores que no corresponden (p. ej. un tipo de respaldo inexistente). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail jsonIlegible(HttpMessageNotReadableException e) {
        Throwable causa = e.getMostSpecificCause();
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "No se pudo leer la solicitud: " + causa.getMessage().lines().findFirst().orElse(""));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos no validos");
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        problema.setProperty("campos", campos);
        return problema;
    }
}
