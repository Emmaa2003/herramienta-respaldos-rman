package com.example.respaldos.validacion;

import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.InfoBaseDatos;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.infraestructura.EspacioDisco;
import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.DiaSemana;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.ModoArchivado;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoMensaje;
import com.example.respaldos.modelo.TipoRespaldo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Reglas que revisan una estrategia antes de generar su script.
 * <p>
 * Bloqueantes: la configuracion esta incompleta o el script fallaria (objetos que no
 * existen, destino inexistente, NOARCHIVELOG con la base abierta...). El resto son
 * advertencias, recomendaciones o informacion: no impiden continuar.
 * <p>
 * Las reglas solo describen; nunca cambian la estrategia ni la base.
 */
@Component
public class ReglasValidacion {

    private static final Set<TipoElemento> DE_DATAFILES =
            EnumSet.of(TipoElemento.BASE_DATOS, TipoElemento.TABLESPACE, TipoElemento.DATAFILE);
    private static final long MB = 1024L * 1024;
    private static final long GB = MB * 1024;

    public List<Hallazgo> evaluar(ContextoValidacion c) {
        Estrategia e = c.estrategia();
        Set<TipoElemento> tipos = e.getElementos().stream()
                .map(EstrategiaElemento::getTipoElemento)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TipoElemento.class)));
        boolean conDatafiles = tipos.stream().anyMatch(DE_DATAFILES::contains);
        List<Hallazgo> h = new ArrayList<>();

        general(e, tipos, h);
        if (!c.contenedorActivo()) {
            h.add(Hallazgo.bloqueante("CONTENEDOR_DETENIDO", "El contenedor '"
                    + e.getBaseDatos().getContenedor() + "' no esta en ejecucion: no se puede verificar la "
                    + "estrategia contra la base ni ejecutar el respaldo."));
        } else if (c.info() == null) {
            h.add(Hallazgo.bloqueante("CATALOGO_NO_DISPONIBLE",
                    "No se pudo leer el catalogo de la base: " + c.errorCatalogo()));
        } else {
            objetos(c, tipos, h);
            archivado(c, tipos, conDatafiles, h);
            destino(c, tipos, conDatafiles, h);
        }
        como(c, tipos, conDatafiles, h);
        cuando(e, c.ahora(), h);

        h.sort(Comparator.comparing((Hallazgo x) -> !x.bloqueante()).thenComparing(x -> orden(x.tipo())));
        return h;
    }

    // --- Informacion general -------------------------------------------------------------

    private static void general(Estrategia e, Set<TipoElemento> tipos, List<Hallazgo> h) {
        if (tipos.isEmpty()) {
            h.add(Hallazgo.bloqueante("SIN_ELEMENTOS",
                    "Configuracion incompleta: la estrategia no indica que respaldar."));
        }
        if (e.getBaseDatos().getAmbiente() == Ambiente.PRODUCCION) {
            h.add(Hallazgo.advertencia("AMBIENTE_PRODUCCION",
                    "La base esta registrada como PRODUCCION. Pruebe el script primero en un ambiente "
                            + "de desarrollo o pruebas."));
        }
        if (!e.isActiva()) {
            h.add(Hallazgo.advertencia("ESTRATEGIA_INACTIVA",
                    "La estrategia esta inactiva: no se ejecutara de forma programada hasta activarla."));
        }
    }

    // --- QUE: los objetos existen en la base ---------------------------------------------

    private static void objetos(ContextoValidacion c, Set<TipoElemento> tipos, List<Hallazgo> h) {
        Estrategia e = c.estrategia();
        String pdb = c.info().pdb();
        boolean conObjetos = tipos.contains(TipoElemento.TABLESPACE) || tipos.contains(TipoElemento.DATAFILE);

        if (conObjetos && !e.getBaseDatos().getServicio().equalsIgnoreCase(pdb)) {
            h.add(Hallazgo.bloqueante("SERVICIO_DISTINTO", "La base registrada indica el servicio '"
                    + e.getBaseDatos().getServicio() + "', pero el catalogo disponible es el de " + pdb
                    + ". No se pueden verificar los tablespaces ni los datafiles elegidos."));
            return;
        }

        for (EstrategiaElemento el : e.getElementos()) {
            if (el.getTipoElemento() == TipoElemento.TABLESPACE) {
                revisarTablespace(el.getNombreObjeto(), c, h);
            } else if (el.getTipoElemento() == TipoElemento.DATAFILE
                    && buscarDatafile(el.getNombreObjeto(), c.datafiles()).isEmpty()) {
                h.add(Hallazgo.bloqueante("DATAFILE_INEXISTENTE", "El datafile '" + el.getNombreObjeto()
                        + "' no esta entre los datafiles de " + pdb + "."));
            }
        }

        if (tipos.contains(TipoElemento.BASE_DATOS) && conObjetos) {
            h.add(Hallazgo.advertencia("ELEMENTOS_REDUNDANTES",
                    "La estrategia respalda la base completa y ademas tablespaces o datafiles que ya "
                            + "estan incluidos en ella: se copiarian dos veces."));
        }
    }

    private static void revisarTablespace(String nombre, ContextoValidacion c, List<Hallazgo> h) {
        String pdb = c.info().pdb();
        String soloNombre = nombre;
        int dosPuntos = nombre.indexOf(':');
        if (dosPuntos >= 0) {
            String pdbIndicado = nombre.substring(0, dosPuntos);
            soloNombre = nombre.substring(dosPuntos + 1);
            if (!pdbIndicado.equalsIgnoreCase(pdb)) {
                h.add(Hallazgo.bloqueante("OBJETO_OTRO_PDB", "El tablespace " + nombre
                        + " pertenece a otro contenedor; solo se pueden elegir tablespaces de " + pdb + "."));
                return;
            }
        }
        String buscado = soloNombre;
        Optional<TablespaceInfo> ts = c.tablespaces().stream().filter(t -> t.nombre().equals(buscado)).findFirst();
        if (ts.isEmpty()) {
            h.add(Hallazgo.bloqueante("TABLESPACE_INEXISTENTE",
                    "El tablespace " + soloNombre + " no existe en " + pdb + "."));
        } else if ("TEMPORARY".equals(ts.get().contenido())) {
            h.add(Hallazgo.bloqueante("TABLESPACE_TEMPORAL", "El tablespace " + soloNombre
                    + " es temporal: RMAN no respalda tablespaces temporales."));
        } else if ("OFFLINE".equals(ts.get().estado())) {
            h.add(Hallazgo.advertencia("TABLESPACE_OFFLINE", "El tablespace " + soloNombre
                    + " esta OFFLINE: revise que el respaldo lo incluya como se espera."));
        }
    }

    // --- Modo de archivado -----------------------------------------------------------------

    private static void archivado(ContextoValidacion c, Set<TipoElemento> tipos, boolean conDatafiles,
                                  List<Hallazgo> h) {
        InfoBaseDatos info = c.info();
        Estrategia e = c.estrategia();

        if (info.modoArchivado() == ModoArchivado.NOARCHIVELOG) {
            h.add(Hallazgo.advertencia("NOARCHIVELOG",
                    "La base de datos se encuentra en modo NOARCHIVELOG. Las posibilidades de recuperacion "
                            + "son mas limitadas. Revise la estrategia de respaldo y los requerimientos de "
                            + "recuperacion antes de continuar."));
            if (tipos.contains(TipoElemento.ARCHIVELOG)) {
                h.add(Hallazgo.bloqueante("ARCHIVELOG_SIN_ARCHIVADO",
                        "La base esta en NOARCHIVELOG y no genera archived redo logs: quite ARCHIVELOG "
                                + "de la estrategia."));
            }
            if (conDatafiles && info.abierta()) {
                h.add(Hallazgo.bloqueante("NOARCHIVELOG_BASE_ABIERTA",
                        "En NOARCHIVELOG, RMAN no puede respaldar datafiles con la base abierta (ORA-19602); "
                                + "solo con la base en MOUNT. La aplicacion no cierra la base ni cambia su "
                                + "modo de archivado: esa decision corresponde al administrador."));
            }
            return;
        }

        if (!tipos.contains(TipoElemento.ARCHIVELOG)) {
            if (e.getPrioridad() == Prioridad.ALTA) {
                h.add(Hallazgo.recomendacion("INCLUIR_ARCHIVELOG",
                        "La estrategia tiene prioridad ALTA y la base esta en ARCHIVELOG. Considere incorporar "
                                + "el respaldo periodico de los archived redo logs para poder recuperar hasta "
                                + "un punto en el tiempo."));
            } else if (!c.otraEstrategiaConArchivelog()) {
                h.add(Hallazgo.recomendacion("INCLUIR_ARCHIVELOG",
                        "La base de datos se encuentra en modo ARCHIVELOG. Considere incorporar el respaldo "
                                + "periodico de los archived redo logs dentro de la estrategia para mejorar "
                                + "las posibilidades de recuperacion."));
            }
        }
    }

    // --- Destino y espacio -----------------------------------------------------------------

    private static void destino(ContextoValidacion c, Set<TipoElemento> tipos, boolean conDatafiles,
                                List<Hallazgo> h) {
        String ruta = c.estrategia().getRutaDestino();
        EspacioDisco destino = c.espacioDestino();
        if (destino == null) {
            h.add(Hallazgo.bloqueante("DESTINO_INEXISTENTE", "No se pudo usar la ruta de destino " + ruta
                    + " en el contenedor: " + c.errorDestino()));
            return;
        }

        if (destino.disponiblesBytes() < destino.totalBytes() / 10) {
            h.add(Hallazgo.advertencia("ESPACIO_BAJO", "El destino tiene menos del 10% libre ("
                    + gb(destino.disponiblesBytes()) + " de " + gb(destino.totalBytes()) + ")."));
        }

        if (conDatafiles) {
            long estimado = bytesEstimados(c, tipos);
            String detalle = gb(estimado) + " estimados como maximo (tamano de los datafiles de "
                    + c.info().pdb() + "; RMAN solo copia los bloques usados"
                    + (tipos.contains(TipoElemento.BASE_DATOS)
                    ? ", aunque BASE_DATOS incluye tambien la raiz del CDB" : "") + ")";
            if (estimado > destino.disponiblesBytes()) {
                h.add(Hallazgo.advertencia("ESPACIO_INSUFICIENTE", "Puede faltar espacio en el destino: "
                        + detalle + " y " + gb(destino.disponiblesBytes()) + " disponibles."));
            } else {
                h.add(Hallazgo.informativo("ESPACIO_DISPONIBLE", "Espacio en el destino: "
                        + gb(destino.disponiblesBytes()) + " disponibles; " + detalle + "."));
            }
        }

        EspacioDisco datos = c.espacioDatos();
        if (datos != null && datos.sistemaArchivos().equals(destino.sistemaArchivos())
                && datos.puntoMontaje().equals(destino.puntoMontaje())) {
            h.add(Hallazgo.recomendacion("DESTINO_MISMO_DISCO", "El destino " + ruta + " esta en el mismo "
                    + "sistema de archivos que los datafiles (" + destino.puntoMontaje() + "). Si se pierde ese "
                    + "disco se pierden la base y sus respaldos: considere un destino en otro dispositivo."));
        }
    }

    /** Suma de los datafiles que cubre la estrategia: cota superior del tamano del respaldo. */
    private static long bytesEstimados(ContextoValidacion c, Set<TipoElemento> tipos) {
        if (tipos.contains(TipoElemento.BASE_DATOS)) {
            return c.datafiles().stream().mapToLong(DatafileInfo::bytes).sum();
        }
        Set<Integer> numeros = new LinkedHashSet<>();
        for (EstrategiaElemento el : c.estrategia().getElementos()) {
            if (el.getTipoElemento() == TipoElemento.TABLESPACE) {
                String nombre = el.getNombreObjeto().substring(el.getNombreObjeto().indexOf(':') + 1);
                c.datafiles().stream().filter(d -> d.tablespace().equals(nombre))
                        .forEach(d -> numeros.add(d.numero()));
            } else if (el.getTipoElemento() == TipoElemento.DATAFILE) {
                buscarDatafile(el.getNombreObjeto(), c.datafiles()).ifPresent(d -> numeros.add(d.numero()));
            }
        }
        return c.datafiles().stream().filter(d -> numeros.contains(d.numero())).mapToLong(DatafileInfo::bytes).sum();
    }

    // --- COMO ----------------------------------------------------------------------------------

    private static void como(ContextoValidacion c, Set<TipoElemento> tipos, boolean conDatafiles,
                             List<Hallazgo> h) {
        Estrategia e = c.estrategia();
        TipoRespaldo tipo = e.getTipoRespaldo();

        if (tipo.esIncremental() && !conDatafiles && !tipos.isEmpty()) {
            h.add(Hallazgo.advertencia("NIVEL_SIN_EFECTO",
                    "El respaldo incremental solo aplica a datafiles. Esta estrategia no respalda datafiles: "
                            + "control file, SPFILE y archived redo logs se respaldan siempre completos."));
        }
        boolean nivel1 = tipo == TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL
                || tipo == TipoRespaldo.INCREMENTAL_NIVEL_1_ACUMULATIVO;
        if (nivel1 && conDatafiles) {
            if (!c.hayNivel0Exitoso()) {
                h.add(Hallazgo.advertencia("SIN_NIVEL_0",
                        "No hay un respaldo incremental nivel 0 exitoso registrado para esta base. Si RMAN no "
                                + "encuentra un nivel 0, el primer nivel 1 se hace como nivel 0 (mas grande y "
                                + "lento). Un respaldo COMPLETO no sirve como base de los incrementales."));
            }
            h.add(Hallazgo.informativo("RECUPERACION_INCREMENTAL",
                    tipo == TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL
                            ? "Diferencial: para recuperar se aplican el nivel 0 y todos los nivel 1 posteriores."
                            : "Acumulativo: para recuperar basta el nivel 0 y el ultimo nivel 1 acumulativo."));
        }

        if (conDatafiles && !tipos.contains(TipoElemento.BASE_DATOS) && !tipos.contains(TipoElemento.CONTROLFILE)) {
            h.add(Hallazgo.recomendacion("INCLUIR_CONTROLFILE",
                    "La estrategia respalda tablespaces o datafiles sin el control file. Considere incluirlo: "
                            + "sin un control file respaldado no se puede recuperar ante la perdida total."));
        }
        if (e.getDiasRetencion() == null) {
            h.add(Hallazgo.recomendacion("DEFINIR_RETENCION",
                    "La estrategia no define cuantos dias conservar los respaldos. Considere definir la "
                            + "retencion para controlar el espacio y saber hasta donde se puede recuperar."));
        }
    }

    // --- CUANDO --------------------------------------------------------------------------------

    private static void cuando(Estrategia e, LocalDateTime ahora, List<Hallazgo> h) {
        Programacion p = e.getProgramacion();
        if (p == null) {
            h.add(Hallazgo.advertencia("SIN_PROGRAMACION",
                    "La estrategia no tiene programacion: no se ejecutara automaticamente."));
            return;
        }
        if (!p.isActiva()) {
            h.add(Hallazgo.advertencia("PROGRAMACION_INACTIVA", "La programacion esta desactivada."));
        }
        if (p.getFrecuencia() == Frecuencia.UNA_VEZ) {
            if (p.getFechaInicio().atTime(p.getHora()).isBefore(ahora)) {
                h.add(Hallazgo.advertencia("PROGRAMACION_VENCIDA",
                        "La programacion es de una sola vez y su fecha ya paso: no se volvera a ejecutar."));
            }
            h.add(Hallazgo.advertencia("SIN_REPETICION",
                    "Una programacion de una sola vez no mantiene la proteccion en el tiempo."));
        }

        if (p.getVentanaInicio() != null) {
            Set<LocalTime> horarios = horariosDelDia(p);
            long fuera = horarios.stream().filter(t -> !enVentana(t, p.getVentanaInicio(), p.getVentanaFin())).count();
            if (fuera > 0) {
                h.add(Hallazgo.advertencia("FUERA_DE_VENTANA", (horarios.size() == 1
                        ? "La hora " + p.getHora() + " esta"
                        : fuera + " de " + horarios.size() + " horarios estan")
                        + " fuera de la ventana de respaldo (" + p.getVentanaInicio() + " a "
                        + p.getVentanaFin() + ")."));
            }
        }

        Long separacion = horasEntreEjecuciones(p);
        if (separacion != null) {
            Prioridad prioridad = e.getPrioridad();
            if (separacion > prioridad.getHorasMaximasSinRespaldo()) {
                h.add(Hallazgo.advertencia("FRECUENCIA_INSUFICIENTE", "La programacion deja hasta "
                        + separacion + " h entre respaldos; para prioridad " + prioridad
                        + " se espera como maximo " + prioridad.getHorasMaximasSinRespaldo() + " h."));
            }
            boolean copiaCompleta = e.getTipoRespaldo() == TipoRespaldo.COMPLETO
                    || e.getTipoRespaldo() == TipoRespaldo.INCREMENTAL_NIVEL_0;
            if (copiaCompleta && separacion < 24) {
                h.add(Hallazgo.advertencia("COMPLETOS_FRECUENTES",
                        "Se programan copias completas cada " + separacion + " h. Esto aumenta el espacio y "
                                + "el tiempo de respaldo; considere incrementales nivel 1 entre copias completas."));
            }
        }
    }

    /** Horas del dia en que se ejecuta. Para CADA_N_HORAS recorre una semana: si 24 no es multiplo de N, varian. */
    private static Set<LocalTime> horariosDelDia(Programacion p) {
        Set<LocalTime> horarios = new TreeSet<>();
        if (p.getFrecuencia() != Frecuencia.CADA_N_HORAS) {
            horarios.add(p.getHora());
            return horarios;
        }
        for (int h = 0; h < 24 * 7; h += p.getIntervalo()) {
            horarios.add(p.getHora().plusHours(h));
        }
        return horarios;
    }

    /** La ventana puede cruzar la medianoche (p. ej. 22:00 a 05:00). El fin no esta incluido. */
    static boolean enVentana(LocalTime t, LocalTime inicio, LocalTime fin) {
        return inicio.isBefore(fin)
                ? !t.isBefore(inicio) && t.isBefore(fin)
                : !t.isBefore(inicio) || t.isBefore(fin);
    }

    /** Mayor separacion posible entre dos ejecuciones, en horas; null si no se repite. */
    static Long horasEntreEjecuciones(Programacion p) {
        int n = p.getIntervalo();
        return switch (p.getFrecuencia()) {
            case UNA_VEZ -> null;
            case CADA_N_HORAS -> (long) n;
            case DIARIA -> 24L * n;
            case MENSUAL -> 24L * 31 * n;
            case SEMANAL -> {
                List<Integer> dias = p.getDiasSemana().stream().map(DiaSemana::ordinal).sorted().toList();
                if (dias.isEmpty()) {
                    yield null;
                }
                int mayor = 7 * n - (dias.getLast() - dias.getFirst());
                for (int i = 1; i < dias.size(); i++) {
                    mayor = Math.max(mayor, dias.get(i) - dias.get(i - 1));
                }
                yield 24L * mayor;
            }
        };
    }

    private static Optional<DatafileInfo> buscarDatafile(String nombre, List<DatafileInfo> datafiles) {
        if (nombre.chars().allMatch(Character::isDigit)) {
            int numero = Integer.parseInt(nombre);
            return datafiles.stream().filter(d -> d.numero() == numero).findFirst();
        }
        return datafiles.stream().filter(d -> d.ruta().equals(nombre)).findFirst();
    }

    private static int orden(TipoMensaje tipo) {
        return switch (tipo) {
            case ADVERTENCIA -> 0;
            case RECOMENDACION -> 1;
            case INFORMATIVO -> 2;
        };
    }

    private static String gb(long bytes) {
        return bytes >= GB
                ? String.format(Locale.ROOT, "%.1f GB", bytes / (double) GB)
                : (bytes / MB) + " MB";
    }
}
