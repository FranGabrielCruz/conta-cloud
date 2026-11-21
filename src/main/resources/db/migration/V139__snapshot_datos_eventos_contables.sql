ALTER TABLE asiento_contable ADD COLUMN fuente_datos JSONB;
COMMENT ON COLUMN asiento_contable.fuente_datos IS 'Hechos confirmados del evento, incluida valoración de inventario, protegidos por inmutabilidad.';

