ALTER TABLE asiento_contable
 ADD COLUMN fuente_version BIGINT CHECK (fuente_version IS NULL OR fuente_version >= 0),
 ADD COLUMN fuente_trace_id VARCHAR(120);
COMMENT ON COLUMN asiento_contable.fuente_version IS 'Versión del documento al confirmar el evento. NULL en asientos anteriores.';
COMMENT ON COLUMN asiento_contable.fuente_trace_id IS 'TraceId del evento original; inmutable al contabilizar.';

