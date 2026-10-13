ALTER TABLE warehouse ADD COLUMN system_managed BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO warehouse(tenant_id,empresa_id,code,name,active,branch_id,normalized_name,warehouse_type,
                      observation,system_managed,created_by,updated_by)
SELECT e.tenant_id,e.id,'SYS-TRANSIT','Mercancía en tránsito',TRUE,s.id,'mercancía en tránsito','TRANSITO',
       'Almacén técnico administrado por el sistema para transferencias en tránsito.',TRUE,NULL,NULL
FROM empresas e
JOIN LATERAL (
    SELECT b.id FROM sucursales b WHERE b.empresa_id=e.id
    ORDER BY b.principal DESC,b.activo DESC,b.nombre,b.id LIMIT 1
) s ON TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM warehouse w WHERE w.tenant_id=e.tenant_id AND w.empresa_id=e.id AND w.system_managed
)
ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;

CREATE TABLE inventory_transfer_sequence(
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_inventory_transfer_sequence_empresa FOREIGN KEY(tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id)
);

CREATE TABLE inventory_transfer(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    transfer_number VARCHAR(30) NOT NULL,
    transfer_date DATE NOT NULL,
    transfer_type VARCHAR(20) NOT NULL,
    source_warehouse_id UUID NOT NULL,
    destination_warehouse_id UUID NOT NULL,
    transit_warehouse_id UUID,
    reference VARCHAR(100),
    notes VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    dispatched_at TIMESTAMPTZ,
    dispatched_by UUID,
    received_at TIMESTAMPTZ,
    received_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancellation_reason VARCHAR(500),
    reversal_of_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_inventory_transfer_type CHECK(transfer_type IN('DIRECT','IN_TRANSIT')),
    CONSTRAINT ck_inventory_transfer_status CHECK(status IN('DRAFT','IN_TRANSIT','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    CONSTRAINT ck_inventory_transfer_warehouses CHECK(source_warehouse_id<>destination_warehouse_id),
    CONSTRAINT fk_inventory_transfer_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_inventory_transfer_source FOREIGN KEY(tenant_id,empresa_id,source_warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_transfer_destination FOREIGN KEY(tenant_id,empresa_id,destination_warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_transfer_transit FOREIGN KEY(tenant_id,empresa_id,transit_warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_transfer_dispatched_by FOREIGN KEY(dispatched_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_transfer_received_by FOREIGN KEY(received_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_transfer_cancelled_by FOREIGN KEY(cancelled_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_transfer_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_transfer_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
    CONSTRAINT fk_inventory_transfer_reversal FOREIGN KEY(reversal_of_id) REFERENCES inventory_transfer(id),
    CONSTRAINT uk_inventory_transfer_number UNIQUE(tenant_id,empresa_id,transfer_number),
    CONSTRAINT uk_inventory_transfer_scope UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE inventory_transfer_line(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_transfer_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    product_id UUID NOT NULL,
    unit_of_measure_id UUID NOT NULL,
    product_code_snapshot VARCHAR(24) NOT NULL,
    description_snapshot VARCHAR(500) NOT NULL,
    unit_snapshot VARCHAR(120) NOT NULL,
    dispatched_quantity NUMERIC(19,4) NOT NULL,
    received_quantity NUMERIC(19,4) NOT NULL DEFAULT 0,
    pending_quantity NUMERIC(19,4) NOT NULL,
    difference_quantity NUMERIC(19,4) NOT NULL DEFAULT 0,
    difference_reason VARCHAR(500),
    conversion_factor NUMERIC(19,8) NOT NULL DEFAULT 1,
    base_quantity NUMERIC(19,4) NOT NULL,
    line_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_transfer_line_quantities CHECK(dispatched_quantity>0 AND received_quantity>=0 AND
        pending_quantity>=0 AND conversion_factor>0 AND base_quantity>0),
    CONSTRAINT fk_inventory_transfer_line_header FOREIGN KEY(tenant_id,empresa_id,inventory_transfer_id)
        REFERENCES inventory_transfer(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_transfer_line_product FOREIGN KEY(tenant_id,empresa_id,product_id)
        REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_transfer_line_unit FOREIGN KEY(tenant_id,empresa_id,unit_of_measure_id)
        REFERENCES unit_of_measure(tenant_id,empresa_id,id),
    CONSTRAINT uk_inventory_transfer_line_order UNIQUE(inventory_transfer_id,line_order),
    CONSTRAINT uk_inventory_transfer_line_product_unit UNIQUE(inventory_transfer_id,product_id,unit_of_measure_id)
);

CREATE INDEX idx_inventory_transfer_list ON inventory_transfer(tenant_id,empresa_id,transfer_date DESC,created_at DESC,id DESC);
CREATE INDEX idx_inventory_transfer_status ON inventory_transfer(tenant_id,empresa_id,status,transfer_date DESC);
CREATE INDEX idx_inventory_transfer_warehouses ON inventory_transfer(tenant_id,empresa_id,source_warehouse_id,destination_warehouse_id);
CREATE INDEX idx_inventory_transfer_line_product ON inventory_transfer_line(tenant_id,empresa_id,product_id);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('inventario_transferencias.ver','Ver transferencias de inventario','Consultar transferencias de inventario','INVENTARIO','inventario_transferencias','ver'),
('inventario_transferencias.crear','Crear transferencias de inventario','Crear transferencias de inventario','INVENTARIO','inventario_transferencias','crear'),
('inventario_transferencias.editar','Editar transferencias en borrador','Editar transferencias de inventario en borrador','INVENTARIO','inventario_transferencias','editar'),
('inventario_transferencias.despachar','Despachar transferencias','Despachar transferencias de inventario','INVENTARIO','inventario_transferencias','despachar'),
('inventario_transferencias.recibir','Recibir transferencias','Recibir transferencias de inventario en tránsito','INVENTARIO','inventario_transferencias','recibir'),
('inventario_transferencias.cancelar','Cancelar transferencias en borrador','Cancelar transferencias de inventario en borrador','INVENTARIO','inventario_transferencias','cancelar'),
('inventario_transferencias.revertir','Revertir transferencias recibidas','Crear la reversión trazable de una transferencia recibida','INVENTARIO','inventario_transferencias','revertir'),
('inventario_transferencias.movimientos','Ver movimientos de transferencias','Consultar los movimientos relacionados a una transferencia','INVENTARIO','inventario_transferencias','movimientos')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
JOIN empresas e ON e.id=r.empresa_id
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='inventario_transferencias'
  AND (upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE FROM empresas e
WHERE upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
