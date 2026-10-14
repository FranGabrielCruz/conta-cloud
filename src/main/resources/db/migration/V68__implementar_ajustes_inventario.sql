CREATE TABLE inventory_adjustment_reason(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(150) NOT NULL,
    allowed_direction VARCHAR(10) NOT NULL,
    requires_notes BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_adjustment_reason_direction CHECK(allowed_direction IN('IN','OUT','BOTH')),
    CONSTRAINT fk_inventory_adjustment_reason_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT uk_inventory_adjustment_reason_code UNIQUE(tenant_id,empresa_id,code),
    CONSTRAINT uk_inventory_adjustment_reason_scope UNIQUE(tenant_id,empresa_id,id)
);

INSERT INTO inventory_adjustment_reason(tenant_id,empresa_id,code,name,allowed_direction,requires_notes)
SELECT e.tenant_id,e.id,v.code,v.name,v.direction,v.notes
FROM empresas e CROSS JOIN (VALUES
 ('DAMAGED','Producto dañado','OUT',FALSE),('EXPIRED','Producto vencido','OUT',FALSE),
 ('BREAKAGE','Rotura','OUT',FALSE),('LOSS','Pérdida','OUT',FALSE),
 ('FOUND','Mercancía encontrada','IN',FALSE),('REGISTER_ERROR','Error de registro','BOTH',FALSE),
 ('INVENTORY_CORRECTION','Corrección de inventario','BOTH',FALSE),('OTHER','Otro','BOTH',TRUE)
) AS v(code,name,direction,notes)
ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;

CREATE TABLE inventory_adjustment_sequence(
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_inventory_adjustment_sequence_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE inventory_adjustment(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    adjustment_number VARCHAR(30) NOT NULL,
    effective_date DATE NOT NULL,
    warehouse_id UUID NOT NULL,
    reason_id UUID NOT NULL,
    reference VARCHAR(100),
    notes VARCHAR(1000),
    adjustment_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    warehouse_name_snapshot VARCHAR(200),
    branch_name_snapshot VARCHAR(200),
    reason_name_snapshot VARCHAR(150),
    confirmed_at TIMESTAMPTZ,
    confirmed_by UUID,
    confirmed_by_name_snapshot VARCHAR(200),
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancellation_reason VARCHAR(500),
    reversed_at TIMESTAMPTZ,
    reversed_by UUID,
    reversed_by_adjustment_id UUID,
    reversal_of_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_inventory_adjustment_type CHECK(adjustment_type IN('ENTRY','EXIT','MIXED')),
    CONSTRAINT ck_inventory_adjustment_status CHECK(status IN('DRAFT','CONFIRMED','CANCELLED','REVERSED')),
    CONSTRAINT fk_inventory_adjustment_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_inventory_adjustment_warehouse FOREIGN KEY(tenant_id,empresa_id,warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_adjustment_reason FOREIGN KEY(tenant_id,empresa_id,reason_id) REFERENCES inventory_adjustment_reason(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_adjustment_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_adjustment_cancelled_by FOREIGN KEY(cancelled_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_adjustment_reversed_by FOREIGN KEY(reversed_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_adjustment_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_adjustment_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_adjustment_reversal_of FOREIGN KEY(reversal_of_id) REFERENCES inventory_adjustment(id),
    CONSTRAINT uk_inventory_adjustment_number UNIQUE(tenant_id,empresa_id,adjustment_number),
    CONSTRAINT uk_inventory_adjustment_scope UNIQUE(tenant_id,empresa_id,id)
);

ALTER TABLE inventory_adjustment ADD CONSTRAINT fk_inventory_adjustment_reversed_document
    FOREIGN KEY(reversed_by_adjustment_id) REFERENCES inventory_adjustment(id);

CREATE TABLE inventory_adjustment_line(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_adjustment_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    product_id UUID NOT NULL,
    unit_of_measure_id UUID NOT NULL,
    adjustment_quantity NUMERIC(19,4) NOT NULL CHECK(adjustment_quantity<>0),
    base_quantity NUMERIC(19,4) NOT NULL CHECK(base_quantity<>0),
    conversion_factor NUMERIC(19,8) NOT NULL DEFAULT 1 CHECK(conversion_factor>0),
    balance_before NUMERIC(19,4),
    balance_after NUMERIC(19,4),
    product_code_snapshot VARCHAR(24) NOT NULL,
    product_name_snapshot VARCHAR(300) NOT NULL,
    unit_snapshot VARCHAR(120) NOT NULL,
    line_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_adjustment_line_header FOREIGN KEY(tenant_id,empresa_id,inventory_adjustment_id)
        REFERENCES inventory_adjustment(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_adjustment_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_adjustment_line_unit FOREIGN KEY(tenant_id,empresa_id,unit_of_measure_id) REFERENCES unit_of_measure(tenant_id,empresa_id,id),
    CONSTRAINT uk_inventory_adjustment_line_order UNIQUE(inventory_adjustment_id,line_order),
    CONSTRAINT uk_inventory_adjustment_line_product_unit UNIQUE(inventory_adjustment_id,product_id,unit_of_measure_id)
);

CREATE INDEX idx_inventory_adjustment_list ON inventory_adjustment(tenant_id,empresa_id,effective_date DESC,created_at DESC,id DESC);
CREATE INDEX idx_inventory_adjustment_filters ON inventory_adjustment(tenant_id,empresa_id,status,warehouse_id,reason_id,effective_date DESC);
CREATE INDEX idx_inventory_adjustment_line_product ON inventory_adjustment_line(tenant_id,empresa_id,product_id);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('inventario_ajustes.ver','Ver ajustes de inventario','Consultar ajustes de inventario','INVENTARIO','inventario_ajustes','ver'),
('inventario_ajustes.crear','Crear ajustes de inventario','Crear ajustes de inventario','INVENTARIO','inventario_ajustes','crear'),
('inventario_ajustes.editar','Editar borradores de ajuste','Editar ajustes de inventario en borrador','INVENTARIO','inventario_ajustes','editar'),
('inventario_ajustes.confirmar','Confirmar ajustes de inventario','Confirmar ajustes y generar movimientos','INVENTARIO','inventario_ajustes','confirmar'),
('inventario_ajustes.anular','Anular borradores de ajuste','Anular ajustes que no han afectado inventario','INVENTARIO','inventario_ajustes','anular'),
('inventario_ajustes.revertir','Revertir ajustes confirmados','Crear movimientos inversos trazables','INVENTARIO','inventario_ajustes','revertir'),
('inventario_ajustes.pdf','Generar PDF de ajustes','Generar el documento oficial del ajuste','INVENTARIO','inventario_ajustes','pdf'),
('inventario_ajustes.movimientos','Ver movimientos de ajustes','Consultar movimientos relacionados con ajustes','INVENTARIO','inventario_ajustes','movimientos')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p JOIN empresas e ON e.id=r.empresa_id
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='inventario_ajustes'
  AND (upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE FROM empresas e
WHERE upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
