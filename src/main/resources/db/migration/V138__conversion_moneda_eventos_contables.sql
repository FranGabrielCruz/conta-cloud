ALTER TABLE asiento_contable
 ADD COLUMN fuente_moneda_id UUID,
 ADD COLUMN fuente_tasa_cambio NUMERIC(19,8),
 ADD CONSTRAINT fk_asiento_fuente_moneda FOREIGN KEY (empresa_id,fuente_moneda_id) REFERENCES monedas(empresa_id,id),
 ADD CONSTRAINT ck_asiento_fuente_conversion CHECK (
  (fuente_moneda_id IS NULL AND fuente_tasa_cambio IS NULL) OR
  (fuente_moneda_id IS NOT NULL AND fuente_tasa_cambio IS NOT NULL AND fuente_tasa_cambio > 0));
COMMENT ON COLUMN asiento_contable.fuente_moneda_id IS 'Moneda original de la operación; el libro conserva moneda base.';
COMMENT ON COLUMN asiento_contable.fuente_tasa_cambio IS 'Factor histórico origen a base en la fecha contable; inmutable después de contabilizar.';

