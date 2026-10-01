package com.example.respaldos.basedatos;

import com.example.respaldos.comun.Mensaje;
import com.example.respaldos.infraestructura.CatalogoOracle.DatafileInfo;
import com.example.respaldos.infraestructura.CatalogoOracle.InfoBaseDatos;
import com.example.respaldos.infraestructura.CatalogoOracle.TablespaceInfo;
import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.ModoArchivado;
import com.example.respaldos.modelo.TipoMensaje;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReglasInspeccionTest {

    private final ReglasInspeccion reglas = new ReglasInspeccion();

    private static final List<TablespaceInfo> TABLESPACES = List.of(
            new TablespaceInfo("XEPDB1", "SYSTEM", "PERMANENT", "ONLINE"),
            new TablespaceInfo("XEPDB1", "TEMP", "TEMPORARY", "ONLINE"),
            new TablespaceInfo("XEPDB1", "USERS", "PERMANENT", "ONLINE"));
    private static final List<DatafileInfo> DATAFILES = List.of(
            new DatafileInfo(9, "/opt/oracle/oradata/XE/XEPDB1/system01.dbf", "SYSTEM", 272L * 1024 * 1024),
            new DatafileInfo(12, "/opt/oracle/oradata/XE/XEPDB1/users01.dbf", "USERS", 10L * 1024 * 1024));

    @Test
    void noarchivelogGeneraUnaAdvertenciaYNingunaRecomendacionDeArchivelog() {
        List<Mensaje> m = reglas.evaluar(base(Ambiente.PRUEBAS), info(ModoArchivado.NOARCHIVELOG),
                TABLESPACES, DATAFILES, false);

        assertThat(m).extracting(Mensaje::tipo, Mensaje::codigo).contains(
                tuple(TipoMensaje.ADVERTENCIA, "NOARCHIVELOG"),
                tuple(TipoMensaje.INFORMATIVO, "NOARCHIVELOG_RESPALDO_CERRADO"));
        assertThat(m).extracting(Mensaje::codigo).doesNotContain("INCLUIR_ARCHIVELOG", "MODO_ARCHIVELOG");
    }

    @Test
    void archivelogSinEstrategiaQueLoRespaldeGeneraUnaRecomendacion() {
        List<Mensaje> m = reglas.evaluar(base(Ambiente.PRUEBAS), info(ModoArchivado.ARCHIVELOG),
                TABLESPACES, DATAFILES, false);

        assertThat(m).extracting(Mensaje::tipo, Mensaje::codigo).contains(
                tuple(TipoMensaje.INFORMATIVO, "MODO_ARCHIVELOG"),
                tuple(TipoMensaje.RECOMENDACION, "INCLUIR_ARCHIVELOG"));
        assertThat(m).noneMatch(x -> x.tipo() == TipoMensaje.ADVERTENCIA);
    }

    @Test
    void archivelogYaCubiertoEsSoloInformativo() {
        List<Mensaje> m = reglas.evaluar(base(Ambiente.PRUEBAS), info(ModoArchivado.ARCHIVELOG),
                TABLESPACES, DATAFILES, true);

        assertThat(m).extracting(Mensaje::codigo).contains("ARCHIVELOG_CUBIERTO").doesNotContain("INCLUIR_ARCHIVELOG");
        assertThat(m).noneMatch(x -> x.tipo() == TipoMensaje.RECOMENDACION);
    }

    @Test
    void produccionYServicioDistintoGeneranAdvertencias() {
        BaseDatos base = base(Ambiente.PRODUCCION);
        base.setServicio("OTRAPDB");

        List<Mensaje> m = reglas.evaluar(base, info(ModoArchivado.ARCHIVELOG), TABLESPACES, DATAFILES, true);

        assertThat(m).filteredOn(x -> x.tipo() == TipoMensaje.ADVERTENCIA)
                .extracting(Mensaje::codigo).containsExactly("AMBIENTE_PRODUCCION", "SERVICIO_DISTINTO");
    }

    @Test
    void tablespacesOfflineYSoloLecturaSeReportanConNombreRman() {
        List<TablespaceInfo> tablespaces = List.of(
                new TablespaceInfo("XEPDB1", "HIST", "PERMANENT", "READ ONLY"),
                new TablespaceInfo("XEPDB1", "APP", "PERMANENT", "OFFLINE"));

        List<Mensaje> m = reglas.evaluar(base(Ambiente.PRUEBAS), info(ModoArchivado.ARCHIVELOG),
                tablespaces, DATAFILES, true);

        assertThat(m).anySatisfy(x -> {
            assertThat(x.tipo()).isEqualTo(TipoMensaje.ADVERTENCIA);
            assertThat(x.texto()).contains("XEPDB1:APP");
        });
        assertThat(m).anySatisfy(x -> {
            assertThat(x.codigo()).isEqualTo("TABLESPACE_SOLO_LECTURA");
            assertThat(x.texto()).contains("XEPDB1:HIST");
        });
    }

    @Test
    void resumenDelCatalogo() {
        List<Mensaje> m = reglas.evaluar(base(Ambiente.PRUEBAS), info(ModoArchivado.ARCHIVELOG),
                TABLESPACES, DATAFILES, true);

        assertThat(m).filteredOn(x -> x.codigo().equals("CATALOGO")).singleElement()
                .extracting(Mensaje::texto).asString()
                .contains("3 tablespaces (1 temporales").contains("2 datafiles con 282 MB");
    }

    private static BaseDatos base(Ambiente ambiente) {
        BaseDatos base = new BaseDatos();
        base.setNombre("XE");
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(ambiente);
        return base;
    }

    private static InfoBaseDatos info(ModoArchivado modo) {
        return new InfoBaseDatos("XE", modo, "READ WRITE", "XEPDB1");
    }
}
