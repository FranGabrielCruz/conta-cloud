CREATE TABLE sales_return_reason (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(150) NOT NULL,
    requires_observation BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_sales_return_reason_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT uk_sales_return_reason_code UNIQUE(tenant_id,empresa_id,code),
    CONSTRAINT uk_sales_return_reason_scope_id UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE sales_return_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_sales_return_sequence_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE sales_credit_note_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_sales_credit_note_sequence_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE sales_return (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    return_number VARCHAR(30) NOT NULL,
    sales_invoice_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    reason_id UUID NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    reason_name VARCHAR(150) NOT NULL,
    observation VARCHAR(1000),
    returns_inventory BOOLEAN NOT NULL,
    destination_warehouse_id UUID,
    customer_id UUID,
    customer_name VARCHAR(180) NOT NULL,
    customer_identification VARCHAR(50),
    currency_id UUID NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    currency_symbol VARCHAR(10),
    gross_subtotal NUMERIC(19,4) NOT NULL,
    discount_total NUMERIC(19,4) NOT NULL,
    surcharge_total NUMERIC(19,4) NOT NULL,
    tax_total NUMERIC(19,4) NOT NULL,
    total NUMERIC(19,4) NOT NULL,
    operation_key UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    confirmed_by UUID NOT NULL,
    CONSTRAINT ck_sales_return_status CHECK(status='CONFIRMED'),
    CONSTRAINT ck_sales_return_inventory CHECK((returns_inventory AND destination_warehouse_id IS NOT NULL) OR (NOT returns_inventory AND destination_warehouse_id IS NULL)),
    CONSTRAINT ck_sales_return_amounts CHECK(gross_subtotal>=0 AND discount_total>=0 AND surcharge_total>=0 AND tax_total>=0 AND total>0),
    CONSTRAINT fk_sales_return_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_sales_return_invoice FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_return_reason FOREIGN KEY(tenant_id,empresa_id,reason_id) REFERENCES sales_return_reason(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_return_warehouse FOREIGN KEY(tenant_id,empresa_id,destination_warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_return_customer FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_return_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_sales_return_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    CONSTRAINT uk_sales_return_number UNIQUE(tenant_id,empresa_id,return_number),
    CONSTRAINT uk_sales_return_operation UNIQUE(tenant_id,empresa_id,operation_key),
    CONSTRAINT uk_sales_return_scope_id UNIQUE(tenant_id,empresa_id,id)
);
CREATE INDEX idx_sales_return_invoice ON sales_return(tenant_id,empresa_id,sales_invoice_id,confirmed_at DESC,id DESC);

CREATE TABLE sales_return_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_return_id UUID NOT NULL,
    sales_invoice_line_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_code VARCHAR(24) NOT NULL,
    description VARCHAR(300) NOT NULL,
    unit_of_measure_id UUID NOT NULL,
    unit_name VARCHAR(80) NOT NULL,
    returned_quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    gross_subtotal NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL,
    surcharge_amount NUMERIC(19,4) NOT NULL,
    taxable_base_amount NUMERIC(19,4) NOT NULL,
    tax_rate NUMERIC(9,6) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL,
    total NUMERIC(19,4) NOT NULL,
    track_inventory BOOLEAN NOT NULL,
    line_order INTEGER NOT NULL,
    CONSTRAINT ck_sales_return_line_values CHECK(returned_quantity>0 AND unit_price>=0 AND gross_subtotal>=0 AND discount_amount>=0 AND surcharge_amount>=0 AND taxable_base_amount>=0 AND tax_rate>=0 AND tax_amount>=0 AND total>=0 AND line_order>0),
    CONSTRAINT fk_sales_return_line_return FOREIGN KEY(tenant_id,empresa_id,sales_return_id) REFERENCES sales_return(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_return_line_invoice_line FOREIGN KEY(sales_invoice_line_id) REFERENCES sales_invoice_line(id),
    CONSTRAINT fk_sales_return_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_return_line_unit FOREIGN KEY(tenant_id,empresa_id,unit_of_measure_id) REFERENCES unit_of_measure(tenant_id,empresa_id,id),
    CONSTRAINT uk_sales_return_line_invoice_line UNIQUE(sales_return_id,sales_invoice_line_id),
    CONSTRAINT uk_sales_return_line_order UNIQUE(sales_return_id,line_order)
);
CREATE INDEX idx_sales_return_line_invoice_line ON sales_return_line(tenant_id,empresa_id,sales_invoice_line_id);

CREATE TABLE sales_credit_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    credit_note_number VARCHAR(30) NOT NULL,
    sales_return_id UUID NOT NULL,
    sales_invoice_id UUID NOT NULL,
    customer_id UUID,
    currency_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK(amount>0),
    applied_receivable_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK(applied_receivable_amount>=0),
    customer_credit_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK(customer_credit_amount>=0),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK(status='CONFIRMED'),
    confirmed_at TIMESTAMPTZ NOT NULL,
    confirmed_by UUID NOT NULL,
    CONSTRAINT fk_sales_credit_note_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_sales_credit_note_return FOREIGN KEY(tenant_id,empresa_id,sales_return_id) REFERENCES sales_return(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_credit_note_invoice FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_credit_note_customer FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_credit_note_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_sales_credit_note_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    CONSTRAINT uk_sales_credit_note_number UNIQUE(tenant_id,empresa_id,credit_note_number),
    CONSTRAINT uk_sales_credit_note_return UNIQUE(sales_return_id),
    CONSTRAINT uk_sales_credit_note_scope_id UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE customer_credit_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    sales_credit_note_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK(amount>0),
    available_amount NUMERIC(19,4) NOT NULL CHECK(available_amount>=0 AND available_amount<=amount),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT fk_customer_credit_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_customer_credit_customer FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_credit_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_customer_credit_note FOREIGN KEY(tenant_id,empresa_id,sales_credit_note_id) REFERENCES sales_credit_note(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_credit_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    CONSTRAINT uk_customer_credit_note UNIQUE(sales_credit_note_id)
);
CREATE INDEX idx_customer_credit_available ON customer_credit_ledger(tenant_id,empresa_id,customer_id,currency_id) WHERE available_amount>0;

CREATE TRIGGER trg_sales_return_reason_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_return_reason
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_return_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_return
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_return_line_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_return_line
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_credit_note_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_credit_note
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_customer_credit_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer_credit_ledger
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO sales_return_reason(tenant_id,empresa_id,code,name,requires_observation)
SELECT e.tenant_id,e.id,v.code,v.name,v.requires_observation
FROM empresas e CROSS JOIN (VALUES
    ('DEFECTIVE','Producto defectuoso',FALSE),
    ('WRONG_PRODUCT','Producto incorrecto',FALSE),
    ('DAMAGED','Producto dañado',FALSE),
    ('CUSTOMER_DISSATISFIED','Cliente no satisfecho',FALSE),
    ('BILLING_ERROR','Error de facturación',FALSE),
    ('OTHER','Otro',TRUE)
) AS v(code,name,requires_observation)
ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('devoluciones_venta.ver','Ver devoluciones de venta','Consultar facturas elegibles y devoluciones de venta','VENTAS','devoluciones_venta','ver'),
('devoluciones_venta.crear','Crear devoluciones de venta','Preparar devoluciones desde facturas confirmadas','VENTAS','devoluciones_venta','crear'),
('devoluciones_venta.confirmar','Confirmar devoluciones de venta','Confirmar devolución, nota de crédito e inventario aplicable','VENTAS','devoluciones_venta','confirmar'),
('devoluciones_venta.pdf','Generar PDF de devolución','Generar o reimprimir el documento de devolución','VENTAS','devoluciones_venta','pdf')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='devoluciones_venta'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
