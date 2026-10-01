package com.example.respaldos.modelo;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Guarda los dias como texto "LUN,MIE,VIE" (siempre en orden de la semana). Vacio = NULL. */
@Converter
public class DiasSemanaConverter implements AttributeConverter<Set<DiaSemana>, String> {

    @Override
    public String convertToDatabaseColumn(Set<DiaSemana> dias) {
        if (dias == null || dias.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(dias).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<DiaSemana> convertToEntityAttribute(String texto) {
        if (texto == null || texto.isBlank()) {
            return EnumSet.noneOf(DiaSemana.class);
        }
        return Arrays.stream(texto.split(","))
                .map(String::trim)
                .map(DiaSemana::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DiaSemana.class)));
    }
}
