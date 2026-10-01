package com.example.respaldos.modelo;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de respaldo: informacion general, COMO y destino.
 * El QUE son los {@link EstrategiaElemento} y el CUANDO es la {@link Programacion}.
 */
@Entity
@Table(name = "estrategia")
public class Estrategia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_datos_id", nullable = false)
    private BaseDatos baseDatos;

    @Column(nullable = false, length = 100)
    private String responsable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridad prioridad;

    @Column(nullable = false)
    private boolean activa;

    // --- COMO ---

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_respaldo", nullable = false, length = 40)
    private TipoRespaldo tipoRespaldo;

    @Column(nullable = false)
    private boolean comprimido;

    @Column(name = "dias_retencion")
    private Integer diasRetencion;

    // --- Destino ---

    @Column(name = "ruta_destino", nullable = false, length = 500)
    private String rutaDestino;

    @Column(nullable = false, length = 20)
    private String dispositivo = "DISK";

    // --- QUE / CUANDO ---

    @OneToMany(mappedBy = "estrategia", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstrategiaElemento> elementos = new ArrayList<>();

    @OneToOne(mappedBy = "estrategia", cascade = CascadeType.ALL, orphanRemoval = true)
    private Programacion programacion;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion", nullable = false)
    private LocalDateTime fechaModificacion;

    public void agregarElemento(EstrategiaElemento elemento) {
        elemento.setEstrategia(this);
        elementos.add(elemento);
    }

    public void quitarElemento(EstrategiaElemento elemento) {
        elementos.remove(elemento);
        elemento.setEstrategia(null);
    }

    /**
     * Deja exactamente los elementos indicados, conservando los que ya existian.
     * No se vacia y vuelve a llenar la lista porque Hibernate inserta antes de borrar
     * y el mismo elemento chocaria con la restriccion unica.
     */
    public void reemplazarElementos(List<EstrategiaElemento> nuevos) {
        elementos.removeIf(actual -> nuevos.stream().noneMatch(actual::mismoObjeto));
        for (EstrategiaElemento nuevo : nuevos) {
            if (elementos.stream().noneMatch(nuevo::mismoObjeto)) {
                agregarElemento(nuevo);
            }
        }
    }

    public void asignarProgramacion(Programacion nueva) {
        if (nueva != null) {
            nueva.setEstrategia(this);
        }
        this.programacion = nueva;
    }

    public Long getId() { return id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BaseDatos getBaseDatos() { return baseDatos; }
    public void setBaseDatos(BaseDatos baseDatos) { this.baseDatos = baseDatos; }

    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }

    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public TipoRespaldo getTipoRespaldo() { return tipoRespaldo; }
    public void setTipoRespaldo(TipoRespaldo tipoRespaldo) { this.tipoRespaldo = tipoRespaldo; }

    public boolean isComprimido() { return comprimido; }
    public void setComprimido(boolean comprimido) { this.comprimido = comprimido; }

    public Integer getDiasRetencion() { return diasRetencion; }
    public void setDiasRetencion(Integer diasRetencion) { this.diasRetencion = diasRetencion; }

    public String getRutaDestino() { return rutaDestino; }
    public void setRutaDestino(String rutaDestino) { this.rutaDestino = rutaDestino; }

    public String getDispositivo() { return dispositivo; }
    public void setDispositivo(String dispositivo) { this.dispositivo = dispositivo; }

    public List<EstrategiaElemento> getElementos() { return elementos; }

    public Programacion getProgramacion() { return programacion; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }

    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
}
