package com.example.respaldos.validacion;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param valida    true si no hay hallazgos bloqueantes: se puede generar el script
 * @param hallazgos primero los bloqueantes, luego advertencias, recomendaciones e informativos
 */
public record ResultadoValidacion(Long estrategiaId, boolean valida, LocalDateTime fecha, List<Hallazgo> hallazgos) {

    public List<Hallazgo> bloqueantes() {
        return hallazgos.stream().filter(Hallazgo::bloqueante).toList();
    }
}
