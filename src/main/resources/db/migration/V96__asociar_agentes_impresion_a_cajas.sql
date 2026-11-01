ALTER TABLE cajas ADD CONSTRAINT uk_caja_sucursal_impresion UNIQUE (tenant_id,empresa_id,sucursal_id,id);
ALTER TABLE print_agents ADD COLUMN cash_register_id UUID;
ALTER TABLE print_agents ADD CONSTRAINT fk_print_agent_caja FOREIGN KEY (tenant_id,empresa_id,sucursal_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,sucursal_id,id);
CREATE INDEX idx_print_agent_caja ON print_agents(tenant_id,empresa_id,cash_register_id);
