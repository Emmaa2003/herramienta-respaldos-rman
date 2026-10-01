package com.example.respaldos.modelo;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** CUANDO respaldar: programacion de una estrategia. */
@Entity
@Table(name = "programacion")
public class Programacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estrategia_id", nullable = false, unique = true)
    private Estrategia estrategia;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Convert(converter = HoraConverter.class)
    @Column(nullable = false, length = 5)
    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frecuencia frecuencia;

    /** Dias para frecuencia SEMANAL, p. ej. "LUN,MIE,VIE". */
    @Column(name = "dias_semana", length = 30)
    private String diasSemana;

    /** Cada cuantas horas (CADA_N_HORAS) o cada cuantos dias/semanas/meses. */
    @Column(nullable = false)
    private int intervalo = 1;

    @Convert(converter = HoraConverter.class)
    @Column(name = "ventana_inicio", length = 5)
    private LocalTime ventanaInicio;

    @Convert(converter = HoraConverter.class)
    @Column(name = "ventana_fin", length = 5)
    private LocalTime ventanaFin;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "proxima_ejecucion")
    private LocalDateTime proximaEjecucion;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion", nullable = false)
    private LocalDateTime fechaModificacion;

    public Long getId() { return id; }

    public Estrategia getEstrategia() { return estrategia; }
    void setEstrategia(Estrategia estrategia) { this.estrategia = estrategia; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }

    public Frecuencia getFrecuencia() { return frecuencia; }
    public void setFrecuencia(Frecuencia frecuencia) { this.frecuencia = frecuencia; }

    public String getDiasSemana() { return diasSemana; }
    public void setDiasSemana(String diasSemana) { this.diasSemana = diasSemana; }

    public int getIntervalo() { return intervalo; }
    public void setIntervalo(int intervalo) { this.intervalo = intervalo; }

    public LocalTime getVentanaInicio() { return ventanaInicio; }
    public void setVentanaInicio(LocalTime ventanaInicio) { this.ventanaInicio = ventanaInicio; }

    public LocalTime getVentanaFin() { return ventanaFin; }
    public void setVentanaFin(LocalTime ventanaFin) { this.ventanaFin = ventanaFin; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public LocalDateTime getProximaEjecucion() { return proximaEjecucion; }
    public void setProximaEjecucion(LocalDateTime proximaEjecucion) { this.proximaEjecucion = proximaEjecucion; }

    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
}
