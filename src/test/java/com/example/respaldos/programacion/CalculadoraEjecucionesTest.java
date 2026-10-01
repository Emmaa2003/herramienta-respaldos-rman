package com.example.respaldos.programacion;

import com.example.respaldos.modelo.DiaSemana;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Programacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** 2026-10-01 es jueves. */
class CalculadoraEjecucionesTest {

    private final CalculadoraEjecuciones calc = new CalculadoraEjecuciones();

    @Test
    void diariaDevuelveLaHoraDelDiaOElSiguiente() {
        Programacion p = programacion(Frecuencia.DIARIA, "2026-10-01", "23:00");

        assertThat(calc.siguienteDesde(p, t("2026-09-01 00:00"))).isEqualTo(t("2026-10-01 23:00"));
        assertThat(calc.siguienteDesde(p, t("2026-10-05 12:00"))).isEqualTo(t("2026-10-05 23:00"));
        assertThat(calc.siguienteDesde(p, t("2026-10-05 23:00"))).isEqualTo(t("2026-10-05 23:00"));
        assertThat(calc.siguienteDespuesDe(p, t("2026-10-05 23:00"))).isEqualTo(t("2026-10-06 23:00"));
    }

    @Test
    void diariaCadaDosDias() {
        Programacion p = programacion(Frecuencia.DIARIA, "2026-10-01", "23:00");
        p.setIntervalo(2);

        assertThat(calc.proximas(p, t("2026-10-02 00:00"), 3))
                .containsExactly(t("2026-10-03 23:00"), t("2026-10-05 23:00"), t("2026-10-07 23:00"));
    }

    @Test
    void unaVezSoloSiTodaviaNoPaso() {
        Programacion p = programacion(Frecuencia.UNA_VEZ, "2026-10-01", "23:00");

        assertThat(calc.proximas(p, t("2026-10-01 08:00"), 5)).containsExactly(t("2026-10-01 23:00"));
        assertThat(calc.siguienteDesde(p, t("2026-10-02 00:00"))).isNull();
    }

    @Test
    void cadaNHoras() {
        Programacion p = programacion(Frecuencia.CADA_N_HORAS, "2026-10-01", "00:00");
        p.setIntervalo(6);

        assertThat(calc.proximas(p, t("2026-10-02 07:30"), 3))
                .containsExactly(t("2026-10-02 12:00"), t("2026-10-02 18:00"), t("2026-10-03 00:00"));
    }

    @Test
    void cadaNHorasSoloDentroDeUnaVentanaQueCruzaMedianoche() {
        Programacion p = programacion(Frecuencia.CADA_N_HORAS, "2026-10-01", "00:00");
        p.setIntervalo(2);
        p.setVentanaInicio(LocalTime.of(22, 0));
        p.setVentanaFin(LocalTime.of(5, 0));

        assertThat(calc.proximas(p, t("2026-10-01 05:00"), 5)).containsExactly(
                t("2026-10-01 22:00"), t("2026-10-02 00:00"), t("2026-10-02 02:00"),
                t("2026-10-02 04:00"), t("2026-10-02 22:00"));
    }

    @Test
    void semanalLosDiasElegidos() {
        Programacion p = programacion(Frecuencia.SEMANAL, "2026-10-01", "23:00");
        p.setDiasSemana(EnumSet.of(DiaSemana.LUN, DiaSemana.VIE));

        assertThat(calc.proximas(p, t("2026-09-01 00:00"), 4)).containsExactly(
                t("2026-10-02 23:00"), t("2026-10-05 23:00"), t("2026-10-09 23:00"), t("2026-10-12 23:00"));
    }

    @Test
    void semanalCadaDosSemanasCuentaDesdeLaSemanaDeInicio() {
        Programacion p = programacion(Frecuencia.SEMANAL, "2026-10-01", "23:00");
        p.setDiasSemana(EnumSet.of(DiaSemana.LUN, DiaSemana.VIE));
        p.setIntervalo(2);

        // Semana del 28-sep (inicio): solo queda el viernes 2; la del 5-oct se salta.
        assertThat(calc.proximas(p, t("2026-10-01 00:00"), 3)).containsExactly(
                t("2026-10-02 23:00"), t("2026-10-12 23:00"), t("2026-10-16 23:00"));
    }

    @Test
    void mensualUsaElUltimoDiaCuandoElMesEsMasCorto() {
        Programacion p = programacion(Frecuencia.MENSUAL, "2026-01-31", "08:00");

        assertThat(calc.proximas(p, t("2026-01-01 00:00"), 4)).containsExactly(
                t("2026-01-31 08:00"), t("2026-02-28 08:00"), t("2026-03-31 08:00"), t("2026-04-30 08:00"));
        assertThat(calc.siguienteDesde(p, t("2026-05-15 00:00"))).isEqualTo(t("2026-05-31 08:00"));
    }

    @Test
    void horaFueraDeLaVentanaNuncaSeEjecuta() {
        Programacion p = programacion(Frecuencia.DIARIA, "2026-10-01", "12:00");
        p.setVentanaInicio(LocalTime.of(22, 0));
        p.setVentanaFin(LocalTime.of(5, 0));

        assertThat(calc.siguienteDesde(p, t("2026-10-01 00:00"))).isNull();
    }

    @Test
    void ventana() {
        Programacion p = programacion(Frecuencia.DIARIA, "2026-10-01", "00:00");
        p.setVentanaInicio(LocalTime.of(22, 0));
        p.setVentanaFin(LocalTime.of(5, 0));

        assertThat(CalculadoraEjecuciones.enVentana(p, LocalTime.of(23, 0))).isTrue();
        assertThat(CalculadoraEjecuciones.enVentana(p, LocalTime.of(4, 59))).isTrue();
        assertThat(CalculadoraEjecuciones.enVentana(p, LocalTime.of(5, 0))).isFalse();
        assertThat(CalculadoraEjecuciones.enVentana(p, LocalTime.of(21, 59))).isFalse();
    }

    private static Programacion programacion(Frecuencia frecuencia, String fecha, String hora) {
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.parse(fecha));
        p.setHora(LocalTime.parse(hora));
        p.setFrecuencia(frecuencia);
        return p;
    }

    private static LocalDateTime t(String texto) {
        return LocalDateTime.parse(texto.replace(' ', 'T'));
    }
}
