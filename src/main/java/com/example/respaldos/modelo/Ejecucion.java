package com.example.respaldos.modelo;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Evidencia de una ejecucion. Copia el tipo de respaldo, el script y el destino
 * para que el historial no cambie si despues se modifica la estrategia.
 */
@Entity
@Table(name = "ejecucion")
public class Ejecucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estrategia_id", nullable = false)
    private Estrategia estrategia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "script_id", nullable = false)
    private ScriptRman script;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_datos_id", nullable = false)
    private BaseDatos baseDatos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenEjecucion origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEjecucion estado = EstadoEjecucion.EN_CURSO;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_respaldo", nullable = false, length = 40)
    private TipoRespaldo tipoRespaldo;

    @Lob
    @Column(name = "script_ejecutado", nullable = false)
    private String scriptEjecutado;

    @Column(name = "ruta_destino", nullable = false, length = 500)
    private String rutaDestino;

    @Column(name = "fecha_programada")
    private LocalDateTime fechaProgramada;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "duracion_segundos")
    private Long duracionSegundos;

    @Column(name = "codigo_salida")
    private Integer codigoSalida;

    @Lob
    @Column(name = "salida_rman")
    private String salidaRman;

    @Column(name = "mensaje_error", length = 4000)
    private String mensajeError;

    @Column(name = "tamano_total_bytes")
    private Long tamanoTotalBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVerificacion verificacion = EstadoVerificacion.PENDIENTE;

    @Lob
    @Column(name = "salida_verificacion")
    private String salidaVerificacion;

    @OneToMany(mappedBy = "ejecucion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvidenciaArchivo> archivos = new ArrayList<>();

    public void agregarArchivo(EvidenciaArchivo archivo) {
        archivo.setEjecucion(this);
        archivos.add(archivo);
    }

    public Long getId() { return id; }

    public Estrategia getEstrategia() { return estrategia; }
    public void setEstrategia(Estrategia estrategia) { this.estrategia = estrategia; }

    public ScriptRman getScript() { return script; }
    public void setScript(ScriptRman script) { this.script = script; }

    public BaseDatos getBaseDatos() { return baseDatos; }
    public void setBaseDatos(BaseDatos baseDatos) { this.baseDatos = baseDatos; }

    public OrigenEjecucion getOrigen() { return origen; }
    public void setOrigen(OrigenEjecucion origen) { this.origen = origen; }

    public EstadoEjecucion getEstado() { return estado; }
    public void setEstado(EstadoEjecucion estado) { this.estado = estado; }

    public TipoRespaldo getTipoRespaldo() { return tipoRespaldo; }
    public void setTipoRespaldo(TipoRespaldo tipoRespaldo) { this.tipoRespaldo = tipoRespaldo; }

    public String getScriptEjecutado() { return scriptEjecutado; }
    public void setScriptEjecutado(String scriptEjecutado) { this.scriptEjecutado = scriptEjecutado; }

    public String getRutaDestino() { return rutaDestino; }
    public void setRutaDestino(String rutaDestino) { this.rutaDestino = rutaDestino; }

    public LocalDateTime getFechaProgramada() { return fechaProgramada; }
    public void setFechaProgramada(LocalDateTime fechaProgramada) { this.fechaProgramada = fechaProgramada; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public Long getDuracionSegundos() { return duracionSegundos; }
    public void setDuracionSegundos(Long duracionSegundos) { this.duracionSegundos = duracionSegundos; }

    public Integer getCodigoSalida() { return codigoSalida; }
    public void setCodigoSalida(Integer codigoSalida) { this.codigoSalida = codigoSalida; }

    public String getSalidaRman() { return salidaRman; }
    public void setSalidaRman(String salidaRman) { this.salidaRman = salidaRman; }

    public String getMensajeError() { return mensajeError; }
    public void setMensajeError(String mensajeError) { this.mensajeError = mensajeError; }

    public Long getTamanoTotalBytes() { return tamanoTotalBytes; }
    public void setTamanoTotalBytes(Long tamanoTotalBytes) { this.tamanoTotalBytes = tamanoTotalBytes; }

    public EstadoVerificacion getVerificacion() { return verificacion; }
    public void setVerificacion(EstadoVerificacion verificacion) { this.verificacion = verificacion; }

    public String getSalidaVerificacion() { return salidaVerificacion; }
    public void setSalidaVerificacion(String salidaVerificacion) { this.salidaVerificacion = salidaVerificacion; }

    public List<EvidenciaArchivo> getArchivos() { return archivos; }
}
