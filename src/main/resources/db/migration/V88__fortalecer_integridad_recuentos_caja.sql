ALTER TABLE cash_counts
    DROP CONSTRAINT ck_cash_count_values,
    ADD CONSTRAINT ck_cash_count_values CHECK (counted_amount >= 0);

CREATE UNIQUE INDEX uk_cash_denom_tenant_id
    ON cash_denominations(tenant_id, empresa_id, id);

ALTER TABLE cash_count_details
    DROP CONSTRAINT fk_cash_count_detail_denom,
    ADD CONSTRAINT fk_cash_count_detail_denom
        FOREIGN KEY (tenant_id, empresa_id, denomination_id)
        REFERENCES cash_denominations(tenant_id, empresa_id, id);
