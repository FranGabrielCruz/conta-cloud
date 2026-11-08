ALTER TABLE billing_print_jobs
 ADD COLUMN available_at timestamptz NOT NULL DEFAULT current_timestamp,
 ADD COLUMN attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count>=0),
 ADD COLUMN last_attempt_at timestamptz,
 ADD COLUMN completed_at timestamptz,
 ADD COLUMN last_error_code varchar(64),
 ADD COLUMN lease_until timestamptz,
 ADD COLUMN output_started_at timestamptz,
 ADD COLUMN protocol_version smallint NOT NULL DEFAULT 1 CHECK (protocol_version IN (1,2)),
 ADD COLUMN last_result_claim_id uuid,
 ADD COLUMN last_result_printed boolean;
UPDATE billing_print_jobs SET available_at=created_at,
 lease_until=CASE WHEN status='PROCESSING' THEN coalesce(claimed_at,current_timestamp)+interval '300' second ELSE NULL END,
 completed_at=CASE WHEN status IN ('PRINTED','FAILED','CANCELLED') THEN coalesce(claimed_at,created_at) ELSE NULL END;
CREATE INDEX idx_billing_print_jobs_eligible ON billing_print_jobs(tenant_id,empresa_id,agent_id,status,available_at,created_at,id);
