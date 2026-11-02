-- Supports session-scoped receipt lookup without scanning historical company receipts.
CREATE INDEX idx_receipt_payment_session_lookup
    ON customer_receipt_payment(tenant_id,empresa_id,cash_register_session_id,customer_receipt_id)
    WHERE cash_register_session_id IS NOT NULL;
