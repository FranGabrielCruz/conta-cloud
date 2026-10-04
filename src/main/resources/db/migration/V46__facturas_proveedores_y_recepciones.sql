CREATE TABLE warehouse (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL, name VARCHAR(120) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID,
    CONSTRAINT fk_warehouse_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT uk_warehouse_code UNIQUE(tenant_id,empresa_id,code),
    CONSTRAINT uk_warehouse_scope UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE purchase_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    supplier_id UUID NOT NULL, branch_id UUID NOT NULL, supplier_invoice_number VARCHAR(100) NOT NULL,
    normalized_supplier_invoice_number VARCHAR(100) NOT NULL, fiscal_number VARCHAR(50), invoice_date DATE NOT NULL,
    due_date DATE NOT NULL, payment_term_id UUID, currency_id UUID NOT NULL, purchase_order_id UUID,
    reference VARCHAR(100), notes VARCHAR(1000), subtotal NUMERIC(19,4) NOT NULL DEFAULT 0,
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0, tax_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    total NUMERIC(19,4) NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    registered_at TIMESTAMPTZ, registered_by UUID, voided_at TIMESTAMPTZ, voided_by UUID,
    void_reason VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_invoice_status CHECK(status IN('DRAFT','REGISTERED','VOIDED')),
    CONSTRAINT ck_purchase_invoice_dates CHECK(due_date>=invoice_date),
    CONSTRAINT ck_purchase_invoice_amounts CHECK(subtotal>=0 AND discount_total>=0 AND tax_total>=0 AND total>=0),
    CONSTRAINT fk_purchase_invoice_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_purchase_invoice_supplier FOREIGN KEY(tenant_id,empresa_id,supplier_id) REFERENCES supplier(tenant_id,empresa_id,id),
    CONSTRAINT fk_purchase_invoice_branch FOREIGN KEY(empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_purchase_invoice_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_purchase_invoice_term FOREIGN KEY(empresa_id,payment_term_id) REFERENCES condiciones_pago(empresa_id,id),
    CONSTRAINT fk_purchase_invoice_order FOREIGN KEY(tenant_id,empresa_id,purchase_order_id) REFERENCES purchase_order(tenant_id,empresa_id,id),
    CONSTRAINT uk_purchase_invoice_supplier_number UNIQUE(tenant_id,empresa_id,supplier_id,normalized_supplier_invoice_number),
    CONSTRAINT uk_purchase_invoice_scope UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE purchase_invoice_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), purchase_invoice_id UUID NOT NULL, tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL, product_id UUID NOT NULL, product_code_snapshot VARCHAR(24),
    description_snapshot VARCHAR(500) NOT NULL, unit_snapshot VARCHAR(120), quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL, discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_id UUID, tax_name_snapshot VARCHAR(120), tax_rate_snapshot NUMERIC(9,6) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0, line_subtotal NUMERIC(19,4) NOT NULL,
    line_total NUMERIC(19,4) NOT NULL, line_order INTEGER NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_purchase_invoice_line CHECK(quantity>0 AND unit_price>=0 AND discount_amount>=0 AND tax_amount>=0 AND line_subtotal>=0 AND line_total>=0),
    CONSTRAINT fk_purchase_invoice_line_invoice FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_id) REFERENCES purchase_invoice(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_invoice_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_purchase_invoice_line_tax FOREIGN KEY(empresa_id,tax_id) REFERENCES impuestos(empresa_id,id),
    CONSTRAINT uk_purchase_invoice_line_order UNIQUE(purchase_invoice_id,line_order)
);

CREATE TABLE accounts_payable (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    purchase_invoice_id UUID NOT NULL, supplier_id UUID NOT NULL, currency_id UUID NOT NULL,
    due_date DATE NOT NULL, original_amount NUMERIC(19,4) NOT NULL, applied_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', voided BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID NOT NULL,
    CONSTRAINT ck_accounts_payable_amounts CHECK(original_amount>=0 AND applied_amount>=0 AND applied_amount<=original_amount),
    CONSTRAINT ck_accounts_payable_status CHECK(status IN('PENDING','PARTIALLY_PAID','PAID')),
    CONSTRAINT fk_accounts_payable_invoice FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_id) REFERENCES purchase_invoice(tenant_id,empresa_id,id),
    CONSTRAINT uk_accounts_payable_invoice UNIQUE(purchase_invoice_id)
);

CREATE TABLE purchase_receipt_sequence (
    tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, last_number BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(tenant_id,empresa_id), FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE purchase_receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    receipt_number VARCHAR(30) NOT NULL, supplier_id UUID NOT NULL, purchase_order_id UUID,
    warehouse_id UUID NOT NULL, receipt_date DATE NOT NULL, reference VARCHAR(100), notes VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', confirmed_at TIMESTAMPTZ, confirmed_by UUID,
    voided_at TIMESTAMPTZ, voided_by UUID, void_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_purchase_receipt_status CHECK(status IN('DRAFT','CONFIRMED','VOIDED')),
    CONSTRAINT fk_purchase_receipt_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_purchase_receipt_supplier FOREIGN KEY(tenant_id,empresa_id,supplier_id) REFERENCES supplier(tenant_id,empresa_id,id),
    CONSTRAINT fk_purchase_receipt_order FOREIGN KEY(tenant_id,empresa_id,purchase_order_id) REFERENCES purchase_order(tenant_id,empresa_id,id),
    CONSTRAINT fk_purchase_receipt_warehouse FOREIGN KEY(tenant_id,empresa_id,warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT uk_purchase_receipt_number UNIQUE(tenant_id,empresa_id,receipt_number),
    CONSTRAINT uk_purchase_receipt_scope UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE purchase_receipt_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), purchase_receipt_id UUID NOT NULL, purchase_order_line_id UUID,
    tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, product_id UUID NOT NULL,
    product_code_snapshot VARCHAR(24), description_snapshot VARCHAR(500) NOT NULL, unit_snapshot VARCHAR(120),
    quantity_received NUMERIC(19,4) NOT NULL, line_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_purchase_receipt_line_quantity CHECK(quantity_received>0),
    CONSTRAINT fk_purchase_receipt_line_receipt FOREIGN KEY(tenant_id,empresa_id,purchase_receipt_id) REFERENCES purchase_receipt(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_receipt_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_purchase_receipt_line_order_line FOREIGN KEY(purchase_order_line_id) REFERENCES purchase_order_line(id),
    CONSTRAINT uk_purchase_receipt_line_order UNIQUE(purchase_receipt_id,line_order)
);

CREATE TABLE inventory_movement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    warehouse_id UUID NOT NULL, product_id UUID NOT NULL, movement_date DATE NOT NULL,
    quantity NUMERIC(19,4) NOT NULL, direction VARCHAR(10) NOT NULL, movement_type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(40) NOT NULL, reference_id UUID NOT NULL, reversal_of_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID NOT NULL,
    CONSTRAINT ck_inventory_movement_quantity CHECK(quantity>0),
    CONSTRAINT ck_inventory_movement_direction CHECK(direction IN('IN','OUT')),
    CONSTRAINT fk_inventory_movement_warehouse FOREIGN KEY(tenant_id,empresa_id,warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_movement_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_inventory_movement_reversal FOREIGN KEY(reversal_of_id) REFERENCES inventory_movement(id),
    CONSTRAINT uk_inventory_movement_reference UNIQUE(reference_type,reference_id,product_id,direction)
);

CREATE INDEX idx_purchase_invoice_list ON purchase_invoice(tenant_id,empresa_id,invoice_date DESC);
CREATE INDEX idx_purchase_receipt_list ON purchase_receipt(tenant_id,empresa_id,receipt_date DESC);
CREATE INDEX idx_receipt_order_line ON purchase_receipt_line(purchase_order_line_id);
CREATE INDEX idx_inventory_balance ON inventory_movement(tenant_id,empresa_id,warehouse_id,product_id,movement_date);

INSERT INTO warehouse(tenant_id,empresa_id,code,name)
SELECT e.tenant_id,e.id,'PRINCIPAL','Almacén Principal' FROM empresas e
ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('facturas_proveedores.crear','Crear facturas de proveedores','Crear facturas de proveedores','COMPRAS','facturas_proveedores','crear'),
('facturas_proveedores.editar','Editar facturas de proveedores','Editar borradores de facturas','COMPRAS','facturas_proveedores','editar'),
('facturas_proveedores.registrar','Registrar facturas de proveedores','Registrar facturas y cuentas por pagar','COMPRAS','facturas_proveedores','registrar'),
('facturas_proveedores.anular','Anular facturas de proveedores','Anular facturas registradas','COMPRAS','facturas_proveedores','anular'),
('recepciones.ver','Ver recepciones','Consultar recepciones de mercancía','COMPRAS','recepciones','ver'),
('recepciones.crear','Crear recepciones','Crear recepciones en borrador','COMPRAS','recepciones','crear'),
('recepciones.editar','Editar recepciones','Editar recepciones en borrador','COMPRAS','recepciones','editar'),
('recepciones.confirmar','Confirmar recepciones','Confirmar entradas de inventario','COMPRAS','recepciones','confirmar'),
('recepciones.anular','Anular recepciones','Anular recepciones confirmadas','COMPRAS','recepciones','anular')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso IN('facturas_proveedores','recepciones')
WHERE upper(r.codigo)='ADMINISTRADOR' ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'COMPRAS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
