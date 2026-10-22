ALTER TABLE tenant_directory
    ADD COLUMN provisioning_step VARCHAR(50) NOT NULL DEFAULT 'RESERVED',
    ADD COLUMN provisioning_attempts INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN last_error VARCHAR(1000),
    ADD COLUMN last_trace_id VARCHAR(80),
    ADD COLUMN compensation_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN compensation_error VARCHAR(1000);

ALTER TABLE tenant_directory
    ADD CONSTRAINT ck_tenant_directory_provisioning_attempts CHECK (provisioning_attempts > 0),
    ADD CONSTRAINT ck_tenant_directory_compensation_status
        CHECK (compensation_status IN ('NONE','PENDING','COMPLETED','FAILED'));

CREATE INDEX idx_tenant_directory_provisioning_recovery
    ON tenant_directory(status, provisioning_step, updated_at)
    WHERE status = 'FAILED';

CREATE TABLE login_attempt (
    identity_key CHAR(64) PRIMARY KEY,
    failure_count INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_login_attempt_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX idx_login_attempt_cleanup ON login_attempt(updated_at);
