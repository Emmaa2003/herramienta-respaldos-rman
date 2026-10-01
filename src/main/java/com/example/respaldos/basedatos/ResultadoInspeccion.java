package com.example.respaldos.basedatos;

import com.example.respaldos.comun.Mensaje;
import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.modelo.ModoArchivado;

import java.util.List;

/**
 * Lo que se encontro al inspeccionar una base. Si el contenedor no esta corriendo,
 * solo trae la base y el mensaje que lo explica (sin catalogo).
 */
public record ResultadoInspeccion(
        BaseDatosRespuesta base,
        boolean contenedorActivo,
        String nombreBase,
        String pdb,
        ModoArchivado modoArchivado,
        List<TablespaceInfo> tablespaces,
        List<DatafileInfo> datafiles,
        List<Mensaje> mensajes) {
}
