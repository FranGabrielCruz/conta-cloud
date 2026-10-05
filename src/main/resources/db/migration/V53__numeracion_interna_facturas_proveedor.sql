CREATE TABLE purchase_invoice_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, empresa_id),
    CONSTRAINT fk_purchase_invoice_sequence_empresa
        FOREIGN KEY (tenant_id, empresa_id) REFERENCES empresas(tenant_id, id)
);

ALTER TABLE purchase_invoice ADD COLUMN internal_number VARCHAR(30);

WITH numbered AS (
    SELECT id, tenant_id, empresa_id,
           ROW_NUMBER() OVER (PARTITION BY tenant_id, empresa_id ORDER BY created_at, id) AS number
    FROM purchase_invoice
)
UPDATE purchase_invoice invoice
SET internal_number = 'FP-' || LPAD(numbered.number::text, 6, '0')
FROM numbered
WHERE invoice.id = numbered.id;

INSERT INTO purchase_invoice_sequence(tenant_id, empresa_id, last_number)
SELECT tenant_id, empresa_id, COUNT(*)
FROM purchase_invoice
GROUP BY tenant_id, empresa_id;

ALTER TABLE purchase_invoice ALTER COLUMN internal_number SET NOT NULL;
ALTER TABLE purchase_invoice ADD CONSTRAINT uk_purchase_invoice_internal_number
    UNIQUE (tenant_id, empresa_id, internal_number);

CREATE INDEX idx_purchase_invoice_credit_search
    ON purchase_invoice(tenant_id, empresa_id, supplier_id, currency_id, due_date, invoice_date);
