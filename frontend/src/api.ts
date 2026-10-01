// Cliente de la API del backend. Los tipos reflejan los records de Java.

export type TipoMensaje = 'INFORMATIVO' | 'ADVERTENCIA' | 'RECOMENDACION';
export type Ambiente = 'DESARROLLO' | 'PRUEBAS' | 'PRODUCCION';
export type ModoArchivado = 'ARCHIVELOG' | 'NOARCHIVELOG';
export type Prioridad = 'ALTA' | 'MEDIA' | 'BAJA';
export type TipoRespaldo =
  | 'COMPLETO'
  | 'INCREMENTAL_NIVEL_0'
  | 'INCREMENTAL_NIVEL_1_DIFERENCIAL'
  | 'INCREMENTAL_NIVEL_1_ACUMULATIVO';
export type TipoElemento = 'BASE_DATOS' | 'TABLESPACE' | 'DATAFILE' | 'CONTROLFILE' | 'SPFILE' | 'ARCHIVELOG';
export type Frecuencia = 'UNA_VEZ' | 'CADA_N_HORAS' | 'DIARIA' | 'SEMANAL' | 'MENSUAL';
export type DiaSemana = 'LUN' | 'MAR' | 'MIE' | 'JUE' | 'VIE' | 'SAB' | 'DOM';
export type EstadoScript = 'GENERADO' | 'APROBADO' | 'RECHAZADO' | 'INVALIDADO';
export type EstadoEjecucion = 'EN_CURSO' | 'EXITOSO' | 'CON_ADVERTENCIAS' | 'FALLIDO';
export type EstadoVerificacion = 'PENDIENTE' | 'VERIFICADO' | 'FALLIDA' | 'NO_APLICA';
export type EstadoAlerta = 'ABIERTA' | 'ATENDIDA' | 'DESCARTADA' | 'APLICADA';

export interface Mensaje {
  tipo: TipoMensaje;
  codigo: string;
  texto: string;
  bloqueante?: boolean;
}

export interface BaseDatos {
  id: number;
  nombre: string;
  descripcion: string | null;
  contenedor: string;
  servicio: string;
  ambiente: Ambiente;
  modoArchivado: ModoArchivado | null;
  fechaInspeccion: string | null;
  fechaRegistro: string;
}

export interface Inspeccion {
  base: BaseDatos;
  contenedorActivo: boolean;
  nombreBase: string | null;
  pdb: string | null;
  modoArchivado: ModoArchivado | null;
  tablespaces: { pdb: string; nombre: string; contenido: string; estado: string }[];
  datafiles: { numero: number; ruta: string; tablespace: string; bytes: number }[];
  mensajes: Mensaje[];
}

export interface Programacion {
  fechaInicio: string;
  hora: string;
  frecuencia: Frecuencia;
  diasSemana: DiaSemana[];
  intervalo: number;
  ventanaInicio: string | null;
  ventanaFin: string | null;
  activa?: boolean;
  proximaEjecucion?: string | null;
}

export interface Elemento {
  tipo: TipoElemento;
  nombreObjeto: string | null;
}

export interface Estrategia {
  id: number;
  nombre: string;
  descripcion: string | null;
  baseDatos: { id: number; nombre: string };
  responsable: string;
  prioridad: Prioridad;
  activa: boolean;
  elementos: Elemento[];
  tipoRespaldo: TipoRespaldo;
  comprimido: boolean;
  diasRetencion: number | null;
  rutaDestino: string;
  dispositivo: string;
  programacion: Programacion | null;
  fechaCreacion: string;
  fechaModificacion: string;
}

export interface EstrategiaSolicitud {
  nombre: string;
  descripcion: string;
  baseDatosId: number;
  responsable: string;
  prioridad: Prioridad;
  elementos: Elemento[];
  tipoRespaldo: TipoRespaldo;
  comprimido: boolean;
  diasRetencion: number | null;
  rutaDestino: string;
  programacion: Programacion | null;
}

export interface Opciones {
  prioridades: { valor: Prioridad; descripcion: string; criterio: string; horasMaximasSinRespaldo: number }[];
  tiposRespaldo: { valor: TipoRespaldo; nombre: string; descripcion: string; incremental: boolean }[];
  elementos: { valor: TipoElemento; descripcion: string; requiereNombre: boolean }[];
  frecuencias: { valor: Frecuencia; descripcion: string }[];
  diasSemana: DiaSemana[];
}

export interface Validacion {
  estrategiaId: number;
  valida: boolean;
  fecha: string;
  hallazgos: Mensaje[];
}

export interface Script {
  id: number;
  estrategiaId: number;
  version: number;
  contenido: string;
  hashContenido: string;
  estado: EstadoScript;
  vigente: boolean;
  fechaGeneracion: string;
  aprobadoPor: string | null;
  fechaAprobacion: string | null;
  rechazadoPor: string | null;
  fechaRechazo: string | null;
  comentarioRevision: string | null;
}

export interface Generacion {
  script: Script;
  pasos: { origen: string; instruccion: string; explicacion: string }[];
  hallazgos: Mensaje[];
  verificacionSintaxis: string;
}

export interface EstadoProgramacion {
  estrategiaId: number;
  estrategia: string;
  frecuencia: Frecuencia;
  programacionActiva: boolean;
  proximaEjecucion: string | null;
  estado: 'LISTA' | 'PROGRAMACION_INACTIVA' | 'ESTRATEGIA_INACTIVA' | 'SIN_SCRIPT_EJECUTABLE' | 'SIN_PROXIMA';
  detalle: string;
}

export interface EjecucionResumen {
  id: number;
  estrategiaId: number;
  estrategia: string;
  baseDatos: string;
  tipoRespaldo: TipoRespaldo;
  origen: 'MANUAL' | 'PROGRAMADA';
  fechaInicio: string;
  fechaFin: string | null;
  duracionSegundos: number | null;
  estado: EstadoEjecucion;
  verificacion: EstadoVerificacion;
  tamanoTotalBytes: number | null;
  mensajeError: string | null;
}

export interface Ejecucion extends EjecucionResumen {
  scriptId: number;
  scriptVersion: number;
  fechaProgramada: string | null;
  codigoSalida: number | null;
  rutaDestino: string;
  archivos: { ruta: string; tipo: string; tamanoBytes: number | null; existe: boolean }[];
  scriptEjecutado: string;
  salidaRman: string | null;
  salidaVerificacion: string | null;
}

export interface Alerta {
  id: number;
  tipo: TipoMensaje;
  codigo: string;
  mensaje: string;
  estado: EstadoAlerta;
  baseDatosId: number | null;
  baseDatos: string | null;
  estrategiaId: number | null;
  estrategia: string | null;
  ejecucionId: number | null;
  fechaCreacion: string;
  fechaAtencion: string | null;
  atendidaPor: string | null;
  comentario: string | null;
  aplicable: boolean;
}

/** Error devuelto por la API (ProblemDetail), con campos o hallazgos si los hay. */
export class ErrorApi extends Error {
  constructor(
    public estado: number,
    mensaje: string,
    public campos?: Record<string, string>,
    public hallazgos?: Mensaje[],
  ) {
    super(mensaje);
  }
}

async function pedir<T>(metodo: string, ruta: string, cuerpo?: unknown): Promise<T> {
  const respuesta = await fetch('/api' + ruta, {
    method: metodo,
    headers: cuerpo === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
  if (respuesta.status === 204) {
    return undefined as T;
  }
  const texto = await respuesta.text();
  const datos = texto ? JSON.parse(texto) : undefined;
  if (!respuesta.ok) {
    throw new ErrorApi(respuesta.status, datos?.detail ?? `Error ${respuesta.status}`, datos?.campos, datos?.hallazgos);
  }
  return datos as T;
}

const q = (parametros: Record<string, string | number | undefined>) => {
  const p = Object.entries(parametros).filter(([, v]) => v !== undefined && v !== '');
  return p.length ? '?' + new URLSearchParams(p.map(([k, v]) => [k, String(v)])).toString() : '';
};

export const api = {
  opciones: () => pedir<Opciones>('GET', '/opciones'),

  bases: () => pedir<BaseDatos[]>('GET', '/bases-datos'),
  crearBase: (b: Omit<BaseDatos, 'id' | 'modoArchivado' | 'fechaInspeccion' | 'fechaRegistro'>) =>
    pedir<BaseDatos>('POST', '/bases-datos', b),
  eliminarBase: (id: number) => pedir<void>('DELETE', `/bases-datos/${id}`),
  inspeccionar: (id: number) => pedir<Inspeccion>('POST', `/bases-datos/${id}/inspeccion`),

  estrategias: () => pedir<Estrategia[]>('GET', '/estrategias'),
  estrategia: (id: number) => pedir<Estrategia>('GET', `/estrategias/${id}`),
  crearEstrategia: (s: EstrategiaSolicitud) => pedir<Estrategia>('POST', '/estrategias', s),
  actualizarEstrategia: (id: number, s: EstrategiaSolicitud) => pedir<Estrategia>('PUT', `/estrategias/${id}`, s),
  activarEstrategia: (id: number, activa: boolean) =>
    pedir<Estrategia>('POST', `/estrategias/${id}/${activa ? 'activar' : 'desactivar'}`),
  validar: (id: number) => pedir<Validacion>('POST', `/estrategias/${id}/validacion`),

  generarScript: (id: number) => pedir<Generacion>('POST', `/estrategias/${id}/scripts`),
  scripts: (estrategiaId: number) => pedir<Script[]>('GET', `/estrategias/${estrategiaId}/scripts`),
  aprobar: (scriptId: number, aprobadoPor: string, hashContenido: string, comentario: string) =>
    pedir<{ script: Script; hallazgos: Mensaje[] }>('POST', `/scripts/${scriptId}/aprobacion`,
      { aprobadoPor, hashContenido, comentario }),
  rechazar: (scriptId: number, rechazadoPor: string, motivo: string) =>
    pedir<Script>('POST', `/scripts/${scriptId}/rechazo`, { rechazadoPor, motivo }),

  programaciones: () => pedir<EstadoProgramacion[]>('GET', '/programacion'),
  proximas: (estrategiaId: number) => pedir<string[]>('GET', `/estrategias/${estrategiaId}/programacion/proximas`),
  activarProgramacion: (estrategiaId: number, activa: boolean) =>
    pedir<EstadoProgramacion>('POST', `/estrategias/${estrategiaId}/programacion/${activa ? 'activar' : 'desactivar'}`),

  ejecutar: (estrategiaId: number) => pedir<Ejecucion>('POST', `/estrategias/${estrategiaId}/ejecuciones`),
  ejecuciones: (estrategiaId?: number) => pedir<EjecucionResumen[]>('GET', '/ejecuciones' + q({ estrategiaId })),
  ejecucion: (id: number) => pedir<Ejecucion>('GET', `/ejecuciones/${id}`),

  alertas: (filtro: { estado?: string; tipo?: string; estrategiaId?: number }) =>
    pedir<Alerta[]>('GET', '/alertas' + q(filtro)),
  resumenAlertas: () => pedir<Record<TipoMensaje, number>>('GET', '/alertas/resumen'),
  accionAlerta: (id: number, accion: 'atender' | 'descartar' | 'aplicar', atendidaPor: string, comentario: string) =>
    pedir<Alerta>('POST', `/alertas/${id}/${accion}`, { atendidaPor, comentario }),
  revisarAlertas: () => pedir<{ abiertas: number; cerradas: number; vigentes: number }>('POST', '/alertas/revision'),
};
