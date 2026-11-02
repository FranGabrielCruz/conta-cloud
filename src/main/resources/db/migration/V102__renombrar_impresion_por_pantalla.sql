-- Rename only: preserve all values, constraints and defaults without backfill or inversion.
ALTER TABLE print_agents RENAME COLUMN show_pdf_on_print TO print_on_screen;
ALTER TABLE billing_print_jobs RENAME COLUMN show_pdf_on_print TO print_on_screen;
