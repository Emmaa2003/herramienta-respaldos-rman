package com.example.respaldos.modelo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Archivo generado por RMAN en una ejecucion y si se comprobo que existe. */
@Entity
@Table(name = "evidencia_archivo")
public class EvidenciaArchivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ejecucion_id", nullable = false)
    private Ejecucion ejecucion;

    @Column(nullable = false, length = 1000)
    private String ruta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEvidencia tipo;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(nullable = false)
    private boolean existe;

    @Column(name = "fecha_verificacion")
    private LocalDateTime fechaVerificacion;

    protected EvidenciaArchivo() {
    }

    public EvidenciaArchivo(String ruta, TipoEvidencia tipo) {
        this.ruta = ruta;
        this.tipo = tipo;
    }

    public Long getId() { return id; }

    public Ejecucion getEjecucion() { return ejecucion; }
    void setEjecucion(Ejecucion ejecucion) { this.ejecucion = ejecucion; }

    public String getRuta() { return ruta; }

    public TipoEvidencia getTipo() { return tipo; }

    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }

    public boolean isExiste() { return existe; }
    public void setExiste(boolean existe) { this.existe = existe; }

    public LocalDateTime getFechaVerificacion() { return fechaVerificacion; }
    public void setFechaVerificacion(LocalDateTime fechaVerificacion) { this.fechaVerificacion = fechaVerificacion; }
}
