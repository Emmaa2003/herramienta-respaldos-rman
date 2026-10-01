import { api } from '../api';
import type { Navegar } from '../App';
import { CajaError, Estado, fecha, nombreTipo, useCarga } from '../componentes/comun';

export default function Estrategias({ navegar }: { navegar: Navegar }) {
  const estrategias = useCarga(api.estrategias);

  return (
    <>
      <div className="fila" style={{ justifyContent: 'space-between' }}>
        <h2>Estrategias</h2>
        <button className="primario" onClick={() => navegar({ pagina: 'formulario' })}>Nueva estrategia</button>
      </div>
      <div className="panel">
        <table>
          <thead>
            <tr><th>Nombre</th><th>Base</th><th>Prioridad</th><th>Que</th><th>Como</th><th>Cuando</th><th>Estado</th></tr>
          </thead>
          <tbody>
            {estrategias.datos?.map((e) => (
              <tr key={e.id} className="clic" onClick={() => navegar({ pagina: 'estrategia', id: e.id })}>
                <td>{e.nombre}<div className="tenue">{e.responsable}</div></td>
                <td>{e.baseDatos.nombre}</td>
                <td>{e.prioridad}</td>
                <td>{e.elementos.map((el) => el.nombreObjeto ? `${el.tipo} ${el.nombreObjeto}` : el.tipo).join(', ') || '-'}</td>
                <td>{nombreTipo(e.tipoRespaldo)}{e.comprimido ? ', comprimido' : ''}</td>
                <td>
                  {e.programacion
                    ? <>{e.programacion.frecuencia} {e.programacion.hora.substring(0, 5)}<div className="tenue">proxima: {fecha(e.programacion.proximaEjecucion)}</div></>
                    : <span className="tenue">sin programacion</span>}
                </td>
                <td><Estado valor={e.activa ? 'ACTIVA' : 'INACTIVA'} /></td>
              </tr>
            ))}
          </tbody>
        </table>
        {estrategias.datos?.length === 0 && <p className="tenue">Todavia no hay estrategias.</p>}
        <CajaError error={estrategias.error} />
      </div>
    </>
  );
}
