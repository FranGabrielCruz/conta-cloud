ALTER TABLE purchase_invoice
    ADD COLUMN payment_term_name_snapshot VARCHAR(100),
    ADD COLUMN payment_term_type_snapshot VARCHAR(20),
    ADD COLUMN payment_term_days_snapshot INTEGER,
    ADD COLUMN financial_status VARCHAR(30),
    ADD COLUMN registration_key UUID;

UPDATE purchase_invoice f
SET payment_term_name_snapshot=c.nombre,
    payment_term_type_snapshot=c.tipo,
    payment_term_days_snapshot=c.dias
FROM condiciones_pago c
WHERE f.empresa_id=c.empresa_id AND f.payment_term_id=c.id AND f.status<>'DRAFT';

UPDATE purchase_invoice
SET payment_term_name_snapshot=COALESCE(payment_term_name_snapshot,'Crédito'),
    payment_term_type_snapshot=COALESCE(payment_term_type_snapshot,'CREDIT'),
    payment_term_days_snapshot=COALESCE(payment_term_days_snapshot,0)
WHERE status<>'DRAFT';

UPDATE purchase_invoice f
SET financial_status=CASE
    WHEN EXISTS (SELECT 1 FROM accounts_payable a
                 WHERE a.tenant_id=f.tenant_id AND a.empresa_id=f.empresa_id
                   AND a.purchase_invoice_id=f.id AND a.status='PAID') THEN 'PAID'
    WHEN EXISTS (SELECT 1 FROM accounts_payable a
                 WHERE a.tenant_id=f.tenant_id AND a.empresa_id=f.empresa_id
                   AND a.purchase_invoice_id=f.id AND a.status='PARTIALLY_PAID') THEN 'PARTIALLY_PAID'
    ELSE 'PENDING' END
WHERE f.status<>'DRAFT';

ALTER TABLE purchase_invoice
    ADD CONSTRAINT ck_purchase_invoice_term_snapshot_type
        CHECK(payment_term_type_snapshot IS NULL OR payment_term_type_snapshot IN('CASH','CREDIT')),
    ADD CONSTRAINT ck_purchase_invoice_financial_status
        CHECK(financial_status IS NULL OR financial_status IN('PENDING','PARTIALLY_PAID','PAID')),
    ADD CONSTRAINT ck_purchase_invoice_registered_snapshot
        CHECK(status='DRAFT' OR (payment_term_name_snapshot IS NOT NULL
            AND payment_term_type_snapshot IS NOT NULL AND payment_term_days_snapshot IS NOT NULL
            AND financial_status IS NOT NULL));

CREATE UNIQUE INDEX uk_purchase_invoice_registration_key
    ON purchase_invoice(tenant_id,empresa_id,registration_key)
    WHERE registration_key IS NOT NULL;

CREATE TABLE supplier_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    purchase_invoice_id UUID NOT NULL,
    accounts_payable_id UUID,
    fecha DATE NOT NULL,
    currency_id UUID NOT NULL,
    monto NUMERIC(19,4) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    cash_register_id UUID,
    bank_account_id UUID,
    financial_movement_id UUID,
    referencia VARCHAR(100),
    status VARCHAR(12) NOT NULL DEFAULT 'REGISTERED',
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_supplier_payment_amount CHECK(monto>0),
    CONSTRAINT ck_supplier_payment_status CHECK(status IN('REGISTERED','VOIDED')),
    CONSTRAINT ck_supplier_payment_source CHECK(
        (source_type='CASH_REGISTER' AND cash_register_id IS NOT NULL AND bank_account_id IS NULL)
        OR (source_type='BANK_ACCOUNT' AND cash_register_id IS NULL AND bank_account_id IS NOT NULL)),
    CONSTRAINT ck_supplier_payment_void CHECK(
        (status='REGISTERED' AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
        OR (status='VOIDED' AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL)),
    CONSTRAINT fk_supplier_payment_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_supplier_payment_supplier FOREIGN KEY(tenant_id,empresa_id,supplier_id)
        REFERENCES supplier(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_payment_invoice FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_id)
        REFERENCES purchase_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_payment_accounts_payable FOREIGN KEY(accounts_payable_id) REFERENCES accounts_payable(id),
    CONSTRAINT fk_supplier_payment_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_supplier_payment_cash FOREIGN KEY(tenant_id,empresa_id,cash_register_id)
        REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_payment_bank FOREIGN KEY(tenant_id,empresa_id,bank_account_id)
        REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_payment_movement FOREIGN KEY(financial_movement_id) REFERENCES movimientos_financieros(id),
    CONSTRAINT fk_supplier_payment_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_supplier_payment_voided_by FOREIGN KEY(voided_by) REFERENCES usuarios(id),
    CONSTRAINT uk_supplier_payment_invoice UNIQUE(tenant_id,empresa_id,purchase_invoice_id),
    CONSTRAINT uk_supplier_payment_idempotency UNIQUE(tenant_id,empresa_id,idempotency_key)
);

CREATE INDEX idx_supplier_payment_supplier_fecha
    ON supplier_payment(tenant_id,empresa_id,supplier_id,fecha DESC);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('facturas_proveedores.pagar_contado','Pagar facturas al contado',
 'Registrar el pago y movimiento financiero de facturas de proveedores al contado',
 'COMPRAS','facturas_proveedores','pagar_contado')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.codigo='facturas_proveedores.pagar_contado'
WHERE upper(r.codigo)='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
