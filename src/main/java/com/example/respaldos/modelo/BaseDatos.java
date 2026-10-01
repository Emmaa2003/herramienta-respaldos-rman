package com.example.respaldos.modelo;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Base de datos Oracle registrada para ser respaldada. */
@Entity
@Table(name = "base_datos")
public class BaseDatos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Contenedor Docker donde se ejecuta RMAN (rman target /). */
    @Column(nullable = false, length = 100)
    private String contenedor;

    @Column(nullable = false, length = 100)
    private String servicio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Ambiente ambiente;

    /** Null mientras no se haya inspeccionado la base. */
    @Enumerated(EnumType.STRING)
    @Column(name = "modo_archivado", length = 20)
    private ModoArchivado modoArchivado;

    @Column(name = "fecha_inspeccion")
    private LocalDateTime fechaInspeccion;

    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    public Long getId() { return id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getContenedor() { return contenedor; }
    public void setContenedor(String contenedor) { this.contenedor = contenedor; }

    public String getServicio() { return servicio; }
    public void setServicio(String servicio) { this.servicio = servicio; }

    public Ambiente getAmbiente() { return ambiente; }
    public void setAmbiente(Ambiente ambiente) { this.ambiente = ambiente; }

    public ModoArchivado getModoArchivado() { return modoArchivado; }
    public void setModoArchivado(ModoArchivado modoArchivado) { this.modoArchivado = modoArchivado; }

    public LocalDateTime getFechaInspeccion() { return fechaInspeccion; }
    public void setFechaInspeccion(LocalDateTime fechaInspeccion) { this.fechaInspeccion = fechaInspeccion; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
