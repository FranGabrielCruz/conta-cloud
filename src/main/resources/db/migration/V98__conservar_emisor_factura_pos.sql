-- Preserve issuer identity for newly confirmed invoices. Do not invent historical data for older invoices.
ALTER TABLE sales_invoice
 ADD COLUMN pos_issuer_name VARCHAR(200),
 ADD COLUMN pos_issuer_tax_id VARCHAR(60),
 ADD COLUMN pos_issuer_address VARCHAR(500),
 ADD COLUMN pos_issuer_phone VARCHAR(60),
 ADD COLUMN pos_issuer_country VARCHAR(2),
 ADD COLUMN pos_cash_register_name VARCHAR(100),
 ADD COLUMN pos_issuer_zone VARCHAR(80);
