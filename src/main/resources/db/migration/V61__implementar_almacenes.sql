ALTER TABLE warehouse
    ADD COLUMN branch_id UUID,
    ADD COLUMN normalized_name VARCHAR(120),
    ADD COLUMN warehouse_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN responsible_user_id UUID,
    ADD COLUMN address VARCHAR(500),
    ADD COLUMN observation VARCHAR(1000),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

INSERT INTO sucursales(empresa_id,codigo,nombre,principal,activo)
SELECT e.id,'PRINCIPAL','Sucursal Principal',TRUE,TRUE
FROM empresas e
WHERE NOT EXISTS (SELECT 1 FROM sucursales s WHERE s.empresa_id=e.id);

UPDATE warehouse w
SET branch_id=(
        SELECT s.id FROM sucursales s
        WHERE s.empresa_id=w.empresa_id
        ORDER BY s.principal DESC,s.activo DESC,s.nombre,s.id
        LIMIT 1
    ),
    normalized_name=lower(regexp_replace(btrim(w.name),'\s+',' ','g')),
    updated_by=w.created_by;

WITH numbered AS (
    SELECT id,row_number() OVER(PARTITION BY tenant_id,empresa_id ORDER BY created_at,id) AS number
    FROM warehouse
)
UPDATE warehouse w
SET code='ALM-' || lpad(numbered.number::text,6,'0')
FROM numbered
WHERE numbered.id=w.id;

ALTER TABLE warehouse
    ALTER COLUMN branch_id SET NOT NULL,
    ALTER COLUMN normalized_name SET NOT NULL,
    ADD CONSTRAINT uk_warehouse_empresa_id UNIQUE(empresa_id,id),
    ADD CONSTRAINT ck_warehouse_name CHECK (btrim(name)<>''),
    ADD CONSTRAINT ck_warehouse_type CHECK (warehouse_type IN ('GENERAL','DEVOLUCIONES','DANADOS','TRANSITO')),
    ADD CONSTRAINT fk_warehouse_branch FOREIGN KEY(empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    ADD CONSTRAINT fk_warehouse_responsible FOREIGN KEY(empresa_id,responsible_user_id) REFERENCES usuarios(empresa_id,id),
    ADD CONSTRAINT fk_warehouse_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id);

CREATE OR REPLACE FUNCTION contacloud_normalizar_almacen()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.name := regexp_replace(btrim(NEW.name),'\s+',' ','g');
    NEW.normalized_name := lower(NEW.name);
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_warehouse_normalized_name
BEFORE INSERT OR UPDATE OF name ON warehouse
FOR EACH ROW EXECUTE FUNCTION contacloud_normalizar_almacen();

DROP INDEX IF EXISTS uk_warehouse_active_name_branch;
CREATE UNIQUE INDEX uk_warehouse_active_name_branch
    ON warehouse(tenant_id,empresa_id,branch_id,normalized_name) WHERE active;
CREATE INDEX idx_warehouse_list
    ON warehouse(tenant_id,empresa_id,active,branch_id,warehouse_type,name);
CREATE INDEX idx_inventory_movement_warehouse_order
    ON inventory_movement(tenant_id,empresa_id,warehouse_id,movement_date DESC,created_at DESC,id DESC);

CREATE TABLE warehouse_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_warehouse_sequence_empresa FOREIGN KEY(tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id)
);

INSERT INTO warehouse_sequence(tenant_id,empresa_id,last_number)
SELECT tenant_id,empresa_id,count(*) FROM warehouse GROUP BY tenant_id,empresa_id
ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET last_number=GREATEST(warehouse_sequence.last_number,EXCLUDED.last_number);

ALTER TABLE sucursales ADD COLUMN default_warehouse_id UUID;
ALTER TABLE sucursales ADD CONSTRAINT fk_branch_default_warehouse
    FOREIGN KEY(empresa_id,default_warehouse_id) REFERENCES warehouse(empresa_id,id);

UPDATE sucursales s
SET default_warehouse_id=(
    SELECT w.id FROM warehouse w
    WHERE w.empresa_id=s.empresa_id AND w.branch_id=s.id AND w.active
    ORDER BY w.created_at,w.id LIMIT 1
)
WHERE default_warehouse_id IS NULL;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('almacenes.ver','Ver almacenes','Consultar almacenes','INVENTARIO','almacenes','ver'),
('almacenes.crear','Crear almacenes','Crear almacenes','INVENTARIO','almacenes','crear'),
('almacenes.editar','Editar almacenes','Modificar almacenes','INVENTARIO','almacenes','editar'),
('almacenes.activar','Activar almacenes','Activar almacenes','INVENTARIO','almacenes','activar'),
('almacenes.desactivar','Desactivar almacenes','Desactivar almacenes','INVENTARIO','almacenes','desactivar'),
('almacenes.existencias','Ver existencias de almacenes','Consultar existencias por almacén','INVENTARIO','almacenes','existencias'),
('almacenes.movimientos','Ver movimientos de almacenes','Consultar movimientos por almacén','INVENTARIO','almacenes','movimientos')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='almacenes'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
