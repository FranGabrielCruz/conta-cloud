ALTER TABLE inventory_movement
    ADD COLUMN movement_number VARCHAR(30),
    ADD COLUMN effective_at TIMESTAMPTZ,
    ADD COLUMN origin_document_number VARCHAR(40),
    ADD COLUMN origin_reference VARCHAR(100),
    ADD COLUMN source_line_id UUID,
    ADD COLUMN unit_of_measure_id UUID,
    ADD COLUMN source_quantity NUMERIC(19,4),
    ADD COLUMN base_quantity NUMERIC(19,4),
    ADD COLUMN conversion_factor NUMERIC(19,8) NOT NULL DEFAULT 1,
    ADD COLUMN source_key VARCHAR(300);

WITH numbered AS (
    SELECT id,row_number() OVER(PARTITION BY tenant_id,empresa_id ORDER BY movement_date,created_at,id) number
    FROM inventory_movement
)
UPDATE inventory_movement im
SET movement_number='MOV-' || lpad(numbered.number::text,6,'0')
FROM numbered WHERE numbered.id=im.id;

UPDATE inventory_movement im
SET effective_at=(im.movement_date::timestamp + im.created_at::time)::timestamptz,
    origin_document_number=coalesce((SELECT r.receipt_number FROM purchase_receipt r WHERE r.id=im.reference_id),im.reference_type || '-' || left(im.reference_id::text,8)),
    origin_reference=(SELECT r.reference FROM purchase_receipt r WHERE r.id=im.reference_id),
    source_line_id=(SELECT l.id FROM purchase_receipt_line l
        WHERE l.purchase_receipt_id=im.reference_id AND l.product_id=im.product_id ORDER BY l.line_order,l.id LIMIT 1),
    unit_of_measure_id=p.unit_of_measure_id,
    source_quantity=im.quantity,
    base_quantity=im.quantity,
    source_key=im.reference_type || ':' || im.reference_id || ':' || im.warehouse_id || ':' || im.product_id || ':' || im.direction
FROM product p
WHERE p.id=im.product_id;

ALTER TABLE inventory_movement
    ALTER COLUMN movement_number SET NOT NULL,
    ALTER COLUMN effective_at SET NOT NULL,
    ALTER COLUMN origin_document_number SET NOT NULL,
    ALTER COLUMN unit_of_measure_id SET NOT NULL,
    ALTER COLUMN source_quantity SET NOT NULL,
    ALTER COLUMN base_quantity SET NOT NULL,
    ALTER COLUMN source_key SET NOT NULL,
    ADD CONSTRAINT ck_inventory_movement_conversion CHECK(conversion_factor>0 AND source_quantity>0 AND base_quantity>0),
    ADD CONSTRAINT fk_inventory_movement_unit FOREIGN KEY(tenant_id,empresa_id,unit_of_measure_id)
        REFERENCES unit_of_measure(tenant_id,empresa_id,id),
    ADD CONSTRAINT uk_inventory_movement_number UNIQUE(tenant_id,empresa_id,movement_number),
    ADD CONSTRAINT uk_inventory_movement_source_key UNIQUE(tenant_id,empresa_id,source_key);

ALTER TABLE inventory_movement DROP CONSTRAINT IF EXISTS uk_inventory_movement_reference;

CREATE TABLE inventory_movement_sequence(
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_inventory_movement_sequence_empresa FOREIGN KEY(tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id)
);

INSERT INTO inventory_movement_sequence(tenant_id,empresa_id,last_number)
SELECT tenant_id,empresa_id,count(*) FROM inventory_movement GROUP BY tenant_id,empresa_id
ON CONFLICT(tenant_id,empresa_id) DO UPDATE
SET last_number=GREATEST(inventory_movement_sequence.last_number,EXCLUDED.last_number);

CREATE INDEX idx_inventory_movement_general
    ON inventory_movement(tenant_id,empresa_id,effective_at DESC,created_at DESC,id DESC);
CREATE INDEX idx_inventory_movement_product_history
    ON inventory_movement(tenant_id,empresa_id,product_id,warehouse_id,effective_at,created_at,id);
CREATE INDEX idx_inventory_movement_origin
    ON inventory_movement(tenant_id,empresa_id,reference_type,reference_id);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('inventario_movimientos.ver','Ver movimientos de inventario','Consultar movimientos de inventario','INVENTARIO','inventario_movimientos','ver'),
('inventario_movimientos.kardex','Ver Kardex de inventario','Consultar el Kardex por producto','INVENTARIO','inventario_movimientos','kardex'),
('inventario_movimientos.detalle','Ver detalle de movimiento','Consultar el detalle trazable de un movimiento','INVENTARIO','inventario_movimientos','detalle')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='inventario_movimientos'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
