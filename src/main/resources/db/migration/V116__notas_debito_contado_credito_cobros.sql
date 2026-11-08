ALTER TABLE sales_debit_note ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE sales_debit_note ADD COLUMN payment_condition VARCHAR(10) NOT NULL DEFAULT 'CREDIT' CHECK(payment_condition IN('CASH','CREDIT'));
ALTER TABLE sales_debit_note ADD COLUMN receivable_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY_INVOICE' CHECK(receivable_mode IN('LEGACY_INVOICE','DOCUMENT','NONE'));
ALTER TABLE sales_debit_note ADD CONSTRAINT ck_debit_note_condition CHECK((payment_condition='CREDIT' AND customer_id IS NOT NULL AND receivable_mode IN('LEGACY_INVOICE','DOCUMENT')) OR (payment_condition='CASH' AND receivable_mode='NONE'));
ALTER TABLE sales_debit_note ADD CONSTRAINT uk_debit_note_invoice_scope UNIQUE(tenant_id,empresa_id,sales_invoice_id,id);
ALTER TABLE customer_accounts_receivable ADD COLUMN sales_debit_note_id UUID;
ALTER TABLE customer_accounts_receivable ADD CONSTRAINT fk_customer_ar_debit_note FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id,sales_debit_note_id) REFERENCES sales_debit_note(tenant_id,empresa_id,sales_invoice_id,id);
ALTER TABLE customer_accounts_receivable DROP CONSTRAINT uk_customer_ar_invoice;
CREATE UNIQUE INDEX uk_customer_ar_invoice ON customer_accounts_receivable(tenant_id,empresa_id,sales_invoice_id) WHERE sales_debit_note_id IS NULL;
CREATE UNIQUE INDEX uk_customer_ar_debit_note ON customer_accounts_receivable(tenant_id,empresa_id,sales_debit_note_id) WHERE sales_debit_note_id IS NOT NULL;
ALTER TABLE movimientos_financieros ADD CONSTRAINT uk_debit_money_scope UNIQUE(tenant_id,empresa_id,id);
CREATE TABLE sales_debit_note_payment (
 id UUID PRIMARY KEY,tenant_id UUID NOT NULL,empresa_id UUID NOT NULL,sales_debit_note_id UUID NOT NULL,
 payment_method_id UUID NOT NULL,method_name VARCHAR(100) NOT NULL,method_type VARCHAR(24) NOT NULL,
 amount NUMERIC(19,4) NOT NULL CHECK(amount>0),received_amount NUMERIC(19,4),change_amount NUMERIC(19,4) NOT NULL CHECK(change_amount>=0),
 reference VARCHAR(100),cash_register_session_id UUID,financial_movement_id UUID NOT NULL,
 FOREIGN KEY(tenant_id,empresa_id,sales_debit_note_id) REFERENCES sales_debit_note(tenant_id,empresa_id,id),
 FOREIGN KEY(tenant_id,empresa_id,payment_method_id) REFERENCES sales_payment_method(tenant_id,empresa_id,id),
 FOREIGN KEY(tenant_id,empresa_id,financial_movement_id) REFERENCES movimientos_financieros(tenant_id,empresa_id,id),
 FOREIGN KEY(tenant_id,empresa_id,cash_register_session_id) REFERENCES cash_register_session(tenant_id,empresa_id,id),
 UNIQUE(tenant_id,empresa_id,financial_movement_id));
CREATE INDEX idx_debit_payment_document ON sales_debit_note_payment(tenant_id,empresa_id,sales_debit_note_id);
CREATE INDEX idx_debit_payment_session ON sales_debit_note_payment(tenant_id,empresa_id,cash_register_session_id);
CREATE TRIGGER trg_debit_payment_scope BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_debit_note_payment FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
ALTER TABLE movimientos_financieros DROP CONSTRAINT ck_movimientos_financieros_origen;
ALTER TABLE movimientos_financieros ADD CONSTRAINT ck_movimientos_financieros_origen CHECK(tipo_origen IN('MANUAL','CUSTOMER_RECEIPT','SUPPLIER_PAYMENT','SALES_INVOICE','SALES_PAYMENT','TRANSFER','OPENING_BALANCE','ADJUSTMENT','REVERSAL','CASH_VOUCHER','DEBIT_NOTE_PAYMENT','OTHER'));
CREATE UNIQUE INDEX uk_debit_payment_movement ON movimientos_financieros(tenant_id,empresa_id,tipo_origen,origen_id) WHERE tipo_origen='DEBIT_NOTE_PAYMENT';
