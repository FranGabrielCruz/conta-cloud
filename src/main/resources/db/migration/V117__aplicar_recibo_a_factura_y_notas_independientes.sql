-- A receipt can cover the original invoice and independent debit notes in one transaction.
-- The receivable identifies the paid document; the invoice remains the immutable origin link.
ALTER TABLE customer_receipt_application DROP CONSTRAINT uk_customer_receipt_application;
ALTER TABLE customer_receipt_application ADD CONSTRAINT uk_customer_receipt_application
 UNIQUE(customer_receipt_id,accounts_receivable_id,applied_at);
