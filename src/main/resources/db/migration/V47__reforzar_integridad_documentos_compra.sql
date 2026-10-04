ALTER TABLE purchase_order_line
    ADD CONSTRAINT uk_purchase_order_line_scope UNIQUE(tenant_id,empresa_id,id);

ALTER TABLE purchase_invoice
    ADD CONSTRAINT fk_purchase_invoice_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_purchase_invoice_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_purchase_invoice_registered_by FOREIGN KEY(registered_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_purchase_invoice_voided_by FOREIGN KEY(voided_by) REFERENCES usuarios(id);

ALTER TABLE accounts_payable
    DROP CONSTRAINT uk_accounts_payable_invoice,
    ADD CONSTRAINT fk_accounts_payable_supplier FOREIGN KEY(tenant_id,empresa_id,supplier_id) REFERENCES supplier(tenant_id,empresa_id,id),
    ADD CONSTRAINT fk_accounts_payable_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    ADD CONSTRAINT fk_accounts_payable_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    ADD CONSTRAINT uk_accounts_payable_invoice UNIQUE(tenant_id,empresa_id,purchase_invoice_id);

ALTER TABLE purchase_receipt
    ADD CONSTRAINT fk_purchase_receipt_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_purchase_receipt_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_purchase_receipt_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_purchase_receipt_voided_by FOREIGN KEY(voided_by) REFERENCES usuarios(id);

ALTER TABLE purchase_receipt_line
    DROP CONSTRAINT fk_purchase_receipt_line_order_line,
    ADD CONSTRAINT fk_purchase_receipt_line_order_line FOREIGN KEY(tenant_id,empresa_id,purchase_order_line_id)
        REFERENCES purchase_order_line(tenant_id,empresa_id,id);

ALTER TABLE inventory_movement
    DROP CONSTRAINT uk_inventory_movement_reference,
    ADD CONSTRAINT fk_inventory_movement_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
    ADD CONSTRAINT uk_inventory_movement_reference UNIQUE(tenant_id,empresa_id,reference_type,reference_id,product_id,direction);
