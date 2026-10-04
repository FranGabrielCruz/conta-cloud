ALTER TABLE impuestos
    ALTER COLUMN nombre TYPE VARCHAR(120),
    ALTER COLUMN porcentaje TYPE NUMERIC(9,6),
    ADD COLUMN nombre_normalizado VARCHAR(120),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE impuestos
SET nombre=btrim(regexp_replace(nombre,'\s+',' ','g')),
    nombre_normalizado=lower(btrim(regexp_replace(nombre,'\s+',' ','g'))),
    tipo='PERCENTAGE';

UPDATE impuestos i
SET created_by=(
        SELECT u.id FROM usuarios u
        WHERE u.tenant_id=i.tenant_id AND u.empresa_id=i.empresa_id
        ORDER BY u.activo DESC,u.usuario
        LIMIT 1
    ),
    updated_by=(
        SELECT u.id FROM usuarios u
        WHERE u.tenant_id=i.tenant_id AND u.empresa_id=i.empresa_id
        ORDER BY u.activo DESC,u.usuario
        LIMIT 1
    );

ALTER TABLE impuestos
    ALTER COLUMN nombre_normalizado SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ADD CONSTRAINT ck_impuestos_nombre_normalizado CHECK (btrim(nombre_normalizado) <> ''),
    ADD CONSTRAINT ck_impuestos_porcentaje_rango CHECK (
        tipo <> 'PERCENTAGE' OR (porcentaje >= 0 AND porcentaje <= 100)
    ),
    ADD CONSTRAINT fk_impuestos_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_impuestos_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    ADD CONSTRAINT uk_impuestos_nombre_normalizado UNIQUE (tenant_id,empresa_id,nombre_normalizado);

CREATE INDEX idx_impuestos_contexto_estado
    ON impuestos(tenant_id,empresa_id,activo);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('impuestos.reactivar','Reactivar impuestos','Reactivar impuestos de la empresa','FISCAL','impuestos','reactivar')
ON CONFLICT(codigo) DO UPDATE SET
    nombre=EXCLUDED.nombre,
    descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,
    recurso=EXCLUDED.recurso,
    accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
JOIN permisos p ON p.recurso='impuestos'
WHERE upper(r.codigo)='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT DISTINCT rp.empresa_id,rp.rol_id,nuevo.id
FROM rol_permisos rp
JOIN permisos anterior ON anterior.id=rp.permiso_id AND anterior.codigo='impuestos.desactivar'
JOIN permisos nuevo ON nuevo.codigo='impuestos.reactivar'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
