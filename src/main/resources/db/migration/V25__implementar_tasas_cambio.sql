ALTER TABLE tasas_cambio RENAME COLUMN moneda_id TO moneda_origen_id;
ALTER TABLE tasas_cambio ADD COLUMN moneda_destino_id UUID;

UPDATE tasas_cambio t
SET moneda_destino_id = (
    SELECT m.id
    FROM monedas m
    WHERE m.empresa_id = t.empresa_id
      AND m.id <> t.moneda_origen_id
    ORDER BY m.moneda_base DESC, m.activo DESC, m.codigo_iso
    LIMIT 1
)
WHERE t.moneda_destino_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tasas_cambio WHERE moneda_destino_id IS NULL) THEN
        RAISE EXCEPTION 'No se puede migrar tasas de cambio sin una moneda destino disponible';
    END IF;
END $$;

ALTER TABLE tasas_cambio ALTER COLUMN moneda_destino_id SET NOT NULL;
ALTER TABLE tasas_cambio ALTER COLUMN tasa TYPE NUMERIC(19,8);
ALTER TABLE tasas_cambio ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tasas_cambio ADD COLUMN creado_por UUID;
ALTER TABLE tasas_cambio ADD COLUMN actualizado_por UUID;

ALTER TABLE tasas_cambio DROP CONSTRAINT IF EXISTS uk_tasas_empresa_moneda_fecha;
ALTER TABLE tasas_cambio
    ADD CONSTRAINT fk_tasas_destino_empresa
    FOREIGN KEY (empresa_id, moneda_destino_id) REFERENCES monedas(empresa_id, id);
ALTER TABLE tasas_cambio
    ADD CONSTRAINT ck_tasas_monedas_diferentes
    CHECK (moneda_origen_id <> moneda_destino_id);
ALTER TABLE tasas_cambio
    ADD CONSTRAINT uk_tasas_tenant_empresa_monedas_fecha
    UNIQUE (tenant_id, empresa_id, moneda_origen_id, moneda_destino_id, fecha);

CREATE INDEX idx_tasas_empresa_monedas_fecha
    ON tasas_cambio(tenant_id, empresa_id, moneda_origen_id, moneda_destino_id, fecha DESC);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('tasas_cambio.ver','Ver tasas de cambio','Consultar el histórico de tasas de cambio','FINANZAS','tasas_cambio','ver'),
('tasas_cambio.crear','Crear tasas de cambio','Registrar tasas de cambio','FINANZAS','tasas_cambio','crear'),
('tasas_cambio.editar','Editar tasas de cambio','Modificar tasas de cambio','FINANZAS','tasas_cambio','editar'),
('tasas_cambio.desactivar','Cambiar estado de tasas','Desactivar o reactivar tasas de cambio','FINANZAS','tasas_cambio','desactivar'),
('TASA_CAMBIO_EDITAR','Editar tasas de cambio','Modificar tasas de cambio','FINANZAS','tasas_cambio','editar'),
('TASA_CAMBIO_DESACTIVAR','Cambiar estado de tasas','Desactivar o reactivar tasas de cambio','FINANZAS','tasas_cambio','desactivar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT DISTINCT rp.empresa_id,rp.rol_id,nuevo.id
FROM rol_permisos rp
JOIN permisos anterior ON anterior.id=rp.permiso_id
JOIN permisos nuevo ON nuevo.codigo = CASE anterior.codigo
    WHEN 'TASA_CAMBIO_VER' THEN 'tasas_cambio.ver'
    WHEN 'TASA_CAMBIO_CREAR' THEN 'tasas_cambio.crear'
END
WHERE anterior.codigo IN ('TASA_CAMBIO_VER','TASA_CAMBIO_CREAR')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.recurso='tasas_cambio'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
