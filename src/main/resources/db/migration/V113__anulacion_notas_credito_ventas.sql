ALTER TABLE sales_credit_note DROP CONSTRAINT sales_credit_note_status_check;
ALTER TABLE sales_credit_note ADD CONSTRAINT ck_sales_credit_note_status CHECK(status IN ('CONFIRMED','VOIDED'));
ALTER TABLE sales_credit_note ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales_credit_note ADD COLUMN annulled_at TIMESTAMPTZ;
ALTER TABLE sales_credit_note ADD COLUMN annulled_by UUID REFERENCES usuarios(id);
ALTER TABLE sales_credit_note ADD COLUMN annulled_by_name VARCHAR(200);
ALTER TABLE sales_credit_note ADD COLUMN annulment_reason VARCHAR(1000);
ALTER TABLE sales_credit_note ADD COLUMN annulment_key UUID;
ALTER TABLE sales_credit_note ADD COLUMN annulled_zone VARCHAR(80);
ALTER TABLE sales_credit_note ADD CONSTRAINT ck_sales_credit_note_annulment CHECK (
 (status='CONFIRMED' AND annulled_at IS NULL AND annulled_by IS NULL AND annulment_reason IS NULL AND annulment_key IS NULL)
 OR (status='VOIDED' AND annulled_at IS NOT NULL AND annulled_by IS NOT NULL AND annulled_by_name IS NOT NULL
     AND length(trim(annulment_reason)) BETWEEN 3 AND 1000 AND annulment_key IS NOT NULL AND annulled_zone IS NOT NULL));
INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES('notas_credito.anular','Anular notas de crédito','Revertir los efectos propios de una nota emitida','VENTAS','notas_credito','anular') ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo='notas_credito.anular'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
