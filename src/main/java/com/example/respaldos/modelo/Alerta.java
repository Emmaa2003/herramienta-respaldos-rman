package com.example.respaldos.modelo;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Mensaje persistente del control preventivo (informativo, advertencia o recomendacion).
 * Puede referirse a una base, a una estrategia o a una ejecucion.
 */
@Entity
@Table(name = "alerta")
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMensaje tipo;

    /** Identificador de la condicion detectada, p. ej. NOARCHIVELOG o SIN_PROGRAMACION. */
    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_datos_id")
    private BaseDatos baseDatos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estrategia_id")
    private Estrategia estrategia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ejecucion_id")
    private Ejecucion ejecucion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAlerta estado = EstadoAlerta.ABIERTA;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_atencion")
    private LocalDateTime fechaAtencion;

    @Column(name = "atendida_por", length = 100)
    private String atendidaPor;

    @Column(length = 1000)
    private String comentario;

    protected Alerta() {
    }

    public Alerta(TipoMensaje tipo, String codigo, String mensaje) {
        this.tipo = tipo;
        this.codigo = codigo;
        this.mensaje = mensaje;
    }

    public Long getId() { return id; }

    public TipoMensaje getTipo() { return tipo; }

    public String getCodigo() { return codigo; }

    public String getMensaje() { return mensaje; }

    public BaseDatos getBaseDatos() { return baseDatos; }
    public void setBaseDatos(BaseDatos baseDatos) { this.baseDatos = baseDatos; }

    public Estrategia getEstrategia() { return estrategia; }
    public void setEstrategia(Estrategia estrategia) { this.estrategia = estrategia; }

    public Ejecucion getEjecucion() { return ejecucion; }
    public void setEjecucion(Ejecucion ejecucion) { this.ejecucion = ejecucion; }

    public EstadoAlerta getEstado() { return estado; }
    public void setEstado(EstadoAlerta estado) { this.estado = estado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }

    public LocalDateTime getFechaAtencion() { return fechaAtencion; }
    public void setFechaAtencion(LocalDateTime fechaAtencion) { this.fechaAtencion = fechaAtencion; }

    public String getAtendidaPor() { return atendidaPor; }
    public void setAtendidaPor(String atendidaPor) { this.atendidaPor = atendidaPor; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
}
