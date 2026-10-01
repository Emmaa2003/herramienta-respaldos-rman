package com.example.respaldos.basedatos;

import com.example.respaldos.comun.ConflictoException;
import com.example.respaldos.comun.Mensaje;
import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.comun.SolicitudInvalidaException;
import com.example.respaldos.infraestructura.CatalogoOracle;
import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.InfoBaseDatos;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.infraestructura.ClienteContenedor;
import com.example.respaldos.infraestructura.PropiedadesDocker;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.repositorio.BaseDatosRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Registro de bases de datos e inspeccion de su modo de archivado y catalogo. */
@Service
@Transactional
public class BaseDatosService {

    private final BaseDatosRepository bases;
    private final EstrategiaRepository estrategias;
    private final ClienteContenedor cliente;
    private final CatalogoOracle catalogo;
    private final ReglasInspeccion reglas;
    private final PropiedadesDocker propiedadesDocker;

    public BaseDatosService(BaseDatosRepository bases, EstrategiaRepository estrategias,
                            ClienteContenedor cliente, CatalogoOracle catalogo, ReglasInspeccion reglas,
                            PropiedadesDocker propiedadesDocker) {
        this.bases = bases;
        this.estrategias = estrategias;
        this.cliente = cliente;
        this.catalogo = catalogo;
        this.reglas = reglas;
        this.propiedadesDocker = propiedadesDocker;
    }

    @Transactional(readOnly = true)
    public List<BaseDatosRespuesta> listar() {
        return bases.findAll(Sort.by("nombre")).stream().map(BaseDatosRespuesta::de).toList();
    }

    @Transactional(readOnly = true)
    public BaseDatosRespuesta obtener(Long id) {
        return BaseDatosRespuesta.de(buscar(id));
    }

    public BaseDatosRespuesta crear(BaseDatosSolicitud solicitud) {
        if (bases.existsByNombreIgnoreCase(solicitud.nombre().trim())) {
            throw new ConflictoException("Ya existe una base de datos con el nombre '" + solicitud.nombre() + "'.");
        }
        BaseDatos base = new BaseDatos();
        copiar(solicitud, base);
        return BaseDatosRespuesta.de(bases.save(base));
    }

    public BaseDatosRespuesta actualizar(Long id, BaseDatosSolicitud solicitud) {
        BaseDatos base = buscar(id);
        if (bases.existsByNombreIgnoreCaseAndIdNot(solicitud.nombre().trim(), id)) {
            throw new ConflictoException("Ya existe una base de datos con el nombre '" + solicitud.nombre() + "'.");
        }
        boolean cambiaDestino = !base.getContenedor().equals(solicitud.contenedor().trim())
                || !base.getServicio().equalsIgnoreCase(solicitud.servicio().trim());
        copiar(solicitud, base);
        if (cambiaDestino) {
            // Lo inspeccionado ya no describe a la base nueva.
            base.setModoArchivado(null);
            base.setFechaInspeccion(null);
        }
        return BaseDatosRespuesta.de(base);
    }

    public void eliminar(Long id) {
        BaseDatos base = buscar(id);
        if (estrategias.existsByBaseDatosId(id)) {
            throw new ConflictoException("La base '" + base.getNombre()
                    + "' tiene estrategias asociadas. Elimine o reasigne las estrategias primero.");
        }
        bases.delete(base);
    }

    /**
     * Lee el modo de archivado y el catalogo, guarda el modo en la base registrada y
     * devuelve los mensajes. No modifica nada en Oracle.
     */
    public ResultadoInspeccion inspeccionar(Long id) {
        BaseDatos base = buscar(id);

        if (!cliente.contenedorEnEjecucion(base.getContenedor())) {
            return new ResultadoInspeccion(BaseDatosRespuesta.de(base), false, null, null,
                    base.getModoArchivado(), List.of(), List.of(),
                    List.of(Mensaje.advertencia("CONTENEDOR_DETENIDO",
                            "El contenedor '" + base.getContenedor() + "' no esta en ejecucion. No se pudo "
                                    + "inspeccionar la base y no se podran ejecutar respaldos.")));
        }

        InfoBaseDatos info = catalogo.informacionBase();
        List<TablespaceInfo> tablespaces = catalogo.tablespaces();
        List<DatafileInfo> datafiles = catalogo.datafiles();
        boolean archivelogEnEstrategia = estrategias.existeActivaConElemento(id, TipoElemento.ARCHIVELOG);

        base.setModoArchivado(info.modoArchivado());
        base.setFechaInspeccion(LocalDateTime.now());

        List<Mensaje> mensajes = reglas.evaluar(base, info, tablespaces, datafiles, archivelogEnEstrategia);
        return new ResultadoInspeccion(BaseDatosRespuesta.de(base), true, info.nombre(), info.pdb(),
                info.modoArchivado(), tablespaces, datafiles, mensajes);
    }

    private BaseDatos buscar(Long id) {
        return bases.findById(id)
                .orElseThrow(() -> new NoEncontradoException("No existe la base de datos con id " + id + "."));
    }

    private void copiar(BaseDatosSolicitud solicitud, BaseDatos base) {
        String contenedor = solicitud.contenedor().trim();
        if (!propiedadesDocker.contenedoresPermitidos().contains(contenedor)) {
            throw new SolicitudInvalidaException("El contenedor '" + contenedor + "' no esta permitido. "
                    + "Permitidos: " + propiedadesDocker.contenedoresPermitidos());
        }
        base.setNombre(solicitud.nombre().trim());
        base.setDescripcion(solicitud.descripcion());
        base.setContenedor(contenedor);
        base.setServicio(solicitud.servicio().trim());
        base.setAmbiente(solicitud.ambiente());
    }
}
