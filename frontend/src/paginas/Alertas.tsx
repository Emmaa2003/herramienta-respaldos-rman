import { useState } from 'react';
import { api, type Alerta } from '../api';
import type { Navegar } from '../App';
import { CajaError, Estado, fecha, useAccion, useCarga } from '../componentes/comun';

const NOMBRE: Record<string, string> = { INFORMATIVO: 'Informativo', ADVERTENCIA: 'Advertencia', RECOMENDACION: 'Recomendacion' };

/** Panel de alertas: el administrador atiende, descarta o aplica (nunca se aplican solas). */
export default function Alertas({ navegar, administrador }: { navegar: Navegar; administrador: string }) {
  const [estado, setEstado] = useState('ABIERTA');
  const [tipo, setTipo] = useState('');
  const alertas = useCarga(() => api.alertas({ estado, tipo }), [estado, tipo]);
  const [comentario, setComentario] = useState<Record<number, string>>({});
  const [revision, setRevision] = useState<string>();
  const accion = useAccion();

  const actuar = (a: Alerta, que: 'atender' | 'descartar' | 'aplicar') => accion.ejecutar(async () => {
    if (!administrador.trim()) throw new Error('Escriba su nombre en "Administrador" (arriba a la derecha).');
    await api.accionAlerta(a.id, que, administrador, comentario[a.id] ?? '');
    alertas.recargar();
  });

  const revisar = () => accion.ejecutar(async () => {
    const r = await api.revisarAlertas();
    setRevision(`Revision: ${r.abiertas} nueva(s), ${r.cerradas} cerrada(s), ${r.vigentes} abierta(s).`);
    alertas.recargar();
  });

  return (
    <>
      <div className="fila" style={{ justifyContent: 'space-between' }}>
        <h2>Alertas</h2>
        <button disabled={accion.ocupado} onClick={revisar}>Revisar ahora</button>
      </div>
      {revision && <p className="tenue">{revision}</p>}
      <div className="fila panel">
        <label>Estado
          <select value={estado} onChange={(e) => setEstado(e.target.value)}>
            <option value="">Todos</option><option>ABIERTA</option><option>ATENDIDA</option><option>DESCARTADA</option><option>APLICADA</option>
          </select>
        </label>
        <label>Tipo
          <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="">Todos</option><option>ADVERTENCIA</option><option>RECOMENDACION</option><option>INFORMATIVO</option>
          </select>
        </label>
      </div>
      <CajaError error={accion.error ?? alertas.error} />

      {alertas.datos?.map((a) => (
        <div key={a.id} className={`panel mensaje ${a.tipo.toLowerCase()}`} style={{ display: 'block' }}>
          <div className="fila">
            <span className={`insignia ${a.tipo.toLowerCase()}`}>{NOMBRE[a.tipo]}</span>
            <span className="codigo">{a.codigo}</span>
            <Estado valor={a.estado} />
            <span className="tenue">{fecha(a.fechaCreacion)}</span>
            {a.estrategiaId && (
              <a href="#" onClick={(ev) => { ev.preventDefault(); navegar({ pagina: 'estrategia', id: a.estrategiaId! }); }}>{a.estrategia}</a>
            )}
            {!a.estrategiaId && a.baseDatos && <span>{a.baseDatos}</span>}
            {a.ejecucionId && (
              <a href="#" onClick={(ev) => { ev.preventDefault(); navegar({ pagina: 'ejecucion', id: a.ejecucionId! }); }}>ejecucion {a.ejecucionId}</a>
            )}
          </div>
          <p>{a.mensaje}</p>
          {a.estado === 'ABIERTA' ? (
            <div className="fila">
              <input style={{ flex: 1, minWidth: 200 }} placeholder="Comentario"
                     value={comentario[a.id] ?? ''} onChange={(e) => setComentario({ ...comentario, [a.id]: e.target.value })} />
              {a.tipo === 'RECOMENDACION' && (
                <button className="primario" disabled={accion.ocupado} onClick={() => actuar(a, 'aplicar')}
                        title={a.aplicable ? 'La aplicacion agrega el elemento a la estrategia' : 'Indique en el comentario que hizo'}>
                  {a.aplicable ? 'Aplicar a la estrategia' : 'Marcar como aplicada'}
                </button>
              )}
              <button disabled={accion.ocupado} onClick={() => actuar(a, 'atender')}>Atendida</button>
              <button disabled={accion.ocupado} onClick={() => actuar(a, 'descartar')}>Descartar</button>
            </div>
          ) : (
            <p className="tenue">{a.atendidaPor} - {fecha(a.fechaAtencion)}{a.comentario ? ': ' + a.comentario : ''}</p>
          )}
        </div>
      ))}
      {alertas.datos?.length === 0 && <p className="tenue">No hay alertas con ese filtro.</p>}
    </>
  );
}
