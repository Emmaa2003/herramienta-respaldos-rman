import { useState } from 'react';
import { api, type Generacion, type Validacion } from '../api';
import type { Navegar } from '../App';
import { CajaError, Estado, ListaMensajes, fecha, nombreTipo, tamano, useAccion, useCarga } from '../componentes/comun';

/**
 * Flujo completo de una estrategia:
 * estrategia -> validacion -> script -> aprobacion -> programacion -> ejecucion -> evidencia -> alertas.
 */
export default function DetalleEstrategia({ id, navegar, administrador }:
  { id: number; navegar: Navegar; administrador: string }) {
  const estrategia = useCarga(() => api.estrategia(id), [id]);
  const scripts = useCarga(() => api.scripts(id), [id]);
  const programaciones = useCarga(api.programaciones);
  const proximas = useCarga(() => api.proximas(id).catch(() => [] as string[]), [id]);
  const ejecuciones = useCarga(() => api.ejecuciones(id), [id]);
  const alertas = useCarga(() => api.alertas({ estrategiaId: id, estado: 'ABIERTA' }), [id]);
  const [validacion, setValidacion] = useState<Validacion>();
  const [generacion, setGeneracion] = useState<Generacion>();
  const [comentario, setComentario] = useState('');
  const accion = useAccion();

  const e = estrategia.datos;
  const ultimo = scripts.datos?.[0];
  const aprobado = scripts.datos?.find((s) => s.estado === 'APROBADO' && s.vigente);
  const programacion = programaciones.datos?.find((p) => p.estrategiaId === id);

  const recargarTodo = () => {
    estrategia.recargar(); scripts.recargar(); programaciones.recargar();
    proximas.recargar(); ejecuciones.recargar(); alertas.recargar();
  };

  const validar = () => accion.ejecutar(async () => setValidacion(await api.validar(id)));
  const generar = () => accion.ejecutar(async () => {
    setGeneracion(await api.generarScript(id));
    scripts.recargar();
  });
  const aprobar = () => accion.ejecutar(async () => {
    if (!administrador.trim()) throw new Error('Escriba su nombre en "Administrador" (arriba a la derecha) para aprobar.');
    await api.aprobar(ultimo!.id, administrador, ultimo!.hashContenido, comentario);
    setComentario('');
    recargarTodo();
  });
  const rechazar = () => accion.ejecutar(async () => {
    if (!administrador.trim()) throw new Error('Escriba su nombre en "Administrador" para rechazar.');
    if (!comentario.trim()) throw new Error('Indique el motivo del rechazo en el comentario.');
    await api.rechazar(ultimo!.id, administrador, comentario);
    setComentario('');
    scripts.recargar();
  });
  const activar = (activa: boolean) => accion.ejecutar(async () => { await api.activarEstrategia(id, activa); recargarTodo(); });
  const activarProgramacion = (activa: boolean) => accion.ejecutar(async () => {
    await api.activarProgramacion(id, activa); recargarTodo();
  });
  const ejecutar = () => accion.ejecutar(async () => {
    if (!window.confirm(`Se ejecutara en RMAN el script aprobado version ${aprobado!.version}. ¿Continuar?`)) return;
    const x = await api.ejecutar(id);
    navegar({ pagina: 'ejecucion', id: x.id });
  });

  if (!e) return <CajaError error={estrategia.error} />;

  const pasos = [
    ['Estrategia', true],
    ['Validacion', validacion?.valida ?? !!ultimo],
    ['Script', !!ultimo],
    ['Aprobacion', !!aprobado],
    ['Programacion', programacion?.estado === 'LISTA'],
    ['Ejecucion', !!ejecuciones.datos?.length],
    ['Evidencia', !!ejecuciones.datos?.some((x) => x.verificacion === 'VERIFICADO')],
  ] as const;

  return (
    <>
      <div className="fila" style={{ justifyContent: 'space-between' }}>
        <h2>{e.nombre} <Estado valor={e.activa ? 'ACTIVA' : 'INACTIVA'} /></h2>
        <div className="fila">
          <button onClick={() => navegar({ pagina: 'formulario', id })}>Modificar</button>
          <button disabled={accion.ocupado} onClick={() => activar(!e.activa)}>{e.activa ? 'Desactivar' : 'Activar'}</button>
        </div>
      </div>
      <div className="pasos">{pasos.map(([n, hecho]) => <span key={n} className={hecho ? 'hecho' : ''}>{n}</span>)}</div>
      <CajaError error={accion.error} />

      <div className="panel rejilla">
        <div><div className="tenue">Base / prioridad</div>{e.baseDatos.nombre} - {e.prioridad}<div className="tenue">Responsable: {e.responsable}</div></div>
        <div><div className="tenue">Que</div>{e.elementos.map((el) => el.nombreObjeto ? `${el.tipo} ${el.nombreObjeto}` : el.tipo).join(', ') || '-'}</div>
        <div><div className="tenue">Como</div>{nombreTipo(e.tipoRespaldo)}{e.comprimido ? ', comprimido' : ''}<div className="tenue">Retencion: {e.diasRetencion ?? '-'} dias</div></div>
        <div><div className="tenue">Cuando</div>{e.programacion
          ? `${e.programacion.frecuencia} ${e.programacion.hora.substring(0, 5)}${e.programacion.diasSemana.length ? ' ' + e.programacion.diasSemana.join(',') : ''}`
          : 'Sin programacion'}
          {e.programacion?.ventanaInicio && <div className="tenue">Ventana {e.programacion.ventanaInicio.substring(0, 5)} - {e.programacion.ventanaFin?.substring(0, 5)}</div>}
        </div>
        <div><div className="tenue">Destino</div>{e.rutaDestino}</div>
      </div>

      <div className="panel">
        <div className="fila" style={{ justifyContent: 'space-between' }}>
          <h3 style={{ margin: 0 }}>1. Validacion</h3>
          <button disabled={accion.ocupado} onClick={validar}>Validar contra la base</button>
        </div>
        {validacion && (
          <>
            <p>Resultado: <Estado valor={validacion.valida ? 'VERIFICADO' : 'FALLIDA'} /> {validacion.valida ? 'se puede generar el script.' : 'corrija los problemas bloqueantes.'}</p>
            <ListaMensajes mensajes={validacion.hallazgos} />
          </>
        )}
      </div>

      <div className="panel">
        <div className="fila" style={{ justifyContent: 'space-between' }}>
          <h3 style={{ margin: 0 }}>2. Script RMAN</h3>
          <button className="primario" disabled={accion.ocupado} onClick={generar}>Generar script</button>
        </div>
        {generacion && (
          <>
            <p className="tenue">{generacion.verificacionSintaxis}</p>
            <details>
              <summary>Como se tradujo la estrategia ({generacion.pasos.length} pasos)</summary>
              <table>
                <thead><tr><th>Origen</th><th>Instruccion</th><th>Explicacion</th></tr></thead>
                <tbody>
                  {generacion.pasos.map((p, i) => (
                    <tr key={i}><td>{p.origen}</td><td><code>{p.instruccion}</code></td><td>{p.explicacion}</td></tr>
                  ))}
                </tbody>
              </table>
            </details>
            <ListaMensajes mensajes={generacion.hallazgos} />
          </>
        )}
        {ultimo ? (
          <>
            <p>
              Version {ultimo.version} <Estado valor={ultimo.estado} /> {ultimo.vigente ? '' : <Estado valor="NO VIGENTE" />}
              {' '}<span className="tenue">generado {fecha(ultimo.fechaGeneracion)}
              {ultimo.aprobadoPor && `, aprobado por ${ultimo.aprobadoPor} el ${fecha(ultimo.fechaAprobacion)}`}
              {ultimo.rechazadoPor && `, rechazado por ${ultimo.rechazadoPor}: ${ultimo.comentarioRevision}`}</span>
            </p>
            <pre>{ultimo.contenido}</pre>
            <p className="tenue">Huella SHA-256: <code>{ultimo.hashContenido}</code></p>
          </>
        ) : <p className="tenue">Todavia no hay script. Valide y genere.</p>}
      </div>

      {ultimo?.estado === 'GENERADO' && ultimo.vigente && (
        <div className="panel">
          <h3 style={{ marginTop: 0 }}>3. Aprobacion del administrador</h3>
          <p>Revise el script de arriba. Al aprobar se registra a <strong>{administrador || '(sin nombre)'}</strong> y la huella del texto revisado.</p>
          <label>Comentario (obligatorio para rechazar)<textarea value={comentario} onChange={(x) => setComentario(x.target.value)} /></label>
          <div className="fila" style={{ marginTop: 8 }}>
            <button className="primario" disabled={accion.ocupado} onClick={aprobar}>Aprobar script</button>
            <button className="peligro" disabled={accion.ocupado} onClick={rechazar}>Rechazar</button>
          </div>
        </div>
      )}

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>4. Programacion</h3>
        {programacion ? (
          <>
            <p><Estado valor={programacion.estado} /> {programacion.detalle}</p>
            <p>Proximas ejecuciones: {proximas.datos?.length ? proximas.datos.map(fecha).join(' | ') : 'ninguna'}</p>
            <button disabled={accion.ocupado} onClick={() => activarProgramacion(!programacion.programacionActiva)}>
              {programacion.programacionActiva ? 'Desactivar programacion' : 'Activar programacion'}
            </button>
          </>
        ) : <p className="tenue">La estrategia no tiene programacion. Agreguela con "Modificar".</p>}
      </div>

      <div className="panel">
        <div className="fila" style={{ justifyContent: 'space-between' }}>
          <h3 style={{ margin: 0 }}>5. Ejecucion y evidencia</h3>
          <button className="primario" disabled={accion.ocupado || !aprobado} onClick={ejecutar}
                  title={aprobado ? '' : 'Necesita un script aprobado y vigente'}>Ejecutar ahora</button>
        </div>
        <table>
          <thead><tr><th>Inicio</th><th>Origen</th><th>Duracion</th><th>Tamano</th><th>Resultado</th><th>Verificacion</th></tr></thead>
          <tbody>
            {ejecuciones.datos?.map((x) => (
              <tr key={x.id} className="clic" onClick={() => navegar({ pagina: 'ejecucion', id: x.id })}>
                <td>{fecha(x.fechaInicio)}</td><td>{x.origen}</td>
                <td>{x.duracionSegundos != null ? x.duracionSegundos + ' s' : '-'}</td>
                <td>{tamano(x.tamanoTotalBytes)}</td><td><Estado valor={x.estado} /></td><td><Estado valor={x.verificacion} /></td>
              </tr>
            ))}
          </tbody>
        </table>
        {ejecuciones.datos?.length === 0 && <p className="tenue">Sin ejecuciones.</p>}
      </div>

      <div className="panel">
        <h3 style={{ marginTop: 0 }}>Alertas abiertas</h3>
        <ListaMensajes mensajes={(alertas.datos ?? []).map((a) => ({ tipo: a.tipo, codigo: a.codigo, texto: a.mensaje }))} />
      </div>
    </>
  );
}
