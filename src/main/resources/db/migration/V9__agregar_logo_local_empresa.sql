ALTER TABLE empresas
    ADD COLUMN tenant_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN logo_object_key VARCHAR(512);

CREATE INDEX idx_empresas_tenant_id ON empresas(tenant_id);
