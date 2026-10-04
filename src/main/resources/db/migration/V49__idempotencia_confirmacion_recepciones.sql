ALTER TABLE purchase_receipt
    ADD COLUMN idempotency_key UUID,
    ADD CONSTRAINT uk_purchase_receipt_idempotency
        UNIQUE(tenant_id,empresa_id,idempotency_key);
