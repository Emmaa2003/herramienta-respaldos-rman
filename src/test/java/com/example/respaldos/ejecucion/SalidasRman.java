package com.example.respaldos.ejecucion;

/** Salidas de RMAN representativas (formato real de RMAN 21c) para las pruebas sin contenedor. */
final class SalidasRman {

    private SalidasRman() {
    }

    static final String PIEZA = "/opt/oracle/oradata/respaldos/XE_EST7_20261001_0b35e7kq_1_1";
    static final String AUTORESPALDO = "/opt/oracle/homes/OraDBHome21cXE/dbs/c-3062126794-20261001-05";

    static final String EXITOSA = """
            Recovery Manager: Release 21.0.0.0.0 - Production on Thu Oct 1 04:00:00 2026
            Version 21.3.0.0.0

            connected to target database: XE (DBID=3062126794)

            RMAN> 2> 3> 4> 5> 6>
            Starting backup at 01-OCT-26
            using target database control file instead of recovery catalog
            allocated channel: ORA_DISK_1
            channel ORA_DISK_1: SID=30 device type=DISK
            channel ORA_DISK_1: starting compressed incremental level 0 datafile backup set
            channel ORA_DISK_1: specifying datafile(s) in backup set
            input datafile file number=00001 name=/opt/oracle/oradata/XE/system01.dbf
            channel ORA_DISK_1: starting piece 1 at 01-OCT-26
            channel ORA_DISK_1: finished piece 1 at 01-OCT-26
            piece handle=%s tag=EST7_N0 comment=NONE
            channel ORA_DISK_1: backup set complete, elapsed time: 00:00:35
            Finished backup at 01-OCT-26

            Starting Control File and SPFILE Autobackup at 01-OCT-26
            piece handle=%s comment=NONE
            Finished Control File and SPFILE Autobackup at 01-OCT-26

            RMAN>

            Recovery Manager complete.
            """.formatted(PIEZA, AUTORESPALDO);

    static final String CON_ERROR = """
            Starting backup at 01-OCT-26
            using channel ORA_DISK_1
            channel ORA_DISK_1: starting full datafile backup set
            RMAN-00571: ===========================================================
            RMAN-00569: =============== ERROR MESSAGE STACK FOLLOWS ===============
            RMAN-00571: ===========================================================
            RMAN-03009: failure of backup command on ORA_DISK_1 channel at 10/01/2026 04:00:00
            ORA-19504: failed to create file "/opt/oracle/oradata/respaldos/XE_EST7_1"
            ORA-27040: file create error, unable to create file

            Recovery Manager complete.
            """;

    static final String TODO_SALTADO = """
            Starting backup at 01-OCT-26
            using channel ORA_DISK_1
            skipping datafile 00012 because it has not changed
            backup cancelled because all files were skipped
            Finished backup at 01-OCT-26
            """;

    static final String SALTADO_SIN_FINISHED = """
            Starting backup at 01-OCT-26
            skipping datafile 00012 because it has not changed
            backup cancelled because all files were skipped
            """;

    static final String VERIFICACION_OK = """
            RMAN>
            using channel ORA_DISK_1
            crosschecked backup piece: found to be 'AVAILABLE'
            backup piece handle=%s RECID=30 STAMP=1213000000
            crosschecked backup piece: found to be 'AVAILABLE'
            backup piece handle=%s RECID=31 STAMP=1213000001
            Crosschecked 2 objects

            Starting restore at 01-OCT-26
            channel ORA_DISK_1: starting validation of datafile backup set
            channel ORA_DISK_1: validation complete, elapsed time: 00:00:15
            Finished restore at 01-OCT-26
            """.formatted(PIEZA, AUTORESPALDO);

    static final String VERIFICACION_EXPIRADA = """
            crosschecked backup piece: found to be 'AVAILABLE'
            backup piece handle=%s RECID=30 STAMP=1213000000
            crosschecked backup piece: found to be 'EXPIRED'
            backup piece handle=%s RECID=31 STAMP=1213000001
            Crosschecked 2 objects
            """.formatted(PIEZA, AUTORESPALDO);
}
