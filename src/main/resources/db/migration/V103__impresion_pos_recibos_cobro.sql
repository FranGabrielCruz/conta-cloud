ALTER TABLE customer_receipt ADD COLUMN pos_print_snapshot TEXT;
ALTER TABLE customer_receipt ADD COLUMN pos_cash_register_id UUID;
ALTER TABLE customer_receipt ADD CONSTRAINT fk_receipt_pos_cash FOREIGN KEY(tenant_id,empresa_id,branch_id,pos_cash_register_id) REFERENCES cajas(tenant_id,empresa_id,sucursal_id,id);
ALTER TABLE billing_print_jobs DROP CONSTRAINT ck_billing_print_document_type;
ALTER TABLE billing_print_jobs ADD CONSTRAINT ck_billing_print_document_type CHECK(document_type IN ('SALES_INVOICE','SALES_RETURN','PAYMENT_RECEIPT'));
