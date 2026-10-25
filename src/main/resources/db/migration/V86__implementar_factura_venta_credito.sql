ALTER TABLE sales_invoice
    ADD COLUMN payment_term_id UUID,
    ADD COLUMN credit_days INTEGER,
    ADD COLUMN due_date DATE;

ALTER TABLE sales_invoice
    ADD CONSTRAINT fk_sales_invoice_payment_term
        FOREIGN KEY(empresa_id,payment_term_id)
        REFERENCES condiciones_pago(empresa_id,id),
    ADD CONSTRAINT ck_sales_invoice_credit_days CHECK(credit_days IS NULL OR credit_days>0),
    ADD CONSTRAINT ck_sales_invoice_due_date CHECK(due_date IS NULL OR due_date>=invoice_date);

ALTER TABLE sales_invoice DROP CONSTRAINT ck_sales_invoice_amounts;
ALTER TABLE sales_invoice ADD CONSTRAINT ck_sales_invoice_amounts CHECK(
    subtotal>=0 AND discount_total>=0 AND surcharge_total>=0 AND tax_total>=0 AND total>0
    AND (
        (payment_condition='Contado' AND received_amount>=total AND change_amount=received_amount-total
            AND payment_term_id IS NULL AND credit_days IS NULL AND due_date IS NULL)
        OR
        (payment_condition='Crédito' AND received_amount=0 AND change_amount=0 AND customer_id IS NOT NULL
            AND payment_term_id IS NOT NULL AND credit_days>0 AND due_date IS NOT NULL)
    )
);

CREATE INDEX idx_sales_invoice_credit_due
    ON sales_invoice(tenant_id,empresa_id,customer_id,currency_id,due_date,id)
    WHERE payment_condition='Crédito' AND status='CONFIRMED';

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('facturas.facturar_credito','Facturar a crédito','Emitir facturas de venta totalmente a crédito','VENTAS','facturas','facturar_credito'),
('facturas.exceder_limite_credito','Autorizar exceso de crédito','Continuar cuando la política del cliente permite advertir el exceso','VENTAS','facturas','exceder_limite_credito')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR'
  AND p.codigo IN('facturas.facturar_credito','facturas.exceder_limite_credito')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
