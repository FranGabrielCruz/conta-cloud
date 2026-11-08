-- Immutable print representation captured when a new job is queued; existing PDF-only jobs remain valid at 80 mm.
ALTER TABLE billing_print_jobs ADD COLUMN snapshot_json text;
ALTER TABLE billing_print_jobs ADD COLUMN snapshot_version integer;
ALTER TABLE billing_print_jobs ADD COLUMN trace_id varchar(100);
ALTER TABLE billing_print_jobs ADD CONSTRAINT ck_print_job_snapshot_version
    CHECK ((snapshot_json IS NULL AND snapshot_version IS NULL) OR
           (snapshot_json IS NOT NULL AND snapshot_version IS NOT NULL AND snapshot_version = 1 AND octet_length(snapshot_json) <= 2000000));
