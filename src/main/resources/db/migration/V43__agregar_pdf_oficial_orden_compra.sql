ALTER TABLE purchase_order
    ADD COLUMN IF NOT EXISTS pdf_object_key VARCHAR(700);

CREATE INDEX IF NOT EXISTS idx_purchase_order_pdf
    ON purchase_order (tenant_id, empresa_id, id)
    WHERE pdf_object_key IS NOT NULL;
