CREATE TABLE purchase_order_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    PRIMARY KEY (tenant_id,empresa_id),
    CONSTRAINT fk_purchase_order_sequence_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id)
);

CREATE TABLE purchase_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    order_number VARCHAR(30) NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_name_snapshot VARCHAR(180) NOT NULL,
    supplier_tax_id_snapshot VARCHAR(50),
    branch_id UUID NOT NULL,
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    currency_id UUID NOT NULL,
    payment_term_id UUID,
    reference VARCHAR(100),
    notes VARCHAR(1000),
    subtotal_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    issued_at TIMESTAMPTZ,
    issued_by UUID,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_order_status CHECK (status IN ('DRAFT','ISSUED','VOIDED','PARTIALLY_RECEIVED','RECEIVED')),
    CONSTRAINT ck_purchase_order_delivery CHECK (expected_delivery_date IS NULL OR expected_delivery_date >= order_date),
    CONSTRAINT ck_purchase_order_amounts CHECK (subtotal_amount>=0 AND discount_amount>=0 AND tax_amount>=0 AND total_amount>=0),
    CONSTRAINT fk_purchase_order_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_purchase_order_supplier FOREIGN KEY (tenant_id,empresa_id,supplier_id) REFERENCES supplier(tenant_id,empresa_id,id),
    CONSTRAINT fk_purchase_order_branch FOREIGN KEY (empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_purchase_order_currency FOREIGN KEY (empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_purchase_order_payment_term FOREIGN KEY (empresa_id,payment_term_id) REFERENCES condiciones_pago(empresa_id,id),
    CONSTRAINT fk_purchase_order_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_purchase_order_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    CONSTRAINT fk_purchase_order_issued_by FOREIGN KEY (issued_by) REFERENCES usuarios(id),
    CONSTRAINT fk_purchase_order_voided_by FOREIGN KEY (voided_by) REFERENCES usuarios(id),
    CONSTRAINT uk_purchase_order_number UNIQUE (tenant_id,empresa_id,order_number),
    CONSTRAINT uk_purchase_order_scope_id UNIQUE (tenant_id,empresa_id,id)
);

CREATE TABLE purchase_order_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    line_number INTEGER NOT NULL CHECK (line_number>0),
    product_id UUID,
    description VARCHAR(500) NOT NULL CHECK (btrim(description)<>''),
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity>0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price>=0),
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount>=0),
    tax_id UUID,
    tax_name_snapshot VARCHAR(100),
    tax_rate_snapshot NUMERIC(9,4) NOT NULL DEFAULT 0 CHECK (tax_rate_snapshot>=0),
    gross_subtotal_amount NUMERIC(19,4) NOT NULL CHECK (gross_subtotal_amount>=0),
    taxable_base_amount NUMERIC(19,4) NOT NULL CHECK (taxable_base_amount>=0),
    tax_amount NUMERIC(19,4) NOT NULL CHECK (tax_amount>=0),
    total_amount NUMERIC(19,4) NOT NULL CHECK (total_amount>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_order_line_order FOREIGN KEY (tenant_id,empresa_id,purchase_order_id)
        REFERENCES purchase_order(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_line_tax FOREIGN KEY (empresa_id,tax_id) REFERENCES impuestos(empresa_id,id),
    CONSTRAINT uk_purchase_order_line_number UNIQUE (purchase_order_id,line_number)
);

CREATE INDEX idx_purchase_order_list ON purchase_order(tenant_id,empresa_id,order_date DESC,created_at DESC);
CREATE INDEX idx_purchase_order_status ON purchase_order(tenant_id,empresa_id,status,order_date DESC);
CREATE INDEX idx_purchase_order_supplier ON purchase_order(tenant_id,empresa_id,supplier_id,order_date DESC);
CREATE INDEX idx_purchase_order_line_order ON purchase_order_line(tenant_id,empresa_id,purchase_order_id);

CREATE TRIGGER trg_purchase_order_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON purchase_order
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_purchase_order_line_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON purchase_order_line
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('ordenes_compra.ver','Ver órdenes de compra','Consultar órdenes de compra','COMPRAS','ordenes_compra','ver'),
('ordenes_compra.crear','Crear órdenes de compra','Crear órdenes de compra en borrador','COMPRAS','ordenes_compra','crear'),
('ordenes_compra.editar','Editar órdenes de compra','Modificar órdenes de compra en borrador','COMPRAS','ordenes_compra','editar'),
('ordenes_compra.emitir','Emitir órdenes de compra','Emitir órdenes de compra','COMPRAS','ordenes_compra','emitir'),
('ordenes_compra.anular','Anular órdenes de compra','Anular órdenes de compra','COMPRAS','ordenes_compra','anular'),
('ordenes_compra.recibir','Recibir órdenes de compra','Preparado para registrar recepciones futuras','COMPRAS','ordenes_compra','recibir')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso='ordenes_compra'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'COMPRAS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
