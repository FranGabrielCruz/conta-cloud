ALTER TABLE product_category
    ADD COLUMN normalized_name VARCHAR(120);

UPDATE product_category
SET normalized_name=lower(regexp_replace(btrim(name),'\s+',' ','g'));

UPDATE product_category pc
SET created_by=(
    SELECT u.id
    FROM usuarios u
    WHERE u.tenant_id=pc.tenant_id AND u.empresa_id=pc.empresa_id
    ORDER BY u.activo DESC,u.usuario
    LIMIT 1
)
WHERE pc.created_by IS NULL;

ALTER TABLE product_category
    ALTER COLUMN normalized_name SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ADD CONSTRAINT ck_product_category_normalized_name CHECK (btrim(normalized_name) <> '');

DROP INDEX IF EXISTS uk_product_category_name;

ALTER TABLE product_category
    ADD CONSTRAINT uk_product_category_normalized_name
        UNIQUE(tenant_id,empresa_id,normalized_name);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('categorias.ver','Ver categorías','Consultar categorías de productos y servicios','INVENTARIO','categorias','ver'),
('categorias.crear','Crear categorías','Crear categorías de productos y servicios','INVENTARIO','categorias','crear'),
('categorias.editar','Editar categorías','Modificar categorías de productos y servicios','INVENTARIO','categorias','editar'),
('categorias.desactivar','Desactivar categorías','Desactivar categorías de productos y servicios','INVENTARIO','categorias','desactivar'),
('categorias.reactivar','Reactivar categorías','Reactivar categorías de productos y servicios','INVENTARIO','categorias','reactivar')
ON CONFLICT(codigo) DO UPDATE SET
    nombre=EXCLUDED.nombre,
    descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,
    recurso=EXCLUDED.recurso,
    accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
JOIN permisos p ON p.recurso='categorias'
WHERE upper(r.codigo)='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE
FROM empresas e
WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET
    enabled=TRUE,
    updated_at=CURRENT_TIMESTAMP;
