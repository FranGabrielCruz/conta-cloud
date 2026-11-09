
-- A POS receipt documents an existing sale payment; it never creates another receivable or payment.
ALTER TABLE customer_receipt ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE customer_receipt ADD COLUMN pos_invoice_id UUID;
ALTER TABLE customer_receipt ADD CONSTRAINT fk_receipt_pos_invoice
    FOREIGN KEY (tenant_id,empresa_id,pos_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id);
ALTER TABLE customer_receipt ADD CONSTRAINT ck_receipt_customer_or_pos
    CHECK (customer_id IS NOT NULL OR pos_invoice_id IS NOT NULL);
CREATE UNIQUE INDEX uk_receipt_pos_invoice ON customer_receipt(tenant_id,empresa_id,pos_invoice_id)
    WHERE pos_invoice_id IS NOT NULL;

ALTER TABLE customer_receipt_application ADD COLUMN original_total_snapshot NUMERIC(19,4);
ALTER TABLE customer_receipt_application ADD COLUMN debit_note_id_snapshot UUID;
ALTER TABLE customer_receipt_application ADD CONSTRAINT fk_receipt_application_debit_note
    FOREIGN KEY (tenant_id,empresa_id,debit_note_id_snapshot) REFERENCES sales_debit_note(tenant_id,empresa_id,id);

UPDATE customer_receipt_application a
   SET original_total_snapshot=coalesce(n.amount,f.total), debit_note_id_snapshot=n.id
  FROM customer_accounts_receivable ar
  JOIN sales_invoice f ON f.id=ar.sales_invoice_id AND f.tenant_id=ar.tenant_id AND f.empresa_id=ar.empresa_id
  LEFT JOIN sales_debit_note n ON n.id=ar.sales_debit_note_id AND n.tenant_id=ar.tenant_id AND n.empresa_id=ar.empresa_id
 WHERE ar.id=a.accounts_receivable_id AND ar.tenant_id=a.tenant_id AND ar.empresa_id=a.empresa_id;
