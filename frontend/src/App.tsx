import { useState } from 'react';
import Panel from './paginas/Panel';
import Bases from './paginas/Bases';
import Estrategias from './paginas/Estrategias';
import DetalleEstrategia from './paginas/DetalleEstrategia';
import FormularioEstrategia from './paginas/FormularioEstrategia';
import Ejecuciones from './paginas/Ejecuciones';
import DetalleEjecucion from './paginas/DetalleEjecucion';
import Alertas from './paginas/Alertas';

/** Pantalla actual. Navegacion simple, sin dependencias de enrutamiento. */
export type Vista =
  | { pagina: 'panel' }
  | { pagina: 'bases' }
  | { pagina: 'estrategias' }
  | { pagina: 'estrategia'; id: number }
  | { pagina: 'formulario'; id?: number }
  | { pagina: 'ejecuciones' }
  | { pagina: 'ejecucion'; id: number }
  | { pagina: 'alertas' };

export type Navegar = (v: Vista) => void;

const MENU: { pagina: Vista['pagina']; titulo: string }[] = [
  { pagina: 'panel', titulo: 'Panel' },
  { pagina: 'bases', titulo: 'Bases de datos' },
  { pagina: 'estrategias', titulo: 'Estrategias' },
  { pagina: 'ejecuciones', titulo: 'Ejecuciones' },
  { pagina: 'alertas', titulo: 'Alertas' },
];

const SECCION: Record<Vista['pagina'], Vista['pagina']> = {
  panel: 'panel', bases: 'bases', estrategias: 'estrategias', estrategia: 'estrategias',
  formulario: 'estrategias', ejecuciones: 'ejecuciones', ejecucion: 'ejecuciones', alertas: 'alertas',
};

function leerAdministrador() {
  try {
    return localStorage.getItem('administrador') ?? '';
  } catch {
    return '';
  }
}

export default function App() {
  const [vista, setVista] = useState<Vista>({ pagina: 'panel' });
  const [administrador, setAdministrador] = useState(leerAdministrador);

  const cambiarAdministrador = (nombre: string) => {
    setAdministrador(nombre);
    try {
      localStorage.setItem('administrador', nombre);
    } catch {
      // Sin almacenamiento local: el nombre solo dura esta sesion.
    }
  };

  return (
    <>
      <header className="grunge">
        <h1>Gestion de estrategias de respaldo Oracle</h1>
        <nav>
          {MENU.map((m) => (
            <button key={m.pagina} className={SECCION[vista.pagina] === m.pagina ? 'activo' : ''}
                    onClick={() => setVista({ pagina: m.pagina } as Vista)}>
              {m.titulo}
            </button>
          ))}
        </nav>
        <label className="admin">
          Administrador
          <input value={administrador} placeholder="Nombre para aprobar"
                 onChange={(e) => cambiarAdministrador(e.target.value)} />
        </label>
      </header>
      <main>
        {vista.pagina === 'panel' && <Panel navegar={setVista} />}
        {vista.pagina === 'bases' && <Bases />}
        {vista.pagina === 'estrategias' && <Estrategias navegar={setVista} />}
        {vista.pagina === 'formulario' && <FormularioEstrategia id={vista.id} navegar={setVista} />}
        {vista.pagina === 'estrategia' && (
          <DetalleEstrategia id={vista.id} navegar={setVista} administrador={administrador} />
        )}
        {vista.pagina === 'ejecuciones' && <Ejecuciones navegar={setVista} />}
        {vista.pagina === 'ejecucion' && <DetalleEjecucion id={vista.id} navegar={setVista} />}
        {vista.pagina === 'alertas' && <Alertas navegar={setVista} administrador={administrador} />}
      </main>
    </>
  );
}
