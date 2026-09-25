ALTER TABLE empresas
    ADD COLUMN limite_usuarios_habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN limite_usuarios INTEGER;

ALTER TABLE empresas
    ADD CONSTRAINT ck_empresas_limite_usuarios
    CHECK (
        (limite_usuarios_habilitado = FALSE AND limite_usuarios IS NULL)
        OR
        (limite_usuarios_habilitado = TRUE AND limite_usuarios IS NOT NULL AND limite_usuarios > 0)
    );
