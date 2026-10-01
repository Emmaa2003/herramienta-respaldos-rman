package com.example.respaldos.validacion;

import com.example.respaldos.comun.NoEncontradoException;
import com.example.respaldos.infraestructura.AmbienteException;
import com.example.respaldos.infraestructura.CatalogoOracle;
import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.InfoBaseDatos;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.infraestructura.ClienteContenedor;
import com.example.respaldos.infraestructura.EspacioDisco;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.EstadoEjecucion;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import com.example.respaldos.repositorio.EjecucionRepository;
import com.example.respaldos.repositorio.EstrategiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Valida una estrategia contra la base real (regla 2: validar antes de generar o ejecutar).
 * Reune la informacion del ambiente y aplica {@link ReglasValidacion}. Solo lee: no
 * modifica la estrategia, la base registrada ni Oracle.
 */
@Service
@Transactional(readOnly = true)
public class ValidadorEstrategia {

    private final EstrategiaRepository estrategias;
    private final EjecucionRepository ejecuciones;
    private final ClienteContenedor cliente;
    private final CatalogoOracle catalogo;
    private final ReglasValidacion reglas;

    public ValidadorEstrategia(EstrategiaRepository estrategias, EjecucionRepository ejecuciones,
                               ClienteContenedor cliente, CatalogoOracle catalogo, ReglasValidacion reglas) {
        this.estrategias = estrategias;
        this.ejecuciones = ejecuciones;
        this.cliente = cliente;
        this.catalogo = catalogo;
        this.reglas = reglas;
    }

    public ResultadoValidacion validar(Long estrategiaId) {
        Estrategia estrategia = estrategias.findById(estrategiaId)
                .orElseThrow(() -> new NoEncontradoException("No existe la estrategia con id " + estrategiaId + "."));
        return validar(estrategia);
    }

    public ResultadoValidacion validar(Estrategia estrategia) {
        List<Hallazgo> hallazgos = reglas.evaluar(contexto(estrategia));
        boolean valida = hallazgos.stream().noneMatch(Hallazgo::bloqueante);
        return new ResultadoValidacion(estrategia.getId(), valida, LocalDateTime.now(), hallazgos);
    }

    private ContextoValidacion contexto(Estrategia e) {
        BaseDatos base = e.getBaseDatos();
        String contenedor = base.getContenedor();
        boolean activo = cliente.contenedorEnEjecucion(contenedor);

        InfoBaseDatos info = null;
        String errorCatalogo = null;
        List<TablespaceInfo> tablespaces = List.of();
        List<DatafileInfo> datafiles = List.of();
        EspacioDisco destino = null;
        String errorDestino = null;
        EspacioDisco datos = null;

        if (activo) {
            try {
                info = catalogo.informacionBase();
                tablespaces = catalogo.tablespaces();
                datafiles = catalogo.datafiles();
            } catch (AmbienteException ex) {
                info = null;
                errorCatalogo = ex.getMessage();
            }
            try {
                destino = cliente.espacioDisco(contenedor, e.getRutaDestino());
            } catch (AmbienteException ex) {
                errorDestino = ex.getMessage();
            }
            if (!datafiles.isEmpty()) {
                try {
                    datos = cliente.espacioDisco(contenedor, datafiles.getFirst().ruta());
                } catch (AmbienteException ex) {
                    // Solo se usa para comparar discos; sin el dato se omite esa recomendacion.
                }
            }
        }

        boolean hayNivel0 = ejecuciones.existsByBaseDatosIdAndTipoRespaldoAndEstadoIn(base.getId(),
                TipoRespaldo.INCREMENTAL_NIVEL_0, List.of(EstadoEjecucion.EXITOSO, EstadoEjecucion.CON_ADVERTENCIAS));
        boolean otraConArchivelog = e.getId() == null
                ? estrategias.existeActivaConElemento(base.getId(), TipoElemento.ARCHIVELOG)
                : estrategias.existeOtraActivaConElemento(base.getId(), TipoElemento.ARCHIVELOG, e.getId());

        return new ContextoValidacion(e, activo, info, errorCatalogo, tablespaces, datafiles, destino,
                errorDestino, datos, hayNivel0, otraConArchivelog, LocalDateTime.now());
    }
}
