ALTER TABLE monedas
    ALTER COLUMN codigo_iso TYPE VARCHAR(12)
    USING TRIM(codigo_iso);
