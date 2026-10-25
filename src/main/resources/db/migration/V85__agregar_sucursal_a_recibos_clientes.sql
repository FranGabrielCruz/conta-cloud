ALTER TABLE customer_receipt
    ADD COLUMN branch_id UUID,
    ADD COLUMN branch_name_snapshot VARCHAR(150);

UPDATE customer_receipt r
SET branch_id = COALESCE(
        (SELECT c.sucursal_id
           FROM customer_receipt_payment p
           JOIN cajas c ON c.id = p.cash_register_id
          WHERE p.customer_receipt_id = r.id
            AND p.tenant_id = r.tenant_id
            AND p.empresa_id = r.empresa_id
          ORDER BY p.line_order
          LIMIT 1),
        (SELECT f.branch_id
           FROM customer_receipt_application a
           JOIN sales_invoice f ON f.id = a.sales_invoice_id
          WHERE a.customer_receipt_id = r.id
            AND a.tenant_id = r.tenant_id
            AND a.empresa_id = r.empresa_id
          ORDER BY a.applied_at, a.id
          LIMIT 1),
        (SELECT s.id
           FROM sucursales s
          WHERE s.empresa_id = r.empresa_id
          ORDER BY s.principal DESC, s.activo DESC, s.id
          LIMIT 1)
    )
WHERE r.branch_id IS NULL;

UPDATE customer_receipt r
SET branch_name_snapshot = s.nombre
FROM sucursales s
WHERE s.empresa_id = r.empresa_id
  AND s.id = r.branch_id
  AND r.branch_name_snapshot IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM customer_receipt WHERE branch_id IS NULL OR branch_name_snapshot IS NULL) THEN
        RAISE EXCEPTION 'No fue posible determinar la sucursal histórica de todos los recibos de clientes';
    END IF;
END $$;

ALTER TABLE customer_receipt
    ALTER COLUMN branch_id SET NOT NULL,
    ALTER COLUMN branch_name_snapshot SET NOT NULL,
    ADD CONSTRAINT fk_customer_receipt_branch
        FOREIGN KEY(empresa_id,branch_id) REFERENCES sucursales(empresa_id,id);

CREATE INDEX idx_customer_receipt_branch_list
    ON customer_receipt(tenant_id,empresa_id,branch_id,confirmed_at DESC,id DESC);
