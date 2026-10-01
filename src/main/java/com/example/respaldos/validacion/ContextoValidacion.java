package com.example.respaldos.validacion;

import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.InfoBaseDatos;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.infraestructura.EspacioDisco;
import com.example.respaldos.modelo.Estrategia;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Todo lo que las reglas necesitan saber, ya leido del ambiente. Separarlo permite
 * probar las reglas sin Docker ni Oracle.
 *
 * @param info                        null si el contenedor esta detenido o no se pudo leer el catalogo
 * @param errorCatalogo               motivo por el que no se pudo leer el catalogo (null si se leyo)
 * @param espacioDestino              df de la ruta de destino; null si no se pudo obtener
 * @param errorDestino                motivo por el que no se pudo leer el destino (p. ej. no existe)
 * @param espacioDatos                df del directorio de los datafiles, para comparar con el destino
 * @param hayNivel0Exitoso            la app registro un nivel 0 exitoso para esta base
 * @param otraEstrategiaConArchivelog otra estrategia activa de la base ya respalda archived redo logs
 */
public record ContextoValidacion(
        Estrategia estrategia,
        boolean contenedorActivo,
        InfoBaseDatos info,
        String errorCatalogo,
        List<TablespaceInfo> tablespaces,
        List<DatafileInfo> datafiles,
        EspacioDisco espacioDestino,
        String errorDestino,
        EspacioDisco espacioDatos,
        boolean hayNivel0Exitoso,
        boolean otraEstrategiaConArchivelog,
        LocalDateTime ahora) {
}
