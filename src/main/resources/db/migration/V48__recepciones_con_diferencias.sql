ALTER TABLE purchase_receipt
    ADD COLUMN difference_note VARCHAR(1000);

ALTER TABLE purchase_receipt_line
    ADD COLUMN line_source VARCHAR(20),
    ADD COLUMN difference_type VARCHAR(30);

UPDATE purchase_receipt_line
SET line_source = CASE WHEN purchase_order_line_id IS NULL THEN 'MANUAL' ELSE 'ORDER_LINE' END,
    difference_type = 'NONE';

ALTER TABLE purchase_receipt_line
    ALTER COLUMN line_source SET NOT NULL,
    ALTER COLUMN difference_type SET NOT NULL,
    ADD CONSTRAINT ck_purchase_receipt_line_source CHECK(line_source IN('ORDER_LINE','MANUAL')),
    ADD CONSTRAINT ck_purchase_receipt_line_difference CHECK(difference_type IN('NONE','UNORDERED_PRODUCT','OVER_RECEIPT'));

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('recepciones.recibir_diferencias','Recibir diferencias','Confirmar productos fuera de orden o sobrerecepciones','COMPRAS','recepciones','recibir_diferencias')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
JOIN empresas e ON e.id=r.empresa_id
JOIN permisos p ON p.codigo='recepciones.recibir_diferencias'
WHERE upper(r.codigo)='ADMINISTRADOR' AND upper(e.codigo)='DEMO'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

CREATE INDEX idx_receipt_line_confirmed_sum
ON purchase_receipt_line(tenant_id,empresa_id,purchase_order_line_id)
WHERE purchase_order_line_id IS NOT NULL;
