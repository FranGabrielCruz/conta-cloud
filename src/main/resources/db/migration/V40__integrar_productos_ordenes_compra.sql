ALTER TABLE purchase_order_line
    ADD COLUMN product_code_snapshot VARCHAR(24),
    ADD COLUMN unit_of_measure_snapshot VARCHAR(100),
    ADD CONSTRAINT fk_purchase_order_line_product FOREIGN KEY (tenant_id,empresa_id,product_id)
        REFERENCES product(tenant_id,empresa_id,id);

CREATE INDEX idx_purchase_order_line_product
    ON purchase_order_line(tenant_id,empresa_id,product_id);
