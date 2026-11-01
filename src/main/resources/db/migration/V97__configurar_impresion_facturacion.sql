CREATE TABLE billing_print_settings (
 tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, show_pdf_on_print BOOLEAN NOT NULL DEFAULT TRUE,
 PRIMARY KEY(tenant_id,empresa_id), FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id));
CREATE TABLE billing_print_jobs (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, branch_id UUID NOT NULL,
 cash_register_id UUID NOT NULL, agent_id UUID NOT NULL, document_id UUID NOT NULL,
 idempotency_key UUID NOT NULL, document_pdf BYTEA NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, claim_id UUID, claimed_at TIMESTAMPTZ,
 CONSTRAINT ck_billing_print_job_status CHECK(status IN ('PENDING','PROCESSING','PRINTED','FAILED','CANCELLED')),
 FOREIGN KEY(tenant_id,empresa_id,agent_id) REFERENCES print_agents(tenant_id,empresa_id,id),
 FOREIGN KEY(tenant_id,empresa_id,branch_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,sucursal_id,id),
 UNIQUE(tenant_id,empresa_id,idempotency_key));
CREATE INDEX idx_billing_print_jobs_agent ON billing_print_jobs(tenant_id,empresa_id,agent_id,status,created_at);
INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
 ('facturas.configurar_impresion','Configurar impresión de Facturación','Configurar visualización automática de PDF','VENTAS','facturas','configurar_impresion') ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id) SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p WHERE r.codigo='ADMINISTRADOR' AND p.codigo='facturas.configurar_impresion' ON CONFLICT(rol_id,permiso_id) DO NOTHING;
