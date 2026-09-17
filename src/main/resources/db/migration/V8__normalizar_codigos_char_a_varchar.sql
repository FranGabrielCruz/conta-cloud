ALTER TABLE empresas
    ALTER COLUMN pais_codigo TYPE VARCHAR(2)
    USING TRIM(pais_codigo);

ALTER TABLE monedas
    ALTER COLUMN codigo_iso TYPE VARCHAR(3)
    USING TRIM(codigo_iso);
