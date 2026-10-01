package com.example.respaldos.basedatos;

import com.example.respaldos.comun.Mensaje;
import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.InfoBaseDatos;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.ModoArchivado;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte lo encontrado en la base en mensajes para el administrador.
 * Solo informa, advierte o recomienda: no cambia nada en la base (en particular,
 * nunca cambia el modo de archivado).
 */
@Component
public class ReglasInspeccion {

    /**
     * @param archivelogEnEstrategia true si alguna estrategia activa de la base ya respalda
     *                               los archived redo logs
     */
    public List<Mensaje> evaluar(BaseDatos base, InfoBaseDatos info, List<TablespaceInfo> tablespaces,
                                 List<DatafileInfo> datafiles, boolean archivelogEnEstrategia) {
        List<Mensaje> mensajes = new ArrayList<>();

        if (base.getAmbiente() == Ambiente.PRODUCCION) {
            mensajes.add(Mensaje.advertencia("AMBIENTE_PRODUCCION",
                    "La base esta registrada como PRODUCCION. Los scripts RMAN deben probarse primero "
                            + "en un ambiente de desarrollo o pruebas."));
        }

        if (!base.getServicio().equalsIgnoreCase(info.pdb())) {
            mensajes.add(Mensaje.advertencia("SERVICIO_DISTINTO",
                    "El catalogo se leyo del servicio " + info.pdb() + ", pero la base registrada indica '"
                            + base.getServicio() + "'. Los tablespaces y datafiles mostrados pueden no "
                            + "corresponder a esta base."));
        }

        if (info.modoArchivado() == ModoArchivado.NOARCHIVELOG) {
            mensajes.add(Mensaje.advertencia("NOARCHIVELOG",
                    "La base de datos se encuentra en modo NOARCHIVELOG. Las posibilidades de recuperacion "
                            + "son mas limitadas. Revise la estrategia de respaldo y los requerimientos de "
                            + "recuperacion antes de continuar."));
            mensajes.add(Mensaje.informativo("NOARCHIVELOG_RESPALDO_CERRADO",
                    "En NOARCHIVELOG no hay archived redo logs: solo se puede recuperar hasta el ultimo "
                            + "respaldo, y RMAN solo puede respaldar la base cerrada (en estado MOUNT). "
                            + "Con la base abierta el respaldo falla."));
        } else {
            mensajes.add(Mensaje.informativo("MODO_ARCHIVELOG",
                    "La base esta en modo ARCHIVELOG: admite respaldos con la base abierta y recuperacion "
                            + "hasta un punto en el tiempo, siempre que se conserven los archived redo logs."));
            if (archivelogEnEstrategia) {
                mensajes.add(Mensaje.informativo("ARCHIVELOG_CUBIERTO",
                        "Al menos una estrategia activa ya incluye el respaldo de los archived redo logs."));
            } else {
                mensajes.add(Mensaje.recomendacion("INCLUIR_ARCHIVELOG",
                        "La base de datos se encuentra en modo ARCHIVELOG. Considere incorporar el respaldo "
                                + "periodico de los archived redo logs dentro de la estrategia para mejorar "
                                + "las posibilidades de recuperacion."));
            }
        }

        for (TablespaceInfo t : tablespaces) {
            if ("OFFLINE".equals(t.estado())) {
                mensajes.add(Mensaje.advertencia("TABLESPACE_OFFLINE",
                        "El tablespace " + t.nombreRman() + " esta OFFLINE: RMAN no podra respaldarlo "
                                + "mientras siga en ese estado."));
            } else if ("READ ONLY".equals(t.estado())) {
                mensajes.add(Mensaje.informativo("TABLESPACE_SOLO_LECTURA",
                        "El tablespace " + t.nombreRman() + " es de solo lectura: sus datos no cambian, "
                                + "por lo que no necesita respaldarse con la misma frecuencia."));
            }
        }

        long temporales = tablespaces.stream().filter(t -> "TEMPORARY".equals(t.contenido())).count();
        long megas = datafiles.stream().mapToLong(DatafileInfo::bytes).sum() / (1024 * 1024);
        mensajes.add(Mensaje.informativo("CATALOGO",
                "Se encontraron " + tablespaces.size() + " tablespaces (" + temporales
                        + " temporales, que RMAN no respalda) y " + datafiles.size() + " datafiles con "
                        + megas + " MB en total en " + info.pdb() + "."));

        return mensajes;
    }
}
