-- Revision del administrador sobre un script: ademas de aprobar, puede rechazarlo
-- dejando el motivo. comentario_revision guarda el comentario de la aprobacion o
-- el motivo del rechazo.
ALTER TABLE script_rman ADD (
    comentario_revision  VARCHAR2(1000),
    rechazado_por        VARCHAR2(100),
    fecha_rechazo        TIMESTAMP
);

ALTER TABLE script_rman ADD CONSTRAINT ck_script_rechazo CHECK (
    estado <> 'RECHAZADO'
    OR (rechazado_por IS NOT NULL AND fecha_rechazo IS NOT NULL AND comentario_revision IS NOT NULL));
