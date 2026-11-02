-- A PDF decision is an idempotent output request, never a print job.
ALTER TABLE billing_print_jobs ADD CONSTRAINT uk_billing_print_job_context UNIQUE(tenant_id,empresa_id,id);
CREATE TABLE billing_print_requests (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 tenant_id UUID NOT NULL,
 empresa_id UUID NOT NULL,
 idempotency_key UUID NOT NULL,
 document_type VARCHAR(30) NOT NULL,
 document_id UUID NOT NULL,
 output_mode VARCHAR(20),
 agent_id UUID,
 job_id UUID,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(tenant_id,empresa_id,idempotency_key),
 FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
 FOREIGN KEY(tenant_id,empresa_id,agent_id) REFERENCES print_agents(tenant_id,empresa_id,id) ON DELETE CASCADE,
 FOREIGN KEY(tenant_id,empresa_id,job_id) REFERENCES billing_print_jobs(tenant_id,empresa_id,id) ON DELETE CASCADE,
 CHECK ((output_mode IS NULL AND agent_id IS NULL AND job_id IS NULL)
  OR (output_mode IS NOT NULL AND ((output_mode='SCREEN' AND agent_id IS NOT NULL AND job_id IS NULL)
   OR (output_mode='PRINT_AGENT' AND agent_id IS NOT NULL AND job_id IS NOT NULL))))
);
