ALTER TABLE sales_payment_method
ADD COLUMN IF NOT EXISTS financial_bank_account_id UUID;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname='fk_sales_payment_method_financial_bank'
  ) THEN
    ALTER TABLE sales_payment_method
    ADD CONSTRAINT fk_sales_payment_method_financial_bank
    FOREIGN KEY(tenant_id,empresa_id,financial_bank_account_id)
    REFERENCES cuentas_bancarias(tenant_id,empresa_id,id);
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION contacloud_seed_sales_payment_methods() RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO sales_payment_method(tenant_id,empresa_id,code,name,type,display_order,
    requires_reference,requires_bank_account,allows_change,is_cash,is_electronic,icon,is_default) VALUES
  (NEW.tenant_id,NEW.id,'CASH','Efectivo','CASH',10,FALSE,FALSE,TRUE,TRUE,FALSE,'CASH',TRUE),
  (NEW.tenant_id,NEW.id,'CREDIT_CARD','Tarjeta de crédito','CREDIT_CARD',20,FALSE,FALSE,FALSE,FALSE,TRUE,'CREDIT_CARD',FALSE),
  (NEW.tenant_id,NEW.id,'DEBIT_CARD','Tarjeta de débito','DEBIT_CARD',30,FALSE,FALSE,FALSE,FALSE,TRUE,'DEBIT_CARD',FALSE),
  (NEW.tenant_id,NEW.id,'BANK_TRANSFER','Transferencia bancaria','BANK_TRANSFER',40,FALSE,TRUE,FALSE,FALSE,TRUE,'BANK',FALSE),
  (NEW.tenant_id,NEW.id,'CHECK','Cheque','CHECK',50,TRUE,FALSE,FALSE,FALSE,FALSE,'CHECK',FALSE)
  ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_empresa_sales_payment_methods ON empresas;
CREATE TRIGGER trg_empresa_sales_payment_methods
AFTER INSERT ON empresas
FOR EACH ROW EXECUTE FUNCTION contacloud_seed_sales_payment_methods();
