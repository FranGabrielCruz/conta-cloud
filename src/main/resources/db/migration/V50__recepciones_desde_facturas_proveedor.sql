ALTER TABLE purchase_invoice_line
    ADD CONSTRAINT uk_purchase_invoice_line_scope UNIQUE(tenant_id,empresa_id,id);

ALTER TABLE purchase_receipt
    ADD COLUMN purchase_invoice_id UUID,
    ADD CONSTRAINT fk_purchase_receipt_invoice
        FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_id)
        REFERENCES purchase_invoice(tenant_id,empresa_id,id);

ALTER TABLE purchase_receipt_line
    ADD COLUMN purchase_invoice_line_id UUID;

ALTER TABLE purchase_receipt_line
    DROP CONSTRAINT ck_purchase_receipt_line_source,
    DROP CONSTRAINT ck_purchase_receipt_line_difference;

UPDATE purchase_receipt_line
SET line_source='PURCHASE_ORDER'
WHERE line_source='ORDER_LINE';

UPDATE purchase_receipt_line
SET difference_type='OVER_ORDERED_QUANTITY'
WHERE difference_type='OVER_RECEIPT';

ALTER TABLE purchase_receipt_line
    ADD CONSTRAINT ck_purchase_receipt_line_source
        CHECK(line_source IN('PURCHASE_ORDER','PURCHASE_INVOICE','MANUAL')),
    ADD CONSTRAINT ck_purchase_receipt_line_difference
        CHECK(difference_type IN('NONE','UNORDERED_PRODUCT','UNINVOICED_PRODUCT',
            'OVER_ORDERED_QUANTITY','OVER_INVOICED_QUANTITY')),
    ADD CONSTRAINT fk_purchase_receipt_line_invoice_line
        FOREIGN KEY(tenant_id,empresa_id,purchase_invoice_line_id)
        REFERENCES purchase_invoice_line(tenant_id,empresa_id,id);

CREATE INDEX idx_purchase_receipt_invoice
    ON purchase_receipt(tenant_id,empresa_id,purchase_invoice_id)
    WHERE purchase_invoice_id IS NOT NULL;

CREATE INDEX idx_purchase_receipt_line_invoice
    ON purchase_receipt_line(tenant_id,empresa_id,purchase_invoice_line_id)
    WHERE purchase_invoice_line_id IS NOT NULL;
