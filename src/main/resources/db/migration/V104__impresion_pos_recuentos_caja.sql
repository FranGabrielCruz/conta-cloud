ALTER TABLE cash_counts ADD COLUMN pos_print_snapshot TEXT;
ALTER TABLE billing_print_jobs DROP CONSTRAINT ck_billing_print_document_type;
ALTER TABLE billing_print_jobs ADD CONSTRAINT ck_billing_print_document_type CHECK(document_type IN ('SALES_INVOICE','SALES_RETURN','PAYMENT_RECEIPT','CASH_COUNT'));
