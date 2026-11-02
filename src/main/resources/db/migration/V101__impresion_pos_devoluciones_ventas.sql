-- New return snapshots; existing returns remain explicitly distinguishable as legacy data.
ALTER TABLE sales_return ADD COLUMN pos_issuer_name VARCHAR(200);
ALTER TABLE sales_return ADD COLUMN pos_issuer_tax_id VARCHAR(60);
ALTER TABLE sales_return ADD COLUMN pos_issuer_address VARCHAR(500);
ALTER TABLE sales_return ADD COLUMN pos_issuer_phone VARCHAR(60);
ALTER TABLE sales_return ADD COLUMN pos_issuer_country VARCHAR(2);
ALTER TABLE sales_return ADD COLUMN pos_cash_register_name VARCHAR(100);
ALTER TABLE sales_return ADD COLUMN pos_issuer_zone VARCHAR(80);
ALTER TABLE sales_return ADD COLUMN pos_branch_id UUID;
ALTER TABLE sales_return ADD COLUMN pos_cash_register_id UUID;
ALTER TABLE sales_return ADD COLUMN pos_branch_name VARCHAR(150);
ALTER TABLE sales_return ADD COLUMN pos_operator_name VARCHAR(200);
ALTER TABLE sales_return ADD CONSTRAINT ck_sales_return_pos_origin CHECK (
 (pos_branch_id IS NULL AND pos_cash_register_id IS NULL) OR (pos_branch_id IS NOT NULL AND pos_cash_register_id IS NOT NULL));
ALTER TABLE sales_return ADD CONSTRAINT fk_sales_return_pos_register
 FOREIGN KEY(tenant_id,empresa_id,pos_branch_id,pos_cash_register_id) REFERENCES cajas(tenant_id,empresa_id,sucursal_id,id);
ALTER TABLE sales_return_line ADD COLUMN tax_name VARCHAR(120);
ALTER TABLE billing_print_jobs ADD COLUMN document_type VARCHAR(30) NOT NULL DEFAULT 'SALES_INVOICE';
ALTER TABLE billing_print_jobs ADD CONSTRAINT ck_billing_print_document_type CHECK(document_type IN ('SALES_INVOICE','SALES_RETURN'));
