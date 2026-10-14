CREATE OR REPLACE FUNCTION seed_inventory_adjustment_reasons_for_company() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO inventory_adjustment_reason(tenant_id,empresa_id,code,name,allowed_direction,requires_notes)
    VALUES
      (NEW.tenant_id,NEW.id,'DAMAGED','Producto dañado','OUT',FALSE),
      (NEW.tenant_id,NEW.id,'EXPIRED','Producto vencido','OUT',FALSE),
      (NEW.tenant_id,NEW.id,'BREAKAGE','Rotura','OUT',FALSE),
      (NEW.tenant_id,NEW.id,'LOSS','Pérdida','OUT',FALSE),
      (NEW.tenant_id,NEW.id,'FOUND','Mercancía encontrada','IN',FALSE),
      (NEW.tenant_id,NEW.id,'REGISTER_ERROR','Error de registro','BOTH',FALSE),
      (NEW.tenant_id,NEW.id,'INVENTORY_CORRECTION','Corrección de inventario','BOTH',FALSE),
      (NEW.tenant_id,NEW.id,'OTHER','Otro','BOTH',TRUE)
    ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_seed_inventory_adjustment_reasons
AFTER INSERT ON empresas FOR EACH ROW EXECUTE FUNCTION seed_inventory_adjustment_reasons_for_company();
