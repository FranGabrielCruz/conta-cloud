ALTER TABLE condiciones_pago ADD COLUMN tipo VARCHAR(20);
ALTER TABLE condiciones_pago ADD COLUMN descripcion VARCHAR(500);
ALTER TABLE condiciones_pago ADD COLUMN creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE condiciones_pago ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE condiciones_pago ADD COLUMN creado_por UUID;
ALTER TABLE condiciones_pago ADD COLUMN actualizado_por UUID;

UPDATE condiciones_pago
SET tipo = CASE WHEN dias = 0 THEN 'CASH' ELSE 'CREDIT' END
WHERE tipo IS NULL;

ALTER TABLE condiciones_pago ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE condiciones_pago DROP CONSTRAINT IF EXISTS condiciones_pago_dias_check;
ALTER TABLE condiciones_pago
    ADD CONSTRAINT ck_condiciones_pago_tipo_dias CHECK (
        (tipo = 'CASH' AND dias = 0)
        OR
        (tipo = 'CREDIT' AND dias BETWEEN 1 AND 3650)
    );

WITH repetidas AS (
    SELECT id, codigo, nombre,
           row_number() OVER (
               PARTITION BY tenant_id,empresa_id,lower(btrim(nombre))
               ORDER BY id
           ) AS posicion
    FROM condiciones_pago
)
UPDATE condiciones_pago c
SET nombre = left(r.nombre, 80) || ' (' || r.codigo || ')'
FROM repetidas r
WHERE c.id=r.id AND r.posicion>1;

CREATE UNIQUE INDEX uk_condiciones_tenant_empresa_nombre
    ON condiciones_pago(tenant_id, empresa_id, lower(btrim(nombre)));
CREATE INDEX idx_condiciones_tenant_empresa_estado_nombre
    ON condiciones_pago(tenant_id, empresa_id, activo, nombre);

INSERT INTO condiciones_pago(tenant_id,empresa_id,codigo,nombre,tipo,dias,descripcion,activo)
SELECT e.tenant_id,e.id,'CONTADO','Contado','CASH',0,'Pago inmediato.',TRUE
FROM empresas e
WHERE NOT EXISTS (
    SELECT 1 FROM condiciones_pago c
    WHERE c.tenant_id=e.tenant_id AND c.empresa_id=e.id AND lower(btrim(c.nombre))='contado'
);

CREATE OR REPLACE FUNCTION contacloud_crear_condicion_contado()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO condiciones_pago(tenant_id,empresa_id,codigo,nombre,tipo,dias,descripcion,activo)
    VALUES (NEW.tenant_id,NEW.id,'CONTADO','Contado','CASH',0,'Pago inmediato.',TRUE)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_empresa_condicion_contado ON empresas;
CREATE TRIGGER trg_empresa_condicion_contado
AFTER INSERT ON empresas
FOR EACH ROW EXECUTE FUNCTION contacloud_crear_condicion_contado();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('condiciones_pago.ver','Ver condiciones de pago','Consultar condiciones de pago','COMERCIAL','condiciones_pago','ver'),
('condiciones_pago.crear','Crear condiciones de pago','Crear condiciones de pago','COMERCIAL','condiciones_pago','crear'),
('condiciones_pago.editar','Editar condiciones de pago','Modificar condiciones de pago','COMERCIAL','condiciones_pago','editar'),
('condiciones_pago.desactivar','Cambiar estado de condiciones','Desactivar o reactivar condiciones de pago','COMERCIAL','condiciones_pago','desactivar'),
('CONDICION_PAGO_CREAR','Crear condiciones de pago','Crear condiciones de pago','COMERCIAL','condiciones_pago','crear'),
('CONDICION_PAGO_DESACTIVAR','Cambiar estado de condiciones','Desactivar o reactivar condiciones de pago','COMERCIAL','condiciones_pago','desactivar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT DISTINCT rp.empresa_id,rp.rol_id,nuevo.id
FROM rol_permisos rp
JOIN permisos anterior ON anterior.id=rp.permiso_id
JOIN permisos nuevo ON nuevo.codigo = CASE anterior.codigo
    WHEN 'CONDICION_PAGO_VER' THEN 'condiciones_pago.ver'
    WHEN 'CONDICION_PAGO_EDITAR' THEN 'condiciones_pago.editar'
END
WHERE anterior.codigo IN ('CONDICION_PAGO_VER','CONDICION_PAGO_EDITAR')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.recurso='condiciones_pago'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
