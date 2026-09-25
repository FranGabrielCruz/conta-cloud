ALTER TABLE empresas
    DROP CONSTRAINT ck_empresas_limite_usuarios;

ALTER TABLE empresas
    ADD CONSTRAINT ck_empresas_limite_usuarios
    CHECK (
        (limite_usuarios_habilitado = FALSE AND limite_usuarios IS NULL)
        OR
        (limite_usuarios_habilitado = TRUE AND limite_usuarios BETWEEN 1 AND 100000)
    );
