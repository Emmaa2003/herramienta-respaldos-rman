import { api } from '../api';
import type { Navegar } from '../App';
import { CajaError, Estado, fecha, nombreTipo, useCarga } from '../componentes/comun';

/** Vista general: alertas abiertas, programaciones y ultimas ejecuciones. */
export default function Panel({ navegar }: { navegar: Navegar }) {
  const resumen = useCarga(api.resumenAlertas);
  const programaciones = useCarga(api.programaciones);
  const ejecuciones = useCarga(() => api.ejecuciones());

  return (
    <>
      <div className="portada grunge">
        <h2>Panel</h2>
        <p>Control preventivo de respaldos: estrategia, validacion, script, aprobacion, ejecucion y evidencia.</p>
      </div>
      <div className="rejilla">
        {(['ADVERTENCIA', 'RECOMENDACION', 'INFORMATIVO'] as const).map((t) => (
          <div key={t} className="panel clic" onClick={() => navegar({ pagina: 'alertas' })}>
            <div className="tenue">{t === 'ADVERTENCIA' ? 'Advertencias' : t === 'RECOMENDACION' ? 'Recomendaciones' : 'Informativos'} abiertos</div>
            <div className="cifra">{resumen.datos?.[t] ?? '-'}</div>
          </div>
        ))}
      </div>
      <CajaError error={resumen.error} />

      <div className="panel">
        <h3>Programacion</h3>
        <CajaError error={programaciones.error} />
        <table>
          <thead><tr><th>Estrategia</th><th>Frecuencia</th><th>Proxima ejecucion</th><th>Estado</th><th>Detalle</th></tr></thead>
          <tbody>
            {programaciones.datos?.map((p) => (
              <tr key={p.estrategiaId} className="clic" onClick={() => navegar({ pagina: 'estrategia', id: p.estrategiaId })}>
                <td>{p.estrategia}</td>
                <td>{p.frecuencia}</td>
                <td>{fecha(p.proximaEjecucion)}</td>
                <td><Estado valor={p.estado} /></td>
                <td className="tenue">{p.detalle}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {programaciones.datos?.length === 0 && <p className="tenue">Ninguna estrategia tiene programacion.</p>}
      </div>

      <div className="panel">
        <h3>Ultimas ejecuciones</h3>
        <CajaError error={ejecuciones.error} />
        <table>
          <thead><tr><th>Inicio</th><th>Estrategia</th><th>Tipo</th><th>Resultado</th><th>Verificacion</th></tr></thead>
          <tbody>
            {ejecuciones.datos?.slice(0, 8).map((x) => (
              <tr key={x.id} className="clic" onClick={() => navegar({ pagina: 'ejecucion', id: x.id })}>
                <td>{fecha(x.fechaInicio)}</td>
                <td>{x.estrategia}</td>
                <td>{nombreTipo(x.tipoRespaldo)}</td>
                <td><Estado valor={x.estado} /></td>
                <td><Estado valor={x.verificacion} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
