ALTER TABLE unit_of_measure
    ADD COLUMN normalized_name VARCHAR(80),
    ADD COLUMN normalized_abbreviation VARCHAR(20),
    ADD COLUMN description VARCHAR(500);

UPDATE unit_of_measure
SET normalized_name=lower(regexp_replace(btrim(name),'\s+',' ','g')),
    normalized_abbreviation=lower(regexp_replace(btrim(abbreviation),'\s+',' ','g'));

ALTER TABLE unit_of_measure
    ALTER COLUMN normalized_name SET NOT NULL,
    ALTER COLUMN normalized_abbreviation SET NOT NULL,
    DROP CONSTRAINT IF EXISTS uk_unit_of_measure_abbreviation,
    ADD CONSTRAINT uk_unit_of_measure_name_normalized
        UNIQUE(tenant_id,empresa_id,normalized_name),
    ADD CONSTRAINT uk_unit_of_measure_abbreviation_normalized
        UNIQUE(tenant_id,empresa_id,normalized_abbreviation),
    ADD CONSTRAINT ck_unit_of_measure_normalized_name CHECK (btrim(normalized_name) <> ''),
    ADD CONSTRAINT ck_unit_of_measure_normalized_abbreviation CHECK (btrim(normalized_abbreviation) <> '');

CREATE INDEX idx_unit_of_measure_abbreviation_search
    ON unit_of_measure(tenant_id,empresa_id,normalized_abbreviation);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('unidades_medida.ver','Ver unidades de medida','Consultar unidades de medida','INVENTARIO','unidades_medida','ver'),
('unidades_medida.crear','Crear unidades de medida','Crear unidades de medida','INVENTARIO','unidades_medida','crear'),
('unidades_medida.editar','Editar unidades de medida','Modificar unidades de medida','INVENTARIO','unidades_medida','editar'),
('unidades_medida.desactivar','Desactivar unidades de medida','Desactivar unidades de medida','INVENTARIO','unidades_medida','desactivar'),
('unidades_medida.reactivar','Reactivar unidades de medida','Reactivar unidades de medida','INVENTARIO','unidades_medida','reactivar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r JOIN permisos p ON p.recurso='unidades_medida'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;

INSERT INTO unit_of_measure(tenant_id,empresa_id,name,normalized_name,abbreviation,normalized_abbreviation)
SELECT e.tenant_id,e.id,u.name,lower(u.name),u.abbreviation,lower(u.abbreviation)
FROM empresas e CROSS JOIN (VALUES
    ('Unidad','und'),('Caja','caja'),('Paquete','paq'),('Kilogramo','kg'),('Libra','lb'),
    ('Metro','m'),('Litro','L'),('Galón','gal'),('Hora','h'),('Servicio','srv')
) AS u(name,abbreviation)
ON CONFLICT(tenant_id,empresa_id,normalized_abbreviation) DO NOTHING;

CREATE OR REPLACE FUNCTION contacloud_crear_unidades_predeterminadas()
RETURNS trigger AS $$
BEGIN
    INSERT INTO unit_of_measure(tenant_id,empresa_id,name,normalized_name,abbreviation,normalized_abbreviation)
    VALUES
        (NEW.tenant_id,NEW.id,'Unidad','unidad','und','und'),
        (NEW.tenant_id,NEW.id,'Caja','caja','caja','caja'),
        (NEW.tenant_id,NEW.id,'Paquete','paquete','paq','paq'),
        (NEW.tenant_id,NEW.id,'Kilogramo','kilogramo','kg','kg'),
        (NEW.tenant_id,NEW.id,'Libra','libra','lb','lb'),
        (NEW.tenant_id,NEW.id,'Metro','metro','m','m'),
        (NEW.tenant_id,NEW.id,'Litro','litro','L','l'),
        (NEW.tenant_id,NEW.id,'Galón','galón','gal','gal'),
        (NEW.tenant_id,NEW.id,'Hora','hora','h','h'),
        (NEW.tenant_id,NEW.id,'Servicio','servicio','srv','srv')
    ON CONFLICT(tenant_id,empresa_id,normalized_abbreviation) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_empresa_unidades_predeterminadas ON empresas;
CREATE TRIGGER trg_empresa_unidades_predeterminadas
AFTER INSERT ON empresas
FOR EACH ROW EXECUTE FUNCTION contacloud_crear_unidades_predeterminadas();

ALTER TABLE purchase_order_line
    ADD COLUMN unit_of_measure_id_snapshot UUID,
    ADD COLUMN unit_name_snapshot VARCHAR(80),
    ADD COLUMN unit_abbreviation_snapshot VARCHAR(20),
    ADD CONSTRAINT fk_purchase_order_line_unit_snapshot
        FOREIGN KEY(tenant_id,empresa_id,unit_of_measure_id_snapshot)
        REFERENCES unit_of_measure(tenant_id,empresa_id,id);

UPDATE purchase_order_line
SET unit_name_snapshot=unit_of_measure_snapshot
WHERE unit_name_snapshot IS NULL;

CREATE INDEX idx_purchase_order_line_unit_snapshot
    ON purchase_order_line(tenant_id,empresa_id,unit_of_measure_id_snapshot);
