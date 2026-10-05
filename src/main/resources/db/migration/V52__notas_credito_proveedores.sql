CREATE TABLE supplier_credit_note_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(tenant_id,empresa_id),
    FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE supplier_credit_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    internal_number VARCHAR(30) NOT NULL,
    supplier_id UUID NOT NULL,
    related_invoice_id UUID,
    credit_date DATE NOT NULL,
    supplier_credit_number VARCHAR(100) NOT NULL,
    normalized_supplier_credit_number VARCHAR(100) NOT NULL,
    fiscal_number VARCHAR(50),
    currency_id UUID NOT NULL,
    exchange_rate NUMERIC(19,8),
    reason VARCHAR(40) NOT NULL,
    other_reason VARCHAR(250),
    notes VARCHAR(1000),
    subtotal NUMERIC(19,4) NOT NULL DEFAULT 0,
    discount_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    total NUMERIC(19,4) NOT NULL DEFAULT 0,
    applied_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    confirmed_at TIMESTAMPTZ,
    confirmed_by UUID,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_supplier_credit_note_status CHECK(status IN('DRAFT','AVAILABLE','PARTIALLY_APPLIED','APPLIED','VOIDED')),
    CONSTRAINT ck_supplier_credit_note_reason CHECK(reason IN('GOODS_RETURN','POST_PURCHASE_DISCOUNT','PRICE_DIFFERENCE','BONUS','INVOICE_CORRECTION','DEFECTIVE_GOODS','OTHER')),
    CONSTRAINT ck_supplier_credit_note_amounts CHECK(subtotal>=0 AND discount_total>=0 AND tax_total>=0 AND total>=0 AND applied_amount>=0 AND applied_amount<=total),
    CONSTRAINT ck_supplier_credit_note_exchange CHECK(exchange_rate IS NULL OR exchange_rate>0),
    CONSTRAINT ck_supplier_credit_note_other_reason CHECK(reason<>'OTHER' OR btrim(coalesce(other_reason,''))<>''),
    CONSTRAINT fk_supplier_credit_note_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_supplier_credit_note_supplier FOREIGN KEY(tenant_id,empresa_id,supplier_id) REFERENCES supplier(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_credit_note_invoice FOREIGN KEY(tenant_id,empresa_id,related_invoice_id) REFERENCES purchase_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_credit_note_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_supplier_credit_note_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_supplier_credit_note_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
    CONSTRAINT fk_supplier_credit_note_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    CONSTRAINT fk_supplier_credit_note_voided_by FOREIGN KEY(voided_by) REFERENCES usuarios(id),
    CONSTRAINT uk_supplier_credit_note_number UNIQUE(tenant_id,empresa_id,internal_number),
    CONSTRAINT uk_supplier_credit_note_supplier_document UNIQUE(tenant_id,empresa_id,supplier_id,normalized_supplier_credit_number),
    CONSTRAINT uk_supplier_credit_note_scope UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE supplier_credit_note_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_credit_note_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    purchase_invoice_line_id UUID,
    product_id UUID,
    product_code_snapshot VARCHAR(24),
    description_snapshot VARCHAR(500) NOT NULL,
    unit_snapshot VARCHAR(120),
    invoiced_quantity_snapshot NUMERIC(19,4),
    quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_id UUID,
    tax_name_snapshot VARCHAR(120),
    tax_rate_snapshot NUMERIC(9,6) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    line_subtotal NUMERIC(19,4) NOT NULL,
    line_total NUMERIC(19,4) NOT NULL,
    line_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_supplier_credit_note_line_values CHECK(quantity>0 AND unit_price>=0 AND discount_amount>=0 AND tax_amount>=0 AND line_subtotal>=0 AND line_total>0),
    CONSTRAINT fk_supplier_credit_note_line_note FOREIGN KEY(tenant_id,empresa_id,supplier_credit_note_id) REFERENCES supplier_credit_note(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_credit_note_line_invoice_line FOREIGN KEY(purchase_invoice_line_id) REFERENCES purchase_invoice_line(id),
    CONSTRAINT fk_supplier_credit_note_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_credit_note_line_tax FOREIGN KEY(empresa_id,tax_id) REFERENCES impuestos(empresa_id,id),
    CONSTRAINT uk_supplier_credit_note_line_order UNIQUE(supplier_credit_note_id,line_order)
);

CREATE TABLE supplier_credit_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    supplier_credit_note_id UUID NOT NULL,
    purchase_invoice_id UUID NOT NULL,
    accounts_payable_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by UUID NOT NULL,
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_at TIMESTAMPTZ,
    reversed_by UUID,
    reversal_reason VARCHAR(500),
    CONSTRAINT ck_supplier_credit_application_amount CHECK(amount>0),
    CONSTRAINT ck_supplier_credit_application_reversal CHECK((reversed=false AND reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL) OR (reversed=true AND reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND reversal_reason IS NOT NULL)),
    CONSTRAINT fk_supplier_credit_application_note FOREIGN KEY(tenant_id,empresa_id,supplier_credit_note_id) REFERENCES supplier_credit_note(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_credit_application_invoice FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_id) REFERENCES purchase_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_credit_application_payable FOREIGN KEY(accounts_payable_id) REFERENCES accounts_payable(id),
    CONSTRAINT fk_supplier_credit_application_applied_by FOREIGN KEY(applied_by) REFERENCES usuarios(id),
    CONSTRAINT fk_supplier_credit_application_reversed_by FOREIGN KEY(reversed_by) REFERENCES usuarios(id)
);

CREATE INDEX idx_supplier_credit_note_list ON supplier_credit_note(tenant_id,empresa_id,credit_date DESC);
CREATE INDEX idx_supplier_credit_note_available ON supplier_credit_note(tenant_id,empresa_id,supplier_id,currency_id,status);
CREATE INDEX idx_supplier_credit_application_invoice ON supplier_credit_application(tenant_id,empresa_id,purchase_invoice_id,reversed);
CREATE INDEX idx_supplier_credit_line_invoice_line ON supplier_credit_note_line(purchase_invoice_line_id);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('notas_credito_proveedores.crear','Crear notas de crédito de proveedores','Crear notas de crédito en borrador','COMPRAS','notas_credito_proveedores','crear'),
('notas_credito_proveedores.editar','Editar notas de crédito de proveedores','Editar notas de crédito en borrador','COMPRAS','notas_credito_proveedores','editar'),
('notas_credito_proveedores.confirmar','Confirmar notas de crédito de proveedores','Confirmar créditos recibidos de proveedores','COMPRAS','notas_credito_proveedores','confirmar'),
('notas_credito_proveedores.aplicar','Aplicar notas de crédito de proveedores','Aplicar créditos a facturas pendientes','COMPRAS','notas_credito_proveedores','aplicar'),
('notas_credito_proveedores.anular','Anular notas de crédito de proveedores','Anular créditos sin aplicaciones activas','COMPRAS','notas_credito_proveedores','anular'),
('notas_credito_proveedores.imprimir','Imprimir notas de crédito de proveedores','Imprimir notas de crédito de proveedores','COMPRAS','notas_credito_proveedores','imprimir')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso='notas_credito_proveedores'
WHERE upper(r.codigo)='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
