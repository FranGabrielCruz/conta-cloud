-- Identifica obligaciones creadas exclusivamente por notas de débito.
ALTER TABLE customer_accounts_receivable ADD COLUMN debit_note_origin BOOLEAN NOT NULL DEFAULT false;
