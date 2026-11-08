ALTER TABLE sales_credit_note ALTER COLUMN sales_return_id DROP NOT NULL;
ALTER TABLE sales_credit_note ADD COLUMN origin_kind VARCHAR(20) NOT NULL DEFAULT 'SALES_RETURN';
ALTER TABLE sales_credit_note ADD COLUMN issue_date DATE;
ALTER TABLE sales_credit_note ADD COLUMN document_snapshot TEXT;
ALTER TABLE sales_credit_note ADD CONSTRAINT ck_credit_note_origin CHECK
    ((origin_kind='SALES_RETURN' AND sales_return_id IS NOT NULL) OR (origin_kind='FINANCIAL' AND sales_return_id IS NULL AND issue_date IS NOT NULL AND document_snapshot IS NOT NULL));
ALTER TABLE sales_credit_note ADD CONSTRAINT uk_credit_note_invoice_scope UNIQUE(tenant_id,empresa_id,sales_invoice_id,id);
ALTER TABLE sales_invoice_line ADD CONSTRAINT uk_invoice_line_credit_scope UNIQUE(tenant_id,empresa_id,sales_invoice_id,id);

CREATE TABLE sales_credit_note_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    sales_credit_note_id UUID NOT NULL, sales_invoice_id UUID NOT NULL, sales_invoice_line_id UUID NOT NULL,
    credited_quantity NUMERIC(19,4) NOT NULL CHECK(credited_quantity>0),
    gross_amount NUMERIC(19,4) NOT NULL CHECK(gross_amount>=0),
    discount_amount NUMERIC(19,4) NOT NULL CHECK(discount_amount>=0),
    surcharge_amount NUMERIC(19,4) NOT NULL CHECK(surcharge_amount>=0),
    tax_amount NUMERIC(19,4) NOT NULL CHECK(tax_amount>=0),
    credited_amount NUMERIC(19,4) NOT NULL CHECK(credited_amount>=0),
    CONSTRAINT fk_credit_line_note FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id,sales_credit_note_id)
        REFERENCES sales_credit_note(tenant_id,empresa_id,sales_invoice_id,id),
    CONSTRAINT fk_credit_line_invoice FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id,sales_invoice_line_id)
        REFERENCES sales_invoice_line(tenant_id,empresa_id,sales_invoice_id,id),
    CONSTRAINT uk_credit_note_line UNIQUE(sales_credit_note_id,sales_invoice_line_id)
);
CREATE INDEX idx_credit_line_available ON sales_credit_note_line(tenant_id,empresa_id,sales_invoice_line_id);
CREATE INDEX idx_credit_note_invoice ON sales_credit_note(tenant_id,empresa_id,sales_invoice_id,confirmed_at DESC,id DESC);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
 ('notas_credito.crear','Preparar notas de crédito','Preparar crédito financiero desde una factura','VENTAS','notas_credito','crear'),
 ('notas_credito.emitir','Emitir notas de crédito','Emitir crédito financiero de forma transaccional','VENTAS','notas_credito','emitir'),
 ('notas_credito.pdf','Ver PDF de nota de crédito','Consultar el documento histórico en formato grande','VENTAS','notas_credito','pdf')
ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='notas_credito'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
