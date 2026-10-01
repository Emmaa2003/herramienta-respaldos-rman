package com.example.respaldos.infraestructura;

import com.example.respaldos.modelo.ModoArchivado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pruebas contra el contenedor oracle-xe real. Solo usan comandos de lectura
 * (LIST, REPORT, df, stat): no crean respaldos ni modifican la base.
 */
@SpringBootTest
class AmbienteIntegracionTest {

    private static final String CONTENEDOR = "oracle-xe";

    @Autowired ClienteContenedor cliente;
    @Autowired CatalogoOracle catalogo;

    @Test
    void elContenedorEstaCorriendo() {
        assertThat(cliente.contenedorEnEjecucion(CONTENEDOR)).isTrue();
    }

    @Test
    void rmanEjecutaUnScriptSinExitYTermina() {
        ResultadoProceso r = cliente.ejecutarRman(CONTENEDOR, "LIST BACKUP SUMMARY;\n");

        assertThat(r.terminoSinError()).as(r.salida()).isTrue();
        assertThat(r.salida()).contains("connected to target database").contains("Recovery Manager complete");
    }

    @Test
    void rmanDevuelveCodigoDistintoDeCeroYLineasRmanAnteUnError() {
        ResultadoProceso r = cliente.ejecutarRman(CONTENEDOR, "LIST BACKUP OF TABLESPACE NO_EXISTE_XYZ;\n");

        assertThat(r.codigoSalida()).isNotZero();
        assertThat(r.salida()).contains("RMAN-");
    }

    @Test
    void checksyntaxDistingueScriptsCorrectosDeErroneos() {
        assertThat(cliente.verificarSintaxisRman(CONTENEDOR, "RUN {\n  BACKUP DATABASE;\n}\n").terminoSinError())
                .isTrue();

        ResultadoProceso error = cliente.verificarSintaxisRman(CONTENEDOR, "BACKUP DATABAS;\n");
        assertThat(error.codigoSalida()).isNotZero();
        assertThat(error.salida()).contains("RMAN-01009");
    }

    @Test
    void espacioDelDestinoDeRespaldos() {
        EspacioDisco espacio = cliente.espacioDisco(CONTENEDOR, "/opt/oracle/oradata/respaldos");

        assertThat(espacio.totalBytes()).isPositive();
        assertThat(espacio.disponiblesBytes()).isPositive().isLessThanOrEqualTo(espacio.totalBytes());
    }

    @Test
    void tamanoDeArchivoExistenteYNoExistente() {
        assertThat(cliente.tamanoArchivo(CONTENEDOR, "/opt/oracle/oradata/XE/XEPDB1/users01.dbf"))
                .hasValueSatisfying(t -> assertThat(t).isPositive());
        assertThat(cliente.tamanoArchivo(CONTENEDOR, "/opt/oracle/oradata/respaldos/no_existe.bkp"))
                .isEmpty();
    }

    @Test
    void catalogoDeLaBase() {
        assumeTrue(tienePermisosDeCatalogo(),
                "Faltan los GRANT de lectura sobre V_$DATABASE / DBA_TABLESPACES / DBA_DATA_FILES");

        CatalogoOracle.InfoBaseDatos info = catalogo.informacionBase();
        assertThat(info.nombre()).isEqualTo("XE");
        assertThat(info.modoArchivado()).isEqualTo(ModoArchivado.ARCHIVELOG);
        assertThat(info.pdb()).isEqualTo("XEPDB1");

        assertThat(catalogo.tablespaces())
                .anySatisfy(t -> assertThat(t.nombreRman()).isEqualTo("XEPDB1:USERS"));
        assertThat(catalogo.datafiles())
                .anySatisfy(d -> assertThat(d.ruta()).endsWith("/XEPDB1/users01.dbf"));
    }

    private boolean tienePermisosDeCatalogo() {
        try {
            catalogo.informacionBase();
            return true;
        } catch (AmbienteException e) {
            return false;
        }
    }
}
