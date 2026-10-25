CREATE TABLE customer_accounts_receivable (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_invoice_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    original_amount NUMERIC(19,4) NOT NULL,
    applied_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_ar_amounts CHECK(original_amount>0 AND applied_amount>=0 AND applied_amount<=original_amount),
    CONSTRAINT ck_customer_ar_status CHECK(status IN('PENDING','PARTIALLY_PAID','PAID','VOIDED')),
    CONSTRAINT fk_customer_ar_invoice FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_ar_customer FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_ar_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT uk_customer_ar_invoice UNIQUE(tenant_id,empresa_id,sales_invoice_id),
    CONSTRAINT uk_customer_ar_scope UNIQUE(tenant_id,empresa_id,id)
);
CREATE INDEX idx_customer_ar_pending ON customer_accounts_receivable(tenant_id,empresa_id,customer_id,currency_id,status,due_date,id);
CREATE TRIGGER trg_customer_ar_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer_accounts_receivable
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

CREATE TABLE customer_receipt_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_customer_receipt_sequence_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE customer_receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    internal_number VARCHAR(30) NOT NULL,
    customer_id UUID NOT NULL,
    customer_name_snapshot VARCHAR(180) NOT NULL,
    customer_identification_snapshot VARCHAR(50),
    customer_identification_normalized VARCHAR(50),
    currency_id UUID NOT NULL,
    currency_code_snapshot VARCHAR(3) NOT NULL,
    receipt_date DATE NOT NULL,
    received_amount NUMERIC(19,4) NOT NULL,
    applied_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_amount NUMERIC(19,4) NOT NULL,
    reference VARCHAR(100),
    notes VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    idempotency_key UUID NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    confirmed_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_receipt_amounts CHECK(received_amount>0 AND applied_amount>=0 AND available_amount>=0 AND received_amount=applied_amount+available_amount),
    CONSTRAINT ck_customer_receipt_status CHECK(status IN('AVAILABLE','PARTIALLY_APPLIED','APPLIED','VOIDED')),
    CONSTRAINT fk_customer_receipt_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_customer_receipt_customer FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_customer_receipt_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    CONSTRAINT fk_customer_receipt_voided_by FOREIGN KEY(voided_by) REFERENCES usuarios(id),
    CONSTRAINT uk_customer_receipt_number UNIQUE(tenant_id,empresa_id,internal_number),
    CONSTRAINT uk_customer_receipt_idempotency UNIQUE(tenant_id,empresa_id,idempotency_key),
    CONSTRAINT uk_customer_receipt_scope UNIQUE(tenant_id,empresa_id,id)
);
CREATE INDEX idx_customer_receipt_list ON customer_receipt(tenant_id,empresa_id,confirmed_at DESC,id DESC);
CREATE INDEX idx_customer_receipt_customer ON customer_receipt(tenant_id,empresa_id,customer_id,currency_id,status);
CREATE TRIGGER trg_customer_receipt_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer_receipt
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

CREATE TABLE customer_receipt_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    customer_receipt_id UUID NOT NULL,
    accounts_receivable_id UUID NOT NULL,
    sales_invoice_id UUID NOT NULL,
    invoice_number_snapshot VARCHAR(30) NOT NULL,
    invoice_date_snapshot DATE NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_balance NUMERIC(19,4) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    remaining_balance NUMERIC(19,4) NOT NULL,
    applied_by UUID NOT NULL,
    reversed BOOLEAN NOT NULL DEFAULT FALSE,
    reversed_at TIMESTAMPTZ,
    reversed_by UUID,
    reversal_reason VARCHAR(500),
    CONSTRAINT ck_customer_receipt_application_amounts CHECK(amount>0 AND previous_balance>=amount AND remaining_balance=previous_balance-amount),
    CONSTRAINT fk_customer_receipt_application_receipt FOREIGN KEY(tenant_id,empresa_id,customer_receipt_id) REFERENCES customer_receipt(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_application_ar FOREIGN KEY(tenant_id,empresa_id,accounts_receivable_id) REFERENCES customer_accounts_receivable(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_application_invoice FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_application_user FOREIGN KEY(applied_by) REFERENCES usuarios(id),
    CONSTRAINT uk_customer_receipt_application UNIQUE(customer_receipt_id,sales_invoice_id,applied_at),
    CONSTRAINT uk_customer_receipt_application_scope UNIQUE(tenant_id,empresa_id,id)
);
CREATE INDEX idx_customer_receipt_application_receipt ON customer_receipt_application(tenant_id,empresa_id,customer_receipt_id,applied_at,id);
CREATE TRIGGER trg_customer_receipt_application_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer_receipt_application
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

CREATE TABLE customer_receipt_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    customer_receipt_id UUID NOT NULL,
    payment_method_id UUID NOT NULL,
    method_code VARCHAR(30) NOT NULL,
    method_name VARCHAR(100) NOT NULL,
    method_type VARCHAR(24) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    cash_register_id UUID,
    cash_register_session_id UUID,
    bank_account_id UUID,
    destination_snapshot VARCHAR(180),
    reference VARCHAR(100),
    line_order INTEGER NOT NULL,
    financial_movement_id UUID,
    CONSTRAINT ck_customer_receipt_payment_amount CHECK(amount>0),
    CONSTRAINT ck_customer_receipt_payment_destination CHECK((cash_register_id IS NOT NULL AND bank_account_id IS NULL) OR (cash_register_id IS NULL AND bank_account_id IS NOT NULL)),
    CONSTRAINT fk_customer_receipt_payment_receipt FOREIGN KEY(tenant_id,empresa_id,customer_receipt_id) REFERENCES customer_receipt(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_payment_method FOREIGN KEY(tenant_id,empresa_id,payment_method_id) REFERENCES sales_payment_method(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_payment_cash FOREIGN KEY(tenant_id,empresa_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_payment_session FOREIGN KEY(tenant_id,empresa_id,cash_register_session_id) REFERENCES cash_register_session(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_payment_bank FOREIGN KEY(tenant_id,empresa_id,bank_account_id) REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_receipt_payment_movement FOREIGN KEY(financial_movement_id) REFERENCES movimientos_financieros(id),
    CONSTRAINT uk_customer_receipt_payment_order UNIQUE(customer_receipt_id,line_order),
    CONSTRAINT uk_customer_receipt_payment_scope UNIQUE(tenant_id,empresa_id,id)
);
CREATE INDEX idx_customer_receipt_payment_receipt ON customer_receipt_payment(tenant_id,empresa_id,customer_receipt_id,line_order);
CREATE TRIGGER trg_customer_receipt_payment_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer_receipt_payment
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('recibos.ver','Ver recibos/cobros','Consultar recibos y cobros de clientes','VENTAS','recibos','ver'),
('recibos.crear','Crear cobros','Registrar dinero recibido de clientes','VENTAS','recibos','crear'),
('recibos.aplicar','Aplicar cobros','Aplicar cobros o anticipos a facturas pendientes','VENTAS','recibos','aplicar'),
('recibos.imprimir','Reimprimir recibos','Generar el PDF de recibos de cobro','VENTAS','recibos','imprimir'),
('recibos.anular','Anular recibos','Revertir recibos de cobro de forma controlada','VENTAS','recibos','anular'),
('recibos.ver_otras_sucursales','Ver cobros de otras sucursales','Consultar cobros fuera de las sucursales asignadas','VENTAS','recibos','ver_otras_sucursales')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='recibos'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
