package com.example.respaldos.modelo;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Guarda una hora como texto HH:MM (Oracle no tiene un tipo solo-hora). */
@Converter
public class HoraConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime hora) {
        return hora == null ? null : hora.format(FORMATO);
    }

    @Override
    public LocalTime convertToEntityAttribute(String texto) {
        return texto == null ? null : LocalTime.parse(texto, FORMATO);
    }
}
