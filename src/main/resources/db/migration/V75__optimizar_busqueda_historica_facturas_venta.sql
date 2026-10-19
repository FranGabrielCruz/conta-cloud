ALTER TABLE sales_invoice
    ADD COLUMN customer_identification_normalized VARCHAR(50);

UPDATE sales_invoice
SET customer_identification_normalized = regexp_replace(COALESCE(customer_identification,''),'[^0-9]','','g');

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_sales_invoice_number_search
    ON sales_invoice(tenant_id,empresa_id,lower(internal_number));

CREATE INDEX idx_sales_invoice_customer_search
    ON sales_invoice USING gin(lower(customer_name) gin_trgm_ops);

CREATE INDEX idx_sales_invoice_identification_search
    ON sales_invoice(tenant_id,empresa_id,customer_identification_normalized);

CREATE INDEX idx_sales_invoice_history_filters
    ON sales_invoice(tenant_id,empresa_id,branch_id,status,invoice_date DESC,confirmed_at DESC,id DESC);
