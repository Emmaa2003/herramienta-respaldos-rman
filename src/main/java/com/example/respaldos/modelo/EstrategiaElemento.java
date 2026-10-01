package com.example.respaldos.modelo;

import jakarta.persistence.*;

/**
 * Un elemento del QUE respaldar. nombreObjeto solo aplica a TABLESPACE (nombre)
 * y DATAFILE (numero o ruta); para los demas tipos es null.
 */
@Entity
@Table(name = "estrategia_elemento")
public class EstrategiaElemento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estrategia_id", nullable = false)
    private Estrategia estrategia;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_elemento", nullable = false, length = 20)
    private TipoElemento tipoElemento;

    @Column(name = "nombre_objeto", length = 500)
    private String nombreObjeto;

    protected EstrategiaElemento() {
    }

    public EstrategiaElemento(TipoElemento tipoElemento, String nombreObjeto) {
        this.tipoElemento = tipoElemento;
        this.nombreObjeto = nombreObjeto;
    }

    public Long getId() { return id; }

    public Estrategia getEstrategia() { return estrategia; }
    void setEstrategia(Estrategia estrategia) { this.estrategia = estrategia; }

    public TipoElemento getTipoElemento() { return tipoElemento; }

    public String getNombreObjeto() { return nombreObjeto; }
}
