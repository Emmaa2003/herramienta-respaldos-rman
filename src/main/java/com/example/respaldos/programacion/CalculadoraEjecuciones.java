package com.example.respaldos.programacion;

import com.example.respaldos.modelo.DiaSemana;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Programacion;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula cuando le toca ejecutarse a una programacion (CUANDO).
 * <p>
 * Ocurrencias por frecuencia, a partir de fecha de inicio + hora:
 * <ul>
 *   <li>UNA_VEZ: solo esa.</li>
 *   <li>CADA_N_HORAS: cada N horas.</li>
 *   <li>DIARIA: cada N dias.</li>
 *   <li>SEMANAL: los dias elegidos, en una de cada N semanas (contando desde la semana de inicio).</li>
 *   <li>MENSUAL: cada N meses el mismo dia; si el mes es mas corto, su ultimo dia.</li>
 * </ul>
 * Si hay ventana de respaldo, las ocurrencias fuera de ella se saltan: el respaldo solo
 * empieza dentro de la ventana. Todo en hora local del servidor de la aplicacion.
 */
@Component
public class CalculadoraEjecuciones {

    /** Limite de ocurrencias revisadas: evita ciclos sin fin si ninguna cae en la ventana. */
    private static final int MAXIMO_INTENTOS = 5_000;

    /** Primera ocurrencia en o despues de {@code desde}; null si no hay mas. */
    public LocalDateTime siguienteDesde(Programacion p, LocalDateTime desde) {
        LocalDateTime inicio = p.getFechaInicio().atTime(p.getHora());
        LocalDateTime candidata = primeraCandidata(p, inicio, desde);
        for (int i = 0; candidata != null && i < MAXIMO_INTENTOS; i++) {
            if (!candidata.isBefore(desde) && !candidata.isBefore(inicio) && enVentana(p, candidata.toLocalTime())) {
                return candidata;
            }
            candidata = siguienteCandidata(p, inicio, candidata);
        }
        return null;
    }

    /** Primera ocurrencia estrictamente posterior a {@code momento} (minuto siguiente en adelante). */
    public LocalDateTime siguienteDespuesDe(Programacion p, LocalDateTime momento) {
        return siguienteDesde(p, momento.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1));
    }

    /** Las proximas {@code cantidad} ocurrencias desde {@code desde}. */
    public List<LocalDateTime> proximas(Programacion p, LocalDateTime desde, int cantidad) {
        List<LocalDateTime> resultado = new ArrayList<>();
        LocalDateTime siguiente = siguienteDesde(p, desde);
        while (siguiente != null && resultado.size() < cantidad) {
            resultado.add(siguiente);
            siguiente = siguienteDespuesDe(p, siguiente);
        }
        return resultado;
    }

    /** Ocurrencia de la serie cercana a {@code desde} (sin pasarse de mas), para no recorrer desde el inicio. */
    private static LocalDateTime primeraCandidata(Programacion p, LocalDateTime inicio, LocalDateTime desde) {
        int n = p.getIntervalo();
        if (!desde.isAfter(inicio)) {
            return p.getFrecuencia() == Frecuencia.SEMANAL
                    ? primerDiaSemanal(p, inicio.toLocalDate())
                    : inicio;
        }
        return switch (p.getFrecuencia()) {
            case UNA_VEZ -> inicio;
            case CADA_N_HORAS -> inicio.plusHours(n * (Duration.between(inicio, desde).toHours() / n));
            case DIARIA -> inicio.plusDays(n * (ChronoUnit.DAYS.between(inicio, desde) / n));
            case MENSUAL -> mensual(p, n * (ChronoUnit.MONTHS.between(inicio, desde) / n));
            case SEMANAL -> primerDiaSemanal(p, desde.toLocalDate());
        };
    }

    private static LocalDateTime siguienteCandidata(Programacion p, LocalDateTime inicio, LocalDateTime actual) {
        int n = p.getIntervalo();
        return switch (p.getFrecuencia()) {
            case UNA_VEZ -> null;
            case CADA_N_HORAS -> actual.plusHours(n);
            case DIARIA -> actual.plusDays(n);
            case MENSUAL -> mensual(p, ChronoUnit.MONTHS.between(
                    inicio.toLocalDate().withDayOfMonth(1), actual.toLocalDate().withDayOfMonth(1)) + n);
            case SEMANAL -> primerDiaSemanal(p, actual.toLocalDate().plusDays(1));
        };
    }

    /** Mes numero {@code meses} desde el inicio, el mismo dia (o el ultimo del mes si no existe). */
    private static LocalDateTime mensual(Programacion p, long meses) {
        return p.getFechaInicio().plusMonths(meses).atTime(p.getHora());
    }

    /** Primer dia elegido, en una semana que corresponda, a partir de {@code fecha}. */
    private static LocalDateTime primerDiaSemanal(Programacion p, LocalDate fecha) {
        if (p.getDiasSemana().isEmpty()) {
            return null;
        }
        LocalDate lunesInicio = p.getFechaInicio().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 7 * p.getIntervalo() + 7; i++) {
            LocalDate d = fecha.plusDays(i);
            long semanas = ChronoUnit.WEEKS.between(lunesInicio, d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            if (semanas % p.getIntervalo() == 0 && contiene(p, d.getDayOfWeek())) {
                return d.atTime(p.getHora());
            }
        }
        return null;
    }

    private static boolean contiene(Programacion p, DayOfWeek dia) {
        return p.getDiasSemana().stream().map(DiaSemana::aDayOfWeek).anyMatch(dia::equals);
    }

    /** Sin ventana, cualquier hora sirve. La ventana puede cruzar la medianoche; el fin no esta incluido. */
    static boolean enVentana(Programacion p, LocalTime t) {
        LocalTime inicio = p.getVentanaInicio();
        LocalTime fin = p.getVentanaFin();
        if (inicio == null || fin == null) {
            return true;
        }
        return inicio.isBefore(fin)
                ? !t.isBefore(inicio) && t.isBefore(fin)
                : !t.isBefore(inicio) || t.isBefore(fin);
    }
}
