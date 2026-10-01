package com.example.respaldos.modelo;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Script RMAN generado para una estrategia.
 * hashConfiguracion es la huella de la estrategia al generarlo: si la estrategia
 * cambia, deja de coincidir y la aprobacion ya no es valida.
 */
@Entity
@Table(name = "script_rman")
public class ScriptRman {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estrategia_id", nullable = false)
    private Estrategia estrategia;

    @Column(nullable = false)
    private int version;

    @Lob
    @Column(nullable = false)
    private String contenido;

    @Column(name = "hash_contenido", nullable = false, length = 64)
    private String hashContenido;

    @Column(name = "hash_configuracion", nullable = false, length = 64)
    private String hashConfiguracion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoScript estado = EstadoScript.GENERADO;

    @CreationTimestamp
    @Column(name = "fecha_generacion", nullable = false, updatable = false)
    private LocalDateTime fechaGeneracion;

    @Column(name = "aprobado_por", length = 100)
    private String aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    public Long getId() { return id; }

    public Estrategia getEstrategia() { return estrategia; }
    public void setEstrategia(Estrategia estrategia) { this.estrategia = estrategia; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public String getHashContenido() { return hashContenido; }
    public void setHashContenido(String hashContenido) { this.hashContenido = hashContenido; }

    public String getHashConfiguracion() { return hashConfiguracion; }
    public void setHashConfiguracion(String hashConfiguracion) { this.hashConfiguracion = hashConfiguracion; }

    public EstadoScript getEstado() { return estado; }
    public void setEstado(EstadoScript estado) { this.estado = estado; }

    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }

    public String getAprobadoPor() { return aprobadoPor; }
    public void setAprobadoPor(String aprobadoPor) { this.aprobadoPor = aprobadoPor; }

    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDateTime fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }
}
