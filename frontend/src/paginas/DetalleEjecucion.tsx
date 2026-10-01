import { useEffect } from 'react';
import { api } from '../api';
import type { Navegar } from '../App';
import { CajaError, Estado, fecha, nombreTipo, tamano, useCarga } from '../componentes/comun';

/** Evidencia completa de una ejecucion. Mientras esta EN_CURSO se actualiza sola. */
export default function DetalleEjecucion({ id, navegar }: { id: number; navegar: Navegar }) {
  const ejecucion = useCarga(() => api.ejecucion(id), [id]);
  const x = ejecucion.datos;

  useEffect(() => {
    if (x?.estado !== 'EN_CURSO') return;
    const t = setTimeout(ejecucion.recargar, 4000);
    return () => clearTimeout(t);
  }, [x, ejecucion.recargar]);

  if (!x) return <CajaError error={ejecucion.error} />;

  return (
    <>
      <h2>Ejecucion {x.id} <Estado valor={x.estado} /></h2>
      {x.estado === 'EN_CURSO' && <p className="tenue">RMAN se esta ejecutando; esta pagina se actualiza sola.</p>}
      {x.mensajeError && <div className="error">{x.mensajeError}</div>}

      <div className="panel rejilla">
        <div><div className="tenue">Estrategia</div>
          <a href="#" onClick={(ev) => { ev.preventDefault(); navegar({ pagina: 'estrategia', id: x.estrategiaId }); }}>{x.estrategia}</a>
          <div className="tenue">{x.baseDatos}</div></div>
        <div><div className="tenue">Tipo / origen</div>{nombreTipo(x.tipoRespaldo)} - {x.origen}
          {x.fechaProgramada && <div className="tenue">Programada para {fecha(x.fechaProgramada)}</div>}</div>
        <div><div className="tenue">Inicio / fin</div>{fecha(x.fechaInicio)} - {fecha(x.fechaFin)}
          <div className="tenue">{x.duracionSegundos != null ? x.duracionSegundos + ' s' : ''}</div></div>
        <div><div className="tenue">Script / codigo de salida</div>version {x.scriptVersion} - {x.codigoSalida ?? '-'}</div>
        <div><div className="tenue">Destino / tamano</div>{x.rutaDestino}<div>{tamano(x.tamanoTotalBytes)}</div></div>
        <div><div className="tenue">Verificacion</div><Estado valor={x.verificacion} /></div>
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Archivos generados</h3>
        <table>
          <thead><tr><th>Ruta</th><th>Tipo</th><th>Tamano</th><th>Existe</th></tr></thead>
          <tbody>
            {x.archivos.map((a) => (
              <tr key={a.ruta}><td><code>{a.ruta}</code></td><td>{a.tipo}</td><td>{tamano(a.tamanoBytes)}</td>
                <td><Estado valor={a.existe ? 'VERIFICADO' : 'FALLIDA'} /></td></tr>
            ))}
          </tbody>
        </table>
        {x.archivos.length === 0 && <p className="tenue">No se registraron archivos.</p>}
      </div>

      <div className="panel">
        <details open><summary>Script ejecutado</summary><pre>{x.scriptEjecutado}</pre></details>
        {x.salidaRman && <details><summary>Salida de RMAN</summary><pre>{x.salidaRman}</pre></details>}
        {x.salidaVerificacion && <details><summary>Verificacion (CROSSCHECK y RESTORE VALIDATE)</summary><pre>{x.salidaVerificacion}</pre></details>}
      </div>
    </>
  );
}
