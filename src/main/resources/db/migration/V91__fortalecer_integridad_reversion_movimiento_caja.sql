CREATE UNIQUE INDEX IF NOT EXISTS uk_financial_movement_tenant_company_id
    ON movimientos_financieros(tenant_id,empresa_id,id);

ALTER TABLE movimientos_financieros
    DROP CONSTRAINT fk_financial_movement_reversal;

ALTER TABLE movimientos_financieros
    ADD CONSTRAINT fk_financial_movement_reversal
        FOREIGN KEY (tenant_id,empresa_id,reversal_of_movement_id)
        REFERENCES movimientos_financieros(tenant_id,empresa_id,id);
