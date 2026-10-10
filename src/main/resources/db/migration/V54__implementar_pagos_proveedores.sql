ALTER TABLE supplier_payment DROP CONSTRAINT uk_supplier_payment_invoice;
ALTER TABLE supplier_payment DROP CONSTRAINT ck_supplier_payment_status;
ALTER TABLE supplier_payment DROP CONSTRAINT ck_supplier_payment_void;

ALTER TABLE supplier_payment
    ALTER COLUMN purchase_invoice_id DROP NOT NULL,
    ALTER COLUMN status TYPE VARCHAR(30),
    ADD COLUMN internal_number VARCHAR(30),
    ADD COLUMN payment_method VARCHAR(20),
    ADD COLUMN check_number VARCHAR(50),
    ADD COLUMN notes VARCHAR(1000),
    ADD COLUMN applied_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN confirmed_by UUID,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID;

WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY tenant_id,empresa_id ORDER BY created_at,id) AS number
    FROM supplier_payment
)
UPDATE supplier_payment p
SET internal_number='PG-' || lpad(numbered.number::text,6,'0'),
    payment_method=CASE WHEN source_type='CASH_REGISTER' THEN 'CASH' ELSE 'BANK_TRANSFER' END,
    applied_amount=monto,
    status='APPLIED',
    confirmed_at=created_at,
    confirmed_by=created_by,
    updated_at=created_at,
    updated_by=created_by
FROM numbered WHERE numbered.id=p.id;

ALTER TABLE supplier_payment
    ALTER COLUMN internal_number SET NOT NULL,
    ALTER COLUMN payment_method SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL,
    ADD CONSTRAINT ck_supplier_payment_status CHECK(status IN('DRAFT','AVAILABLE','PARTIALLY_APPLIED','APPLIED','VOIDED')),
    ADD CONSTRAINT ck_supplier_payment_method CHECK(payment_method IN('CASH','CARD','BANK_TRANSFER','CHECK','OTHER')),
    ADD CONSTRAINT ck_supplier_payment_amounts CHECK(monto>0 AND applied_amount>=0 AND applied_amount<=monto),
    ADD CONSTRAINT ck_supplier_payment_check CHECK(payment_method<>'CHECK' OR btrim(coalesce(check_number,''))<>''),
    ADD CONSTRAINT ck_supplier_payment_lifecycle CHECK(
        (status='DRAFT' AND confirmed_at IS NULL AND confirmed_by IS NULL AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
        OR (status IN('AVAILABLE','PARTIALLY_APPLIED','APPLIED') AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
        OR (status='VOIDED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND btrim(coalesce(void_reason,''))<>'')),
    ADD CONSTRAINT fk_supplier_payment_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_supplier_payment_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
    ADD CONSTRAINT uk_supplier_payment_number UNIQUE(tenant_id,empresa_id,internal_number),
    ADD CONSTRAINT uk_supplier_payment_scope UNIQUE(tenant_id,empresa_id,id);

CREATE UNIQUE INDEX uk_supplier_payment_active_check
    ON supplier_payment(tenant_id,empresa_id,bank_account_id,check_number)
    WHERE check_number IS NOT NULL AND status<>'VOIDED';

CREATE TABLE supplier_payment_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(tenant_id,empresa_id),
    FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

INSERT INTO supplier_payment_sequence(tenant_id,empresa_id,last_number)
SELECT tenant_id,empresa_id,count(*) FROM supplier_payment GROUP BY tenant_id,empresa_id;

CREATE TABLE supplier_payment_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    supplier_payment_id UUID NOT NULL,
    purchase_invoice_id UUID NOT NULL,
    accounts_payable_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_by UUID NOT NULL,
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_at TIMESTAMPTZ,
    reversed_by UUID,
    reversal_reason VARCHAR(500),
    CONSTRAINT ck_supplier_payment_application_amount CHECK(amount>0),
    CONSTRAINT ck_supplier_payment_application_reversal CHECK(
        (reversed=false AND reversed_at IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL)
        OR (reversed=true AND reversed_at IS NOT NULL AND reversed_by IS NOT NULL AND reversal_reason IS NOT NULL)),
    CONSTRAINT fk_supplier_payment_application_payment FOREIGN KEY(tenant_id,empresa_id,supplier_payment_id)
        REFERENCES supplier_payment(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_payment_application_invoice FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_id)
        REFERENCES purchase_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_supplier_payment_application_payable FOREIGN KEY(accounts_payable_id) REFERENCES accounts_payable(id),
    CONSTRAINT fk_supplier_payment_application_applied_by FOREIGN KEY(applied_by) REFERENCES usuarios(id),
    CONSTRAINT fk_supplier_payment_application_reversed_by FOREIGN KEY(reversed_by) REFERENCES usuarios(id)
);

CREATE TABLE supplier_payment_draft_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    supplier_payment_id UUID NOT NULL,
    purchase_invoice_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    CONSTRAINT ck_supplier_payment_draft_amount CHECK(amount>0),
    CONSTRAINT fk_supplier_payment_draft_payment FOREIGN KEY(tenant_id,empresa_id,supplier_payment_id)
        REFERENCES supplier_payment(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_payment_draft_invoice FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_id)
        REFERENCES purchase_invoice(tenant_id,empresa_id,id),
    CONSTRAINT uk_supplier_payment_draft_invoice UNIQUE(supplier_payment_id,purchase_invoice_id)
);

CREATE INDEX idx_supplier_payment_list ON supplier_payment(tenant_id,empresa_id,fecha DESC,internal_number DESC);
CREATE INDEX idx_supplier_payment_available ON supplier_payment(tenant_id,empresa_id,supplier_id,currency_id,status);
CREATE INDEX idx_supplier_payment_application_invoice ON supplier_payment_application(tenant_id,empresa_id,purchase_invoice_id,reversed);
CREATE INDEX idx_supplier_payment_application_payment ON supplier_payment_application(tenant_id,empresa_id,supplier_payment_id,reversed);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('pagos_proveedores.crear','Crear pagos a proveedores','Crear pagos a proveedores en borrador','COMPRAS','pagos_proveedores','crear'),
('pagos_proveedores.editar','Editar pagos a proveedores','Editar pagos a proveedores en borrador','COMPRAS','pagos_proveedores','editar'),
('pagos_proveedores.confirmar','Confirmar pagos a proveedores','Confirmar pagos y generar la salida financiera','COMPRAS','pagos_proveedores','confirmar'),
('pagos_proveedores.aplicar','Aplicar pagos a proveedores','Aplicar pagos disponibles a facturas pendientes','COMPRAS','pagos_proveedores','aplicar'),
('pagos_proveedores.anular','Anular pagos a proveedores','Revertir pagos, aplicaciones y movimientos financieros','COMPRAS','pagos_proveedores','anular'),
('pagos_proveedores.imprimir','Imprimir pagos a proveedores','Imprimir comprobantes de pagos a proveedores','COMPRAS','pagos_proveedores','imprimir')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso='pagos_proveedores'
WHERE upper(r.codigo)='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
