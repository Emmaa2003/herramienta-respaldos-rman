import { useState } from 'react';
import { api, type Ambiente, type Inspeccion } from '../api';
import { CajaError, Estado, ListaMensajes, fecha, tamano, useAccion, useCarga } from '../componentes/comun';

const VACIA = { nombre: '', descripcion: '', contenedor: 'oracle-xe', servicio: 'XEPDB1', ambiente: 'PRUEBAS' as Ambiente };

/** Registro de bases de datos e inspeccion de su modo de archivado y catalogo. */
export default function Bases() {
  const bases = useCarga(api.bases);
  const [nueva, setNueva] = useState(VACIA);
  const [inspeccion, setInspeccion] = useState<Inspeccion>();
  const accion = useAccion();

  const registrar = () => accion.ejecutar(async () => {
    await api.crearBase(nueva);
    setNueva(VACIA);
    bases.recargar();
  });

  const inspeccionar = (id: number) => accion.ejecutar(async () => {
    setInspeccion(await api.inspeccionar(id));
    bases.recargar();
  });

  return (
    <>
      <h2>Bases de datos</h2>
      <div className="panel">
        <table>
          <thead><tr><th>Nombre</th><th>Contenedor / servicio</th><th>Ambiente</th><th>Modo de archivado</th><th>Inspeccion</th><th></th></tr></thead>
          <tbody>
            {bases.datos?.map((b) => (
              <tr key={b.id}>
                <td>{b.nombre}<div className="tenue">{b.descripcion}</div></td>
                <td>{b.contenedor} / {b.servicio}</td>
                <td>{b.ambiente}</td>
                <td><Estado valor={b.modoArchivado} /></td>
                <td>{fecha(b.fechaInspeccion)}</td>
                <td><button disabled={accion.ocupado} onClick={() => inspeccionar(b.id)}>Inspeccionar</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <CajaError error={bases.error} />
      </div>

      {inspeccion && (
        <div className="panel">
          <h3>Inspeccion de {inspeccion.base.nombre}</h3>
          <p>
            Base {inspeccion.nombreBase ?? '-'}, PDB {inspeccion.pdb ?? '-'}, modo <Estado valor={inspeccion.modoArchivado} />.
            La aplicacion solo informa: no cambia el modo de archivado.
          </p>
          <ListaMensajes mensajes={inspeccion.mensajes} />
          {inspeccion.tablespaces.length > 0 && (
            <details>
              <summary>Tablespaces ({inspeccion.tablespaces.length}) y datafiles ({inspeccion.datafiles.length})</summary>
              <table>
                <thead><tr><th>Tablespace (nombre RMAN)</th><th>Contenido</th><th>Estado</th></tr></thead>
                <tbody>
                  {inspeccion.tablespaces.map((t) => (
                    <tr key={t.nombre}><td>{t.pdb}:{t.nombre}</td><td>{t.contenido}</td><td>{t.estado}</td></tr>
                  ))}
                </tbody>
              </table>
              <table>
                <thead><tr><th>N.</th><th>Ruta</th><th>Tablespace</th><th>Tamano</th></tr></thead>
                <tbody>
                  {inspeccion.datafiles.map((d) => (
                    <tr key={d.numero}><td>{d.numero}</td><td>{d.ruta}</td><td>{d.tablespace}</td><td>{tamano(d.bytes)}</td></tr>
                  ))}
                </tbody>
              </table>
            </details>
          )}
        </div>
      )}

      <div className="panel">
        <h3>Registrar base de datos</h3>
        <div className="rejilla">
          <label>Nombre<input value={nueva.nombre} onChange={(e) => setNueva({ ...nueva, nombre: e.target.value })} /></label>
          <label>Descripcion<input value={nueva.descripcion} onChange={(e) => setNueva({ ...nueva, descripcion: e.target.value })} /></label>
          <label>Contenedor Docker<input value={nueva.contenedor} onChange={(e) => setNueva({ ...nueva, contenedor: e.target.value })} /></label>
          <label>Servicio (PDB)<input value={nueva.servicio} onChange={(e) => setNueva({ ...nueva, servicio: e.target.value })} /></label>
          <label>Ambiente
            <select value={nueva.ambiente} onChange={(e) => setNueva({ ...nueva, ambiente: e.target.value as Ambiente })}>
              <option>DESARROLLO</option><option>PRUEBAS</option><option>PRODUCCION</option>
            </select>
          </label>
        </div>
        <p><button className="primario" disabled={accion.ocupado} onClick={registrar}>Registrar</button></p>
        <CajaError error={accion.error} />
      </div>
    </>
  );
}
