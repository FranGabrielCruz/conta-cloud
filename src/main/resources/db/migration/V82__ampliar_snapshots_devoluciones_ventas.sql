ALTER TABLE sales_return
    ADD COLUMN invoice_number VARCHAR(30),
    ADD COLUMN destination_warehouse_name VARCHAR(120);

UPDATE sales_return r
SET invoice_number = f.internal_number
FROM sales_invoice f
WHERE f.tenant_id = r.tenant_id
  AND f.empresa_id = r.empresa_id
  AND f.id = r.sales_invoice_id;

UPDATE sales_return r
SET destination_warehouse_name = w.name
FROM warehouse w
WHERE w.tenant_id = r.tenant_id
  AND w.empresa_id = r.empresa_id
  AND w.id = r.destination_warehouse_id;

ALTER TABLE sales_return
    ALTER COLUMN invoice_number SET NOT NULL;

CREATE OR REPLACE FUNCTION seed_sales_return_reasons_for_company() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO sales_return_reason(tenant_id,empresa_id,code,name,requires_observation) VALUES
      (NEW.tenant_id,NEW.id,'DEFECTIVE','Producto defectuoso',FALSE),
      (NEW.tenant_id,NEW.id,'WRONG_PRODUCT','Producto incorrecto',FALSE),
      (NEW.tenant_id,NEW.id,'DAMAGED','Producto dañado',FALSE),
      (NEW.tenant_id,NEW.id,'CUSTOMER_DISSATISFIED','Cliente no satisfecho',FALSE),
      (NEW.tenant_id,NEW.id,'BILLING_ERROR','Error de facturación',FALSE),
      (NEW.tenant_id,NEW.id,'OTHER','Otro',TRUE)
    ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_seed_sales_return_reasons
AFTER INSERT ON empresas FOR EACH ROW EXECUTE FUNCTION seed_sales_return_reasons_for_company();
