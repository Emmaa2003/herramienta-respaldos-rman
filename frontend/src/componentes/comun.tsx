import { useCallback, useEffect, useState } from 'react';
import { ErrorApi, type Mensaje } from '../api';

/** Etiqueta de los tres tipos de mensaje del proyecto (regla 4). */
const NOMBRE_TIPO: Record<string, string> = {
  INFORMATIVO: 'Informativo',
  ADVERTENCIA: 'Advertencia',
  RECOMENDACION: 'Recomendacion',
};

export function ListaMensajes({ mensajes }: { mensajes: Mensaje[] }) {
  if (mensajes.length === 0) {
    return <p className="tenue">Sin mensajes.</p>;
  }
  return (
    <ul className="mensajes">
      {mensajes.map((m, i) => (
        <li key={i} className={`mensaje ${m.tipo.toLowerCase()}`}>
          <span className={`insignia ${m.tipo.toLowerCase()}`}>
            {NOMBRE_TIPO[m.tipo]}
            {m.bloqueante ? ' - bloqueante' : ''}
          </span>
          <span className="codigo">{m.codigo}</span>
          <span>{m.texto}</span>
        </li>
      ))}
    </ul>
  );
}

const CLASE_ESTADO: Record<string, string> = {
  EXITOSO: 'ok', VERIFICADO: 'ok', APROBADO: 'ok', LISTA: 'ok', ARCHIVELOG: 'ok', ATENDIDA: 'tenue', APLICADA: 'ok', ACTIVA: 'ok',
  'NO VIGENTE': 'mal',
  CON_ADVERTENCIAS: 'aviso', GENERADO: 'aviso', EN_CURSO: 'aviso', PENDIENTE: 'aviso', ABIERTA: 'aviso',
  FALLIDO: 'mal', FALLIDA: 'mal', RECHAZADO: 'mal', NOARCHIVELOG: 'mal',
};

export function Estado({ valor }: { valor: string | null | undefined }) {
  if (!valor) return <span className="tenue">-</span>;
  return <span className={`estado ${CLASE_ESTADO[valor] ?? 'tenue'}`}>{valor.replaceAll('_', ' ')}</span>;
}

export function CajaError({ error }: { error: unknown }) {
  if (!error) return null;
  if (error instanceof ErrorApi) {
    return (
      <div className="error">
        <strong>{error.message}</strong>
        {error.campos && (
          <ul>
            {Object.entries(error.campos).map(([c, m]) => (
              <li key={c}>{c}: {m}</li>
            ))}
          </ul>
        )}
        {error.hallazgos && <ListaMensajes mensajes={error.hallazgos.filter((h) => h.bloqueante)} />}
      </div>
    );
  }
  return <div className="error">{String(error)}</div>;
}

/** Carga datos al montar y permite recargarlos. */
export function useCarga<T>(cargar: () => Promise<T>, dependencias: unknown[] = []) {
  const [datos, setDatos] = useState<T | undefined>();
  const [error, setError] = useState<unknown>();
  const recargar = useCallback(() => {
    cargar().then((d) => { setDatos(d); setError(undefined); }, setError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencias);
  useEffect(recargar, [recargar]);
  return { datos, error, recargar };
}

/** Ejecuta una accion mostrando su error y evitando dobles clics. */
export function useAccion() {
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState<unknown>();
  const ejecutar = async (accion: () => Promise<unknown>) => {
    setOcupado(true);
    setError(undefined);
    try {
      await accion();
    } catch (e) {
      setError(e);
    } finally {
      setOcupado(false);
    }
  };
  return { ocupado, error, ejecutar };
}

export const fecha = (iso: string | null | undefined) =>
  iso ? iso.replace('T', ' ').substring(0, 16) : '-';

export const tamano = (bytes: number | null | undefined) => {
  if (bytes == null) return '-';
  if (bytes >= 1024 ** 3) return (bytes / 1024 ** 3).toFixed(1) + ' GB';
  if (bytes >= 1024 ** 2) return (bytes / 1024 ** 2).toFixed(1) + ' MB';
  return Math.round(bytes / 1024) + ' KB';
};

export const nombreTipo = (t: string) =>
  ({
    COMPLETO: 'Completo',
    INCREMENTAL_NIVEL_0: 'Incremental nivel 0',
    INCREMENTAL_NIVEL_1_DIFERENCIAL: 'Nivel 1 diferencial',
    INCREMENTAL_NIVEL_1_ACUMULATIVO: 'Nivel 1 acumulativo',
  })[t] ?? t;
