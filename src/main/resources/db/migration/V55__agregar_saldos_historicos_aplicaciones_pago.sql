ALTER TABLE supplier_payment_application
    ADD COLUMN previous_balance NUMERIC(19,4),
    ADD COLUMN remaining_balance NUMERIC(19,4);

UPDATE supplier_payment_application application
SET remaining_balance = payable.original_amount - payable.applied_amount
        + COALESCE((
            SELECT SUM(later.amount)
            FROM supplier_payment_application later
            WHERE later.tenant_id = application.tenant_id
              AND later.empresa_id = application.empresa_id
              AND later.purchase_invoice_id = application.purchase_invoice_id
              AND later.reversed = FALSE
              AND (later.applied_at > application.applied_at
                   OR (later.applied_at = application.applied_at AND later.id > application.id))
        ), 0),
    previous_balance = payable.original_amount - payable.applied_amount
        + COALESCE((
            SELECT SUM(later.amount)
            FROM supplier_payment_application later
            WHERE later.tenant_id = application.tenant_id
              AND later.empresa_id = application.empresa_id
              AND later.purchase_invoice_id = application.purchase_invoice_id
              AND later.reversed = FALSE
              AND (later.applied_at > application.applied_at
                   OR (later.applied_at = application.applied_at AND later.id > application.id))
        ), 0) + application.amount
FROM accounts_payable payable
WHERE payable.id = application.accounts_payable_id;

ALTER TABLE supplier_payment_application
    ALTER COLUMN previous_balance SET NOT NULL,
    ALTER COLUMN remaining_balance SET NOT NULL,
    ADD CONSTRAINT ck_supplier_payment_application_balances
        CHECK(previous_balance >= 0 AND remaining_balance >= 0
              AND previous_balance - amount = remaining_balance);
