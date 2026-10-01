package com.example.respaldos.generador;

import com.example.respaldos.modelo.Ambiente;
import com.example.respaldos.modelo.BaseDatos;
import com.example.respaldos.modelo.Estrategia;
import com.example.respaldos.modelo.EstrategiaElemento;
import com.example.respaldos.modelo.Frecuencia;
import com.example.respaldos.modelo.Prioridad;
import com.example.respaldos.modelo.Programacion;
import com.example.respaldos.modelo.TipoElemento;
import com.example.respaldos.modelo.TipoRespaldo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneradorScriptRmanTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 1, 23, 0);
    private final GeneradorScriptRman generador = new GeneradorScriptRman();

    @Test
    void baseCompletaIncrementalAcumulativoComprimidoConArchivelogControlfileYSpfile() {
        Estrategia e = estrategia(7, TipoRespaldo.INCREMENTAL_NIVEL_1_ACUMULATIVO, true,
                TipoElemento.SPFILE, TipoElemento.CONTROLFILE, TipoElemento.ARCHIVELOG, TipoElemento.BASE_DATOS);

        ScriptGenerado s = generador.generar(e, 1, FECHA);

        assertThat(s.contenido()).isEqualTo("""
                # Estrategia: Produccion diaria (id 7), script version 1
                # Base: XE pruebas (contenedor oracle-xe, servicio XEPDB1)
                # Generado: 2026-10-01 23:00. Revise el script antes de aprobarlo.
                RUN {
                  # QUE: base de datos completa | COMO: incremental nivel 1 acumulativo, comprimido
                  BACKUP AS COMPRESSED BACKUPSET INCREMENTAL LEVEL 1 CUMULATIVE TAG 'EST7_N1A' FORMAT '/opt/oracle/oradata/respaldos/%d_EST7_%T_%U' DATABASE;
                  # QUE: archived redo logs | COMO: completo, comprimido
                  BACKUP AS COMPRESSED BACKUPSET TAG 'EST7_ARC' FORMAT '/opt/oracle/oradata/respaldos/%d_EST7_%T_%U' ARCHIVELOG ALL;
                  # QUE: control file | COMO: completo, comprimido
                  BACKUP AS COMPRESSED BACKUPSET TAG 'EST7_CTL' FORMAT '/opt/oracle/oradata/respaldos/%d_EST7_%T_%U' CURRENT CONTROLFILE;
                  # QUE: SPFILE | COMO: completo, comprimido
                  BACKUP AS COMPRESSED BACKUPSET TAG 'EST7_SPF' FORMAT '/opt/oracle/oradata/respaldos/%d_EST7_%T_%U' SPFILE;
                }
                """);
        assertThat(s.pasos()).extracting(ScriptGenerado.Paso::origen).containsExactly(
                "Estructura",
                "Destino: /opt/oracle/oradata/respaldos",
                "QUE: base de datos completa + COMO: Incremental nivel 1 acumulativo, comprimido",
                "QUE: archived redo logs",
                "QUE: control file",
                "QUE: SPFILE");
    }

    @Test
    void tablespacesConPrefijoDelPdbYDatafilesPorNumeroORuta() {
        Estrategia e = estrategia(3, TipoRespaldo.COMPLETO, false, TipoElemento.CONTROLFILE);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "USERS"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "XEPDB1:SYSAUX"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.DATAFILE, "12"));
        e.agregarElemento(new EstrategiaElemento(TipoElemento.DATAFILE, "/opt/oracle/oradata/XE/XEPDB1/system01.dbf"));

        String script = generador.generar(e, 2, FECHA).contenido();

        assertThat(script).contains("  BACKUP TAG 'EST3_FULL' FORMAT '/opt/oracle/oradata/respaldos/%d_EST3_%T_%U' "
                + "TABLESPACE XEPDB1:USERS, XEPDB1:SYSAUX "
                + "DATAFILE '/opt/oracle/oradata/XE/XEPDB1/system01.dbf', 12;");
        assertThat(script).contains("  BACKUP TAG 'EST3_CTL' FORMAT '/opt/oracle/oradata/respaldos/%d_EST3_%T_%U' "
                + "CURRENT CONTROLFILE;");
        assertThat(script).doesNotContain("COMPRESSED").doesNotContain("INCREMENTAL");
    }

    @Test
    void cadaTipoDeRespaldoProduceSuComandoYSuTag() {
        assertThat(comandoDeDatos(TipoRespaldo.COMPLETO)).contains("BACKUP TAG 'EST1_FULL'");
        assertThat(comandoDeDatos(TipoRespaldo.INCREMENTAL_NIVEL_0)).contains("BACKUP INCREMENTAL LEVEL 0 TAG 'EST1_N0'");
        assertThat(comandoDeDatos(TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL))
                .contains("BACKUP INCREMENTAL LEVEL 1 TAG 'EST1_N1D'").doesNotContain("CUMULATIVE");
        assertThat(comandoDeDatos(TipoRespaldo.INCREMENTAL_NIVEL_1_ACUMULATIVO))
                .contains("BACKUP INCREMENTAL LEVEL 1 CUMULATIVE TAG 'EST1_N1A'");
    }

    @Test
    void sinDatafilesElTipoIncrementalNoSeUsa() {
        Estrategia e = estrategia(4, TipoRespaldo.INCREMENTAL_NIVEL_1_DIFERENCIAL, false, TipoElemento.ARCHIVELOG);

        String script = generador.generar(e, 1, FECHA).contenido();

        assertThat(script).contains("BACKUP TAG 'EST4_ARC'").doesNotContain("INCREMENTAL").doesNotContain("DATABASE");
    }

    @Test
    void elMismoContenidoParaLaMismaConfiguracionSinImportarElOrdenDeLosElementos() {
        Estrategia a = estrategia(5, TipoRespaldo.COMPLETO, false, TipoElemento.SPFILE);
        a.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "USERS"));
        a.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "SYSAUX"));
        Estrategia b = estrategia(5, TipoRespaldo.COMPLETO, false);
        b.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "SYSAUX"));
        b.agregarElemento(new EstrategiaElemento(TipoElemento.SPFILE, null));
        b.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "USERS"));

        assertThat(generador.generar(a, 1, FECHA).contenido()).isEqualTo(generador.generar(b, 1, FECHA).contenido());
    }

    @Test
    void untextoLibreNoPuedeAgregarComandosAlScript() {
        Estrategia e = estrategia(6, TipoRespaldo.COMPLETO, false, TipoElemento.BASE_DATOS);
        e.setNombre("Mala\nDELETE NOPROMPT BACKUP;");

        String script = generador.generar(e, 1, FECHA).contenido();

        assertThat(script.lines()).noneMatch(l -> l.strip().startsWith("DELETE"));
        assertThat(script.lines().filter(l -> l.contains("DELETE"))).allMatch(l -> l.startsWith("#"));
    }

    @Test
    void rechazaDatosQueNoPasaronLaValidacion() {
        Estrategia sinElementos = estrategia(8, TipoRespaldo.COMPLETO, false);
        assertThatThrownBy(() -> generador.generar(sinElementos, 1, FECHA)).isInstanceOf(IllegalArgumentException.class);

        Estrategia rutaMala = estrategia(8, TipoRespaldo.COMPLETO, false, TipoElemento.BASE_DATOS);
        rutaMala.setRutaDestino("/tmp/x' DATABASE; DELETE NOPROMPT BACKUP; #");
        assertThatThrownBy(() -> generador.generar(rutaMala, 1, FECHA)).isInstanceOf(IllegalArgumentException.class);

        Estrategia tablespaceMalo = estrategia(8, TipoRespaldo.COMPLETO, false);
        tablespaceMalo.agregarElemento(new EstrategiaElemento(TipoElemento.TABLESPACE, "USERS; DELETE"));
        assertThatThrownBy(() -> generador.generar(tablespaceMalo, 1, FECHA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laHuellaSoloCambiaConLoQueAfectaAlScript() {
        Estrategia e = estrategia(9, TipoRespaldo.COMPLETO, false, TipoElemento.BASE_DATOS);
        String original = HuellaConfiguracion.de(e);

        e.setNombre("Otro nombre");
        e.setPrioridad(Prioridad.BAJA);
        e.setDiasRetencion(30);
        e.setResponsable("otra persona");
        Programacion p = new Programacion();
        p.setFechaInicio(LocalDate.of(2026, 10, 1));
        p.setHora(LocalTime.of(1, 0));
        p.setFrecuencia(Frecuencia.DIARIA);
        e.asignarProgramacion(p);
        assertThat(HuellaConfiguracion.de(e)).isEqualTo(original);

        e.setComprimido(true);
        assertThat(HuellaConfiguracion.de(e)).isNotEqualTo(original);
        e.setComprimido(false);
        e.setTipoRespaldo(TipoRespaldo.INCREMENTAL_NIVEL_0);
        assertThat(HuellaConfiguracion.de(e)).isNotEqualTo(original);
        e.setTipoRespaldo(TipoRespaldo.COMPLETO);
        e.agregarElemento(new EstrategiaElemento(TipoElemento.SPFILE, null));
        assertThat(HuellaConfiguracion.de(e)).isNotEqualTo(original);
    }

    private String comandoDeDatos(TipoRespaldo tipo) {
        return generador.generar(estrategia(1, tipo, false, TipoElemento.BASE_DATOS), 1, FECHA).contenido();
    }

    private static Estrategia estrategia(long id, TipoRespaldo tipo, boolean comprimido, TipoElemento... elementos) {
        BaseDatos base = new BaseDatos();
        ReflectionTestUtils.setField(base, "id", 1L);
        base.setNombre("XE pruebas");
        base.setContenedor("oracle-xe");
        base.setServicio("XEPDB1");
        base.setAmbiente(Ambiente.PRUEBAS);
        Estrategia e = new Estrategia();
        ReflectionTestUtils.setField(e, "id", id);
        e.setNombre("Produccion diaria");
        e.setBaseDatos(base);
        e.setResponsable("admin");
        e.setPrioridad(Prioridad.ALTA);
        e.setTipoRespaldo(tipo);
        e.setComprimido(comprimido);
        e.setRutaDestino("/opt/oracle/oradata/respaldos");
        for (TipoElemento t : elementos) {
            e.agregarElemento(new EstrategiaElemento(t, null));
        }
        return e;
    }
}
