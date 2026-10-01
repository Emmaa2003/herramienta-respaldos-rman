import { useEffect, useState } from 'react';
import {
  api, type DiaSemana, type Elemento, type EstrategiaSolicitud, type Frecuencia, type Prioridad,
  type Programacion, type TipoElemento, type TipoRespaldo,
} from '../api';
import type { Navegar } from '../App';
import { CajaError, useAccion, useCarga } from '../componentes/comun';

const NUEVA: EstrategiaSolicitud = {
  nombre: '', descripcion: '', baseDatosId: 0, responsable: '', prioridad: 'MEDIA',
  elementos: [{ tipo: 'BASE_DATOS', nombreObjeto: null }],
  tipoRespaldo: 'INCREMENTAL_NIVEL_0', comprimido: true, diasRetencion: 7,
  rutaDestino: '/opt/oracle/oradata/respaldos', programacion: null,
};

const PROGRAMACION: Programacion = {
  fechaInicio: new Date().toISOString().substring(0, 10), hora: '23:00', frecuencia: 'DIARIA',
  diasSemana: [], intervalo: 1, ventanaInicio: '22:00', ventanaFin: '05:00',
};

/** Construccion de la estrategia: informacion general, QUE, COMO, CUANDO y destino. */
export default function FormularioEstrategia({ id, navegar }: { id?: number; navegar: Navegar }) {
  const opciones = useCarga(api.opciones);
  const bases = useCarga(api.bases);
  const [s, setS] = useState<EstrategiaSolicitud>(NUEVA);
  // Texto tal como se escribe (con comas) para tablespaces y datafiles.
  const [texto, setTexto] = useState<Record<'TABLESPACE' | 'DATAFILE', string>>({ TABLESPACE: '', DATAFILE: '' });
  const accion = useAccion();

  useEffect(() => {
    if (id === undefined) return;
    api.estrategia(id).then((e) => {
      const lista = (t: TipoElemento) => e.elementos.filter((x) => x.tipo === t).map((x) => x.nombreObjeto).join(', ');
      setTexto({ TABLESPACE: lista('TABLESPACE'), DATAFILE: lista('DATAFILE') });
      setS({
      nombre: e.nombre, descripcion: e.descripcion ?? '', baseDatosId: e.baseDatos.id, responsable: e.responsable,
      prioridad: e.prioridad, elementos: e.elementos, tipoRespaldo: e.tipoRespaldo, comprimido: e.comprimido,
      diasRetencion: e.diasRetencion, rutaDestino: e.rutaDestino,
      programacion: e.programacion && {
        ...e.programacion,
        hora: e.programacion.hora.substring(0, 5),
        ventanaInicio: e.programacion.ventanaInicio?.substring(0, 5) ?? null,
        ventanaFin: e.programacion.ventanaFin?.substring(0, 5) ?? null,
      },
      });
    });
  }, [id]);

  useEffect(() => {
    if (id === undefined && s.baseDatosId === 0 && bases.datos?.length) {
      setS((x) => ({ ...x, baseDatosId: bases.datos![0].id }));
    }
  }, [bases.datos, id, s.baseDatosId]);

  const cambiar = (c: Partial<EstrategiaSolicitud>) => setS({ ...s, ...c });
  const cambiarProg = (c: Partial<Programacion>) => setS({ ...s, programacion: { ...s.programacion!, ...c } });

  const tiene = (t: TipoElemento) => s.elementos.some((e) => e.tipo === t);
  const alternar = (t: TipoElemento) =>
    cambiar({ elementos: tiene(t) ? s.elementos.filter((e) => e.tipo !== t) : [...s.elementos, { tipo: t, nombreObjeto: null }] });
  const cambiarObjetos = (t: 'TABLESPACE' | 'DATAFILE', valor: string) => {
    setTexto({ ...texto, [t]: valor });
    const nuevos: Elemento[] = valor.split(',').map((x) => x.trim()).filter(Boolean).map((n) => ({ tipo: t, nombreObjeto: n }));
    cambiar({ elementos: [...s.elementos.filter((e) => e.tipo !== t), ...nuevos] });
  };

  const guardar = () => accion.ejecutar(async () => {
    const e = id === undefined ? await api.crearEstrategia(s) : await api.actualizarEstrategia(id, s);
    navegar({ pagina: 'estrategia', id: e.id });
  });

  const o = opciones.datos;
  const p = s.programacion;
  return (
    <>
      <h2>{id === undefined ? 'Nueva estrategia' : 'Modificar estrategia'}</h2>
      <CajaError error={opciones.error ?? bases.error} />

      <fieldset>
        <legend>Informacion general</legend>
        <div className="rejilla">
          <label>Nombre<input value={s.nombre} onChange={(e) => cambiar({ nombre: e.target.value })} /></label>
          <label>Responsable<input value={s.responsable} onChange={(e) => cambiar({ responsable: e.target.value })} /></label>
          <label>Base de datos
            <select value={s.baseDatosId} onChange={(e) => cambiar({ baseDatosId: Number(e.target.value) })}>
              {bases.datos?.map((b) => <option key={b.id} value={b.id}>{b.nombre}</option>)}
            </select>
          </label>
          <label>Prioridad
            <select value={s.prioridad} onChange={(e) => cambiar({ prioridad: e.target.value as Prioridad })}>
              {o?.prioridades.map((x) => <option key={x.valor} value={x.valor}>{x.valor} - {x.descripcion}</option>)}
            </select>
          </label>
        </div>
        <p className="tenue">
          Criterio: {o?.prioridades.find((x) => x.valor === s.prioridad)?.criterio}. Se espera un respaldo exitoso al menos
          cada {o?.prioridades.find((x) => x.valor === s.prioridad)?.horasMaximasSinRespaldo} h.
        </p>
        <label>Descripcion<textarea value={s.descripcion} onChange={(e) => cambiar({ descripcion: e.target.value })} /></label>
      </fieldset>

      <fieldset>
        <legend>Que respaldar</legend>
        <div className="fila">
          {o?.elementos.filter((x) => !x.requiereNombre).map((x) => (
            <label key={x.valor} className="check">
              <input type="checkbox" checked={tiene(x.valor)} onChange={() => alternar(x.valor)} /> {x.descripcion}
            </label>
          ))}
        </div>
        <div className="rejilla" style={{ marginTop: 10 }}>
          <label>Tablespaces (separados por coma)
            <input value={texto.TABLESPACE} placeholder="USERS, SYSAUX" onChange={(e) => cambiarObjetos('TABLESPACE', e.target.value)} />
          </label>
          <label>Datafiles por numero o ruta (separados por coma)
            <input value={texto.DATAFILE} placeholder="12" onChange={(e) => cambiarObjetos('DATAFILE', e.target.value)} />
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>Como respaldar</legend>
        <div className="rejilla">
          <label>Tipo de respaldo
            <select value={s.tipoRespaldo} onChange={(e) => cambiar({ tipoRespaldo: e.target.value as TipoRespaldo })}>
              {o?.tiposRespaldo.map((x) => <option key={x.valor} value={x.valor}>{x.nombre}</option>)}
            </select>
          </label>
          <label>Retencion (dias)
            <input type="number" min={1} value={s.diasRetencion ?? ''}
                   onChange={(e) => cambiar({ diasRetencion: e.target.value ? Number(e.target.value) : null })} />
          </label>
          <label className="check">
            <input type="checkbox" checked={s.comprimido} onChange={(e) => cambiar({ comprimido: e.target.checked })} /> Comprimido
          </label>
        </div>
        <p className="tenue">{o?.tiposRespaldo.find((x) => x.valor === s.tipoRespaldo)?.descripcion}</p>
      </fieldset>

      <fieldset>
        <legend>Cuando respaldar</legend>
        <label className="check">
          <input type="checkbox" checked={p !== null} onChange={(e) => cambiar({ programacion: e.target.checked ? PROGRAMACION : null })} />
          Programar ejecucion automatica
        </label>
        {p && (
          <>
            <div className="rejilla" style={{ marginTop: 10 }}>
              <label>Fecha de inicio<input type="date" value={p.fechaInicio} onChange={(e) => cambiarProg({ fechaInicio: e.target.value })} /></label>
              <label>Hora<input type="time" value={p.hora} onChange={(e) => cambiarProg({ hora: e.target.value })} /></label>
              <label>Frecuencia
                <select value={p.frecuencia}
                        onChange={(e) => cambiarProg({ frecuencia: e.target.value as Frecuencia, diasSemana: [] })}>
                  {o?.frecuencias.map((x) => <option key={x.valor} value={x.valor}>{x.descripcion}</option>)}
                </select>
              </label>
              <label>Intervalo<input type="number" min={1} value={p.intervalo} onChange={(e) => cambiarProg({ intervalo: Number(e.target.value) })} /></label>
              <label>Ventana desde<input type="time" value={p.ventanaInicio ?? ''} onChange={(e) => cambiarProg({ ventanaInicio: e.target.value || null })} /></label>
              <label>Ventana hasta<input type="time" value={p.ventanaFin ?? ''} onChange={(e) => cambiarProg({ ventanaFin: e.target.value || null })} /></label>
            </div>
            {p.frecuencia === 'SEMANAL' && (
              <div className="fila" style={{ marginTop: 10 }}>
                Dias:
                {o?.diasSemana.map((d: DiaSemana) => (
                  <label key={d} className="check">
                    <input type="checkbox" checked={p.diasSemana.includes(d)}
                           onChange={() => cambiarProg({ diasSemana: p.diasSemana.includes(d) ? p.diasSemana.filter((x) => x !== d) : [...p.diasSemana, d] })} />
                    {d}
                  </label>
                ))}
              </div>
            )}
          </>
        )}
      </fieldset>

      <fieldset>
        <legend>Destino</legend>
        <label>Ruta dentro del contenedor (dispositivo DISK)
          <input value={s.rutaDestino} onChange={(e) => cambiar({ rutaDestino: e.target.value })} />
        </label>
        <p className="tenue">El espacio disponible se revisa al validar la estrategia.</p>
      </fieldset>

      <div className="fila">
        <button className="primario" disabled={accion.ocupado} onClick={guardar}>Guardar</button>
        <button onClick={() => navegar(id === undefined ? { pagina: 'estrategias' } : { pagina: 'estrategia', id })}>Cancelar</button>
      </div>
      <CajaError error={accion.error} />
    </>
  );
}
