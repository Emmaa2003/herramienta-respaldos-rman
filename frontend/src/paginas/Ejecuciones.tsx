import { api } from '../api';
import type { Navegar } from '../App';
import { CajaError, Estado, nombreTipo, tamano, useCarga } from '../componentes/comun';

/** Historial de ejecuciones (fecha, estrategia, tipo, inicio, fin, resultado). */
export default function Ejecuciones({ navegar }: { navegar: Navegar }) {
  const ejecuciones = useCarga(() => api.ejecuciones());

  return (
    <>
      <div className="fila" style={{ justifyContent: 'space-between' }}>
        <h2>Historial de ejecuciones</h2>
        <button onClick={ejecuciones.recargar}>Actualizar</button>
      </div>
      <div className="panel">
        <table>
          <thead>
            <tr><th>Fecha</th><th>Estrategia</th><th>Tipo</th><th>Origen</th><th>Inicio</th><th>Fin</th><th>Tamano</th><th>Resultado</th><th>Verificacion</th></tr>
          </thead>
          <tbody>
            {ejecuciones.datos?.map((x) => (
              <tr key={x.id} className="clic" onClick={() => navegar({ pagina: 'ejecucion', id: x.id })}>
                <td>{x.fechaInicio.substring(0, 10)}</td>
                <td>{x.estrategia}<div className="tenue">{x.baseDatos}</div></td>
                <td>{nombreTipo(x.tipoRespaldo)}</td>
                <td>{x.origen}</td>
                <td>{x.fechaInicio.substring(11, 16)}</td>
                <td>{x.fechaFin?.substring(11, 16) ?? '-'}</td>
                <td>{tamano(x.tamanoTotalBytes)}</td>
                <td><Estado valor={x.estado} /></td>
                <td><Estado valor={x.verificacion} /></td>
              </tr>
            ))}
          </tbody>
        </table>
        {ejecuciones.datos?.length === 0 && <p className="tenue">Sin ejecuciones.</p>}
        <CajaError error={ejecuciones.error} />
      </div>
    </>
  );
}
