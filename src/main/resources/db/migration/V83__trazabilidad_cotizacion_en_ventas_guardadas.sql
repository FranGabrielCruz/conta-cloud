ALTER TABLE saved_sale ADD COLUMN source_quote_id UUID;
ALTER TABLE saved_sale_line ADD COLUMN source_quote_line_id UUID;

ALTER TABLE saved_sale ADD CONSTRAINT fk_saved_sale_source_quote
    FOREIGN KEY (tenant_id,empresa_id,source_quote_id)
    REFERENCES sales_quote(tenant_id,empresa_id,id);

ALTER TABLE saved_sale_line ADD CONSTRAINT fk_saved_sale_line_source_quote
    FOREIGN KEY (tenant_id,empresa_id,source_quote_line_id)
    REFERENCES sales_quote_line(tenant_id,empresa_id,id);

CREATE INDEX idx_saved_sale_source_quote
    ON saved_sale(tenant_id,empresa_id,source_quote_id)
    WHERE source_quote_id IS NOT NULL;
