ALTER TABLE sales_quote_line
    DROP CONSTRAINT uk_sales_quote_line_number,
    ADD CONSTRAINT uk_sales_quote_line_number
        UNIQUE (sales_quote_id,line_number) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT uk_sales_quote_line_scope_id
        UNIQUE (tenant_id,empresa_id,id);

ALTER TABLE sales_quote_document_link
    ADD CONSTRAINT uk_sales_quote_document_scope_id
        UNIQUE (tenant_id,empresa_id,id);

ALTER TABLE sales_quote_line_conversion
    DROP CONSTRAINT fk_sales_quote_conversion_line,
    DROP CONSTRAINT fk_sales_quote_conversion_document,
    ADD CONSTRAINT fk_sales_quote_conversion_line
        FOREIGN KEY (tenant_id,empresa_id,sales_quote_line_id)
        REFERENCES sales_quote_line(tenant_id,empresa_id,id),
    ADD CONSTRAINT fk_sales_quote_conversion_document
        FOREIGN KEY (tenant_id,empresa_id,document_link_id)
        REFERENCES sales_quote_document_link(tenant_id,empresa_id,id);
