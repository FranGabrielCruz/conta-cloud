CREATE TABLE pos_terminal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    cash_register_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT ck_pos_terminal_name CHECK (btrim(name)<>''),
    CONSTRAINT fk_pos_terminal_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_pos_terminal_branch FOREIGN KEY(empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_pos_terminal_cash FOREIGN KEY(tenant_id,empresa_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_pos_terminal_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    CONSTRAINT uk_pos_terminal_scope_id UNIQUE(tenant_id,empresa_id,id),
    CONSTRAINT uk_pos_terminal_token UNIQUE(token_hash)
);
CREATE INDEX idx_pos_terminal_cash ON pos_terminal(tenant_id,empresa_id,cash_register_id,active);

ALTER TABLE movimientos_financieros DROP CONSTRAINT ck_movimientos_financieros_origen;
ALTER TABLE movimientos_financieros ADD CONSTRAINT ck_movimientos_financieros_origen CHECK (tipo_origen IN
    ('MANUAL','CUSTOMER_RECEIPT','SUPPLIER_PAYMENT','SALES_INVOICE','TRANSFER','OPENING_BALANCE','ADJUSTMENT','OTHER'));

CREATE TABLE sales_invoice_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_sales_invoice_sequence_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE sales_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    internal_number VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    invoice_date DATE NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    confirmed_by UUID NOT NULL,
    terminal_id UUID NOT NULL,
    cash_register_id UUID NOT NULL,
    cash_register_session_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    customer_id UUID,
    customer_name VARCHAR(180) NOT NULL,
    customer_identification VARCHAR(50),
    seller_name VARCHAR(180) NOT NULL,
    currency_id UUID NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    currency_symbol VARCHAR(10),
    payment_condition VARCHAR(30) NOT NULL DEFAULT 'Contado',
    note VARCHAR(1000),
    subtotal NUMERIC(19,4) NOT NULL,
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    surcharge_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    total NUMERIC(19,4) NOT NULL,
    received_amount NUMERIC(19,4) NOT NULL,
    change_amount NUMERIC(19,4) NOT NULL,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sales_invoice_status CHECK(status IN('CONFIRMED','VOIDED')),
    CONSTRAINT ck_sales_invoice_amounts CHECK(subtotal>=0 AND discount_total>=0 AND surcharge_total>=0 AND tax_total>=0 AND total>0 AND received_amount>=total AND change_amount=received_amount-total),
    CONSTRAINT fk_sales_invoice_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_sales_invoice_terminal FOREIGN KEY(tenant_id,empresa_id,terminal_id) REFERENCES pos_terminal(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_invoice_cash FOREIGN KEY(tenant_id,empresa_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_invoice_session FOREIGN KEY(tenant_id,empresa_id,cash_register_session_id) REFERENCES cash_register_session(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_invoice_branch FOREIGN KEY(empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_sales_invoice_warehouse FOREIGN KEY(tenant_id,empresa_id,warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_invoice_customer FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_invoice_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_sales_invoice_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    CONSTRAINT uk_sales_invoice_number UNIQUE(tenant_id,empresa_id,internal_number),
    CONSTRAINT uk_sales_invoice_idempotency UNIQUE(tenant_id,empresa_id,idempotency_key),
    CONSTRAINT uk_sales_invoice_scope_id UNIQUE(tenant_id,empresa_id,id)
);
CREATE INDEX idx_sales_invoice_list ON sales_invoice(tenant_id,empresa_id,invoice_date DESC,created_at DESC);
CREATE INDEX idx_sales_invoice_session ON sales_invoice(tenant_id,empresa_id,cash_register_session_id,created_at);

CREATE TABLE sales_invoice_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_invoice_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_code VARCHAR(24) NOT NULL,
    description VARCHAR(300) NOT NULL,
    unit_of_measure_id UUID NOT NULL,
    unit_name VARCHAR(80) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_id UUID,
    tax_name VARCHAR(120),
    tax_rate NUMERIC(9,6) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    subtotal NUMERIC(19,4) NOT NULL,
    total NUMERIC(19,4) NOT NULL,
    track_inventory BOOLEAN NOT NULL,
    warehouse_id UUID NOT NULL,
    line_order INTEGER NOT NULL,
    CONSTRAINT ck_sales_invoice_line_values CHECK(quantity>0 AND unit_price>=0 AND discount_amount>=0 AND tax_rate>=0 AND tax_amount>=0 AND subtotal>=0 AND total>=0 AND line_order>0),
    CONSTRAINT fk_sales_invoice_line_invoice FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_invoice_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_invoice_line_unit FOREIGN KEY(tenant_id,empresa_id,unit_of_measure_id) REFERENCES unit_of_measure(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_invoice_line_tax FOREIGN KEY(empresa_id,tax_id) REFERENCES impuestos(empresa_id,id),
    CONSTRAINT fk_sales_invoice_line_warehouse FOREIGN KEY(tenant_id,empresa_id,warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT uk_sales_invoice_line_order UNIQUE(sales_invoice_id,line_order)
);

CREATE TABLE sales_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_invoice_id UUID NOT NULL,
    cash_register_id UUID NOT NULL,
    cash_register_session_id UUID NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'CASH',
    amount NUMERIC(19,4) NOT NULL,
    received_amount NUMERIC(19,4) NOT NULL,
    change_amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT ck_sales_payment_method CHECK(payment_method='CASH'),
    CONSTRAINT ck_sales_payment_amounts CHECK(amount>0 AND received_amount>=amount AND change_amount=received_amount-amount),
    CONSTRAINT fk_sales_payment_invoice FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_payment_cash FOREIGN KEY(tenant_id,empresa_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_payment_session FOREIGN KEY(tenant_id,empresa_id,cash_register_session_id) REFERENCES cash_register_session(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_payment_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    CONSTRAINT uk_sales_payment_invoice UNIQUE(sales_invoice_id)
);

CREATE TRIGGER trg_pos_terminal_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON pos_terminal
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_invoice_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_invoice
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_invoice_line_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_invoice_line
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_payment_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_payment
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('facturas.ver','Ver facturación','Acceder al punto de venta y consultar facturas','VENTAS','facturas','ver'),
('facturas.configurar_terminal','Configurar terminal de venta','Configurar este navegador como terminal de venta','VENTAS','facturas','configurar_terminal'),
('facturas.abrir_caja','Abrir caja desde facturación','Abrir un turno de caja desde el punto de venta','VENTAS','facturas','abrir_caja'),
('facturas.crear','Crear facturas','Preparar facturas de venta','VENTAS','facturas','crear'),
('facturas.cobrar','Cobrar facturas','Confirmar y cobrar facturas de contado','VENTAS','facturas','cobrar'),
('facturas.pdf','Generar PDF de factura','Generar o reimprimir el comprobante PDF','VENTAS','facturas','pdf')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='facturas'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'VENTAS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
