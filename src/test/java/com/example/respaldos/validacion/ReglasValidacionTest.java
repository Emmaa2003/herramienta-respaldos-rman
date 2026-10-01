package com.example.respaldos.validacion;

import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.InfoBaseDatos;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.infraestructura.EspacioDisco;
import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
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
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Reglas de validacion sin Docker ni Oracle: el contexto se arma a mano. */
class ReglasValidacionTest {

    private static final long MB = 1024L * 1024;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 1, 12, 0);

    private final ReglasValidacion reglas = new ReglasValidacion();

    private static final List<TablespaceInfo> TABLESPACES = List.of(
            new TablespaceInfo("XEPDB1", "SYSTEM", "PERMANENT", "ONLINE"),
            new TablespaceInfo("XEPDB1", "TEMP", "TEMPORARY", "ONLINE"),
            new TablespaceInfo("XEPDB1", "USERS", "PERMANENT", "ONLINE"));
    private static final List<DatafileInfo> DATAFILES = List.of(
            new DatafileInfo(9, "/opt/oracle/oradata/XE/XEPDB1/system01.dbf", "SYSTEM", 300 * MB),
            new DatafileInfo(12, "/opt/oracle/oradata/XE/XEPDB1/users01.dbf", "USERS", 10 * MB));
    /** Destino con 100 GB libres en otro disco distinto al de los datos. */
    private static final EspacioDisco DESTINO_OTRO_DISCO =
            new EspacioDisco("/dev/sde", "/respaldos", 200 * 1024 * MB, 100 * 1024 * MB, 100 * 1024 * MB);
    private static final EspacioDisco DISCO_DATOS =
            new EspacioDisco("/dev/sdd", "/opt/oracle/oradata", 500 * 1024 * MB, 100 * 1024 * MB, 400 * 1024 * MB);

    @Test
    void estrategiaBienConfiguradaEsValidaYSinAdvertencias() {
        Estrategia e = estrategia(TipoRespaldo.INCREMENTAL_NIVEL_0,
                TipoElemento.BASE_DATOS, TipoElemento.ARCHIVELOG);
        e.setDiasRetencion(14);
        diaria(e, LocalTime.of(23, 0));

        List<Hallazgo> h = reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG));

        assertThat(h).noneMatch(Hallazgo::bloqueante);
        assertThat(h).noneMatch(x -> x.tipo() == TipoMensaje.ADVERTENCIA);
        assertThat(h).extracting(Hallazgo::codigo).contains("ESPACIO_DISPONIBLE");
    }

    @Test
    void sinElementosEsConfiguracionIncompletaBloqueante() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO);

        assertThat(reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG)))
                .filteredOn(Hallazgo::bloqueante).extracting(Hallazgo::codigo).containsExactly("SIN_ELEMENTOS");
    }

    @Test
    void objetosQueNoExistenOTemporalesOEnOtroPdbBloquean() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.CONTROLFILE);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "NO_EXISTE"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "TEMP"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "OTRAPDB:USERS"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "XEPDB1:USERS"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.DATAFILE, "99"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.DATAFILE, "12"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.DATAFILE, "/opt/oracle/oradata/XE/XEPDB1/system01.dbf"));

        assertThat(reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG)))
                .filteredOn(Hallazgo::bloqueante).extracting(Hallazgo::codigo)
                .containsExactlyInAnyOrder("TABLESPACE_INEXISTENTE", "TABLESPACE_TEMPORAL",
                        "OBJETO_OTRO_PDB", "DATAFILE_INEXISTENTE");
    }

    @Test
    void noarchivelogConBaseAbiertaYArchivelogBloqueaSinCambiarNada() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS, TipoElemento.ARCHIVELOG);
        ContextoValidacion c = contexto(e, ModoArchivado.NOARCHIVELOG);

        List<Hallazgo> h = reglas.evaluar(c);

        assertThat(h).extracting(Hallazgo::codigo, Hallazgo::bloqueante).contains(
                tuple("NOARCHIVELOG", false),
                tuple("ARCHIVELOG_SIN_ARCHIVADO", true),
                tuple("NOARCHIVELOG_BASE_ABIERTA", true));
        assertThat(h).extracting(Hallazgo::codigo).doesNotContain("INCLUIR_ARCHIVELOG");
    }

    @Test
    void noarchivelogConBaseMontadaPermiteRespaldarDatafiles() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        ContextoValidacion abierta = contexto(e, ModoArchivado.NOARCHIVELOG);
        ContextoValidacion montada = new ContextoValidacion(e, true,
                new InfoBaseDatos("XE", ModoArchivado.NOARCHIVELOG, "MOUNTED", "XEPDB1"), null,
                TABLESPACES, DATAFILES, DESTINO_OTRO_DISCO, null, DISCO_DATOS, false, false, AHORA);

        assertThat(reglas.evaluar(abierta)).extracting(Hallazgo::codigo).contains("NOARCHIVELOG_BASE_ABIERTA");
        assertThat(reglas.evaluar(montada)).noneMatch(Hallazgo::bloqueante);
    }

    @Test
    void archivelogSeRecomiendaSegunPrioridadYOtrasEstrategias() {
        Estrategia media = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        media.setPrioridad(Prioridad.MEDIA);
        assertThat(codigos(contexto(media, ModoArchivado.ARCHIVELOG))).contains("INCLUIR_ARCHIVELOG");
        assertThat(codigos(conOtraEstrategiaConArchivelog(media))).doesNotContain("INCLUIR_ARCHIVELOG");

        // Con prioridad ALTA se recomienda aunque otra estrategia ya los respalde.
        Estrategia alta = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        assertThat(reglas.evaluar(conOtraEstrategiaConArchivelog(alta)))
                .anySatisfy(x -> {
                    assertThat(x.codigo()).isEqualTo("INCLUIR_ARCHIVELOG");
                    assertThat(x.tipo()).isEqualTo(TipoMensaje.RECOMENDACION);
                    assertThat(x.texto()).contains("prioridad ALTA");
                });
    }

    @Test
    void contenedorDetenidoODestinoInexistenteBloquean() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        ContextoValidacion detenido = new ContextoValidacion(e, false, null, null, List.of(), List.of(),
                null, null, null, false, false, AHORA);
        ContextoValidacion sinDestino = new ContextoValidacion(e, true,
                new InfoBaseDatos("XE", ModoArchivado.ARCHIVELOG, "READ WRITE", "XEPDB1"), null,
                TABLESPACES, DATAFILES, null, "df: /no/existe: No such file or directory", null, false, false, AHORA);

        assertThat(reglas.evaluar(detenido)).filteredOn(Hallazgo::bloqueante)
                .extracting(Hallazgo::codigo).containsExactly("CONTENEDOR_DETENIDO");
        assertThat(reglas.evaluar(sinDestino)).filteredOn(Hallazgo::bloqueante)
                .extracting(Hallazgo::codigo).containsExactly("DESTINO_INEXISTENTE");
    }

    @Test
    void espacioInsuficienteYMismoDisco() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        EspacioDisco casiLleno = new EspacioDisco("/dev/sdd", "/opt/oracle/oradata", 1000 * MB, 995 * MB, 5 * MB);
        ContextoValidacion c = new ContextoValidacion(e, true,
                new InfoBaseDatos("XE", ModoArchivado.ARCHIVELOG, "READ WRITE", "XEPDB1"), null,
                TABLESPACES, DATAFILES, casiLleno, null, DISCO_DATOS, false, false, AHORA);

        assertThat(reglas.evaluar(c)).extracting(Hallazgo::tipo, Hallazgo::codigo).contains(
                tuple(TipoMensaje.ADVERTENCIA, "ESPACIO_BAJO"),
                tuple(TipoMensaje.ADVERTENCIA, "ESPACIO_INSUFICIENTE"),
                tuple(TipoMensaje.RECOMENDACION, "DESTINO_MISMO_DISCO"));
    }

    @Test
    void nivel1SinNivel0PrevioYRecuperacion() {
        Estrategia e = estrategia(TipoRespaldo.INCREMENTAL_NIVEL_1_ACUMULATIVO, TipoElemento.BASE_DATOS);

        List<Hallazgo> h = reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG));

        assertThat(h).extracting(Hallazgo::codigo).contains("SIN_NIVEL_0", "RECUPERACION_INCREMENTAL");
        assertThat(h).filteredOn(x -> x.codigo().equals("RECUPERACION_INCREMENTAL"))
                .singleElement().extracting(Hallazgo::texto).asString().contains("ultimo nivel 1 acumulativo");
    }

    @Test
    void incrementalSinDatafilesNoTieneEfecto() {
        Estrategia e = estrategia(TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL, TipoElemento.ARCHIVELOG);

        assertThat(codigos(contexto(e, ModoArchivado.ARCHIVELOG)))
                .contains("NIVEL_SIN_EFECTO").doesNotContain("SIN_NIVEL_0");
    }

    @Test
    void recomendacionesDeControlfileYRetencion() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.ARCHIVELOG);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "USERS"));

        assertThat(reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG)))
                .filteredOn(x -> x.tipo() == TipoMensaje.RECOMENDACION)
                .extracting(Hallazgo::codigo).contains("INCLUIR_CONTROLFILE", "DEFINIR_RETENCION");
    }

    @Test
    void sinProgramacionEInactivaSonAdvertenciasNoBloqueantes() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        e.setActiva(false);

        assertThat(reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG)))
                .filteredOn(x -> x.codigo().equals("SIN_PROGRAMACION") || x.codigo().equals("ESTRATEGIA_INACTIVA"))
                .hasSize(2).noneMatch(Hallazgo::bloqueante);
    }

    @Test
    void horaFueraDeVentanaQueCruzaMedianoche() {
        Estrategia e = estrategia(TipoRespaldo.INCREMENTAL_NIVEL_0, TipoElemento.BASE_DATOS);
        diaria(e, LocalTime.of(12, 0)).setVentanaInicio(LocalTime.of(22, 0));
        e.getProgramacion().setVentanaFin(LocalTime.of(5, 0));
        assertThat(codigos(contexto(e, ModoArchivado.ARCHIVELOG))).contains("FUERA_DE_VENTANA");

        e.getProgramacion().setHora(LocalTime.of(2, 0));
        assertThat(codigos(contexto(e, ModoArchivado.ARCHIVELOG))).doesNotContain("FUERA_DE_VENTANA");
    }

    @Test
    void cadaNHorasCuentaLosHorariosFueraDeVentana() {
        Estrategia e = estrategia(TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL, TipoElemento.BASE_DATOS);
        Programacion p = diaria(e, LocalTime.of(0, 0));
        p.setFrecuencia(Frecuencia.CADA_N_HORAS);
        p.setIntervalo(6);
        p.setVentanaInicio(LocalTime.of(0, 0));
        p.setVentanaFin(LocalTime.of(7, 0));

        assertThat(reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG)))
                .filteredOn(x -> x.codigo().equals("FUERA_DE_VENTANA")).singleElement()
                .extracting(Hallazgo::texto).asString().startsWith("2 de 4 horarios");
    }

    @Test
    void frecuenciaInsuficienteParaLaPrioridadYCompletosFrecuentes() {
        Estrategia semanal = estrategia(TipoRespaldo.INCREMENTAL_NIVEL_0, TipoElemento.BASE_DATOS);
        Programacion p = diaria(semanal, LocalTime.of(23, 0));
        p.setFrecuencia(Frecuencia.SEMANAL);
        p.setDiasSemana(EnumSet.of(DiaSemana.LUN, DiaSemana.VIE));
        assertThat(reglas.evaluar(contexto(semanal, ModoArchivado.ARCHIVELOG)))
                .filteredOn(x -> x.codigo().equals("FRECUENCIA_INSUFICIENTE")).singleElement()
                .extracting(Hallazgo::texto).asString().contains("hasta 96 h").contains("como maximo 24 h");

        Estrategia cadaHora = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        Programacion q = diaria(cadaHora, LocalTime.of(0, 0));
        q.setFrecuencia(Frecuencia.CADA_N_HORAS);
        q.setIntervalo(4);
        assertThat(codigos(contexto(cadaHora, ModoArchivado.ARCHIVELOG)))
                .contains("COMPLETOS_FRECUENTES").doesNotContain("FRECUENCIA_INSUFICIENTE");
    }

    @Test
    void unaVezVencidaNoSeRepite() {
        Estrategia e = estrategia(TipoRespaldo.COMPLETO, TipoElemento.BASE_DATOS);
        Programacion p = diaria(e, LocalTime.of(8, 0));
        p.setFrecuencia(Frecuencia.UNA_VEZ);
        p.setFechaInicio(AHORA.toLocalDate().minusDays(1));

        assertThat(codigos(contexto(e, ModoArchivado.ARCHIVELOG)))
                .contains("PROGRAMACION_VENCIDA", "SIN_REPETICION");
    }

    @Test
    void losBloqueantesVanPrimeroYLuegoAdvertenciasRecomendacionesEInformativos() {
        Estrategia e = estrategia(TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL, TipoElemento.BASE_DATOS);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "NO_EXISTE"));

        List<Hallazgo> h = reglas.evaluar(contexto(e, ModoArchivado.ARCHIVELOG));

        assertThat(h.getFirst().bloqueante()).isTrue();
        List<TipoMensaje> tipos = h.stream().filter(x -> !x.bloqueante()).map(Hallazgo::tipo).toList();
        assertThat(tipos).isSortedAccordingTo((a, b) -> Integer.compare(rango(a), rango(b)));
    }

    // --- utilidades ---

    private List<String> codigos(ContextoValidacion c) {
        return reglas.evaluar(c).stream().map(Hallazgo::codigo).toList();
    }

    private static int rango(TipoMensaje t) {
        return switch (t) {
            case ADVERTENCIA -> 0;
            case RECOMENDACION -> 1;
            case INFORMATIVO -> 2;
        };
    }

    private static Estrategia estrategia(TipoRespaldo tipo, TipoElemento... elementos) {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE");
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        Estrategia e = new Estrategia();
        e.setNombre("Prueba");
        e.setBaseDatos(base);
        e.setResponsable("admin");
        e.setPrioridad(Prioridad.ALTA);
        e.setActiva(true);
        e.setTipoRespaldo(tipo);
        e.setRutaDestino("/respaldos");
        for (TipoElemento t : elementos) {
            e.agregarElemento(new EstrategiaElemento(t, null));
        }
        return e;
    }

    private static Programacion diaria(Estrategia e, LocalTime hora) {
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.of(2026, 10, 1));
        p.setHora(hora);
        p.setFrecuencia(Frecuencia.DIARIA);
        e.asignarProgramacion(p);
        return p;
    }

    private static ContextoValidacion contexto(Estrategia e, ModoArchivado modo) {
        return new ContextoValidacion(e, true, new InfoBaseDatos("XE", modo, "READ WRITE", "XEPDB1"), null,
                TABLESPACES, DATAFILES, DESTINO_OTRO_DISCO, null, DISCO_DATOS, false, false, AHORA);
    }

    private static ContextoValidacion conOtraEstrategiaConArchivelog(Estrategia e) {
        return new ContextoValidacion(e, true,
                new InfoBaseDatos("XE", ModoArchivado.ARCHIVELOG, "READ WRITE", "XEPDB1"), null,
                TABLESPACES, DATAFILES, DESTINO_OTRO_DISCO, null, DISCO_DATOS, false, true, AHORA);
    }
}
