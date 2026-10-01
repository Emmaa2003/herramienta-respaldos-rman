package com.example.respaldos.infraestructura;

import com.example.respaldos.modelo.ModoArchivado;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Lee el modo de archivado y el catalogo de tablespaces y datafiles.
 * <p>
 * Usa la conexion de la app (respaldos@XEPDB1), por lo que describe el PDB XEPDB1
 * del contenedor oracle-xe. Requiere SELECT sobre V_$DATABASE, DBA_TABLESPACES y
 * DBA_DATA_FILES.
 * <p>
 * RMAN se conecta a la raiz del CDB (rman target /): desde ahi un tablespace del PDB
 * se nombra PDB:TABLESPACE (p. ej. XEPDB1:USERS). Por eso cada tablespace lleva el
 * nombre del PDB. Los numeros de datafile ya son globales en el CDB.
 */
@Component
public class CatalogoOracle {

    private final JdbcTemplate jdbc;

    public CatalogoOracle(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InfoBaseDatos informacionBase() {
        return consultar(() -> jdbc.queryForObject(
                "SELECT name, log_mode, open_mode, SYS_CONTEXT('USERENV', 'CON_NAME') FROM v$database",
                (rs, i) -> new InfoBaseDatos(
                        rs.getString(1),
                        ModoArchivado.valueOf(rs.getString(2)),
                        rs.getString(3),
                        rs.getString(4))));
    }

    public List<TablespaceInfo> tablespaces() {
        return consultar(() -> jdbc.query(
                "SELECT SYS_CONTEXT('USERENV', 'CON_NAME'), tablespace_name, contents, status "
                        + "FROM dba_tablespaces ORDER BY tablespace_name",
                (rs, i) -> new TablespaceInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))));
    }

    public List<DatafileInfo> datafiles() {
        return consultar(() -> jdbc.query(
                "SELECT file_id, file_name, tablespace_name, bytes FROM dba_data_files ORDER BY file_id",
                (rs, i) -> new DatafileInfo(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4))));
    }

    private static <T> T consultar(Supplier<T> consulta) {
        try {
            return consulta.get();
        } catch (DataAccessException e) {
            throw new AmbienteException("No se pudo leer el catalogo de Oracle. Verifique que el usuario "
                    + "respaldos tenga SELECT sobre V_$DATABASE, DBA_TABLESPACES y DBA_DATA_FILES. "
                    + "Detalle: " + e.getMostSpecificCause().getMessage(), e);
        }
    }

    /**
     * @param modoApertura OPEN_MODE de V$DATABASE (READ WRITE, MOUNTED, ...)
     * @param pdb          contenedor (PDB) al que pertenece el catalogo leido
     */
    public record InfoBaseDatos(String nombre, ModoArchivado modoArchivado, String modoApertura, String pdb) {

        public boolean abierta() {
            return modoApertura != null && modoApertura.startsWith("READ");
        }
    }

    /** @param contenido PERMANENT, UNDO o TEMPORARY (los TEMPORARY no se respaldan con RMAN) */
    public record TablespaceInfo(String pdb, String nombre, String contenido, String estado) {

        /** Nombre con el que RMAN, conectado a la raiz del CDB, reconoce este tablespace. */
        public String nombreRman() {
            return pdb + ":" + nombre;
        }
    }

    public record DatafileInfo(int numero, String ruta, String tablespace, long bytes) {
    }
}
