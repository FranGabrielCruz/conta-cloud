ALTER TABLE impuestos ADD COLUMN IF NOT EXISTS descripcion VARCHAR(500);

ALTER TABLE tipos_comprobantes_fiscales ADD COLUMN IF NOT EXISTS descripcion VARCHAR(500);

ALTER TABLE secuencias ADD COLUMN IF NOT EXISTS numero_inicial BIGINT NOT NULL DEFAULT 1;
ALTER TABLE secuencias ADD COLUMN IF NOT EXISTS numero_final BIGINT;

ALTER TABLE secuencias DROP CONSTRAINT IF EXISTS ck_secuencias_rango;
ALTER TABLE secuencias ADD CONSTRAINT ck_secuencias_rango CHECK (
    numero_inicial > 0
    AND valor_actual >= numero_inicial - 1
    AND (numero_final IS NULL OR numero_final >= numero_inicial)
    AND (numero_final IS NULL OR valor_actual <= numero_final)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_secuencias_empresa_comprobante_prefijo
    ON secuencias(empresa_id, tipo_comprobante_id, COALESCE(serie, ''));

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('impuestos.ver','Ver impuestos','Consultar impuestos de la empresa','FISCAL','impuestos','ver'),
('impuestos.crear','Crear impuestos','Crear impuestos de la empresa','FISCAL','impuestos','crear'),
('impuestos.editar','Editar impuestos','Editar impuestos de la empresa','FISCAL','impuestos','editar'),
('impuestos.desactivar','Cambiar estado de impuestos','Desactivar o reactivar impuestos','FISCAL','impuestos','desactivar'),
('comprobantes_fiscales.ver','Ver comprobantes fiscales','Consultar comprobantes fiscales','FISCAL','comprobantes_fiscales','ver'),
('comprobantes_fiscales.crear','Crear comprobantes fiscales','Crear comprobantes fiscales','FISCAL','comprobantes_fiscales','crear'),
('comprobantes_fiscales.editar','Editar comprobantes fiscales','Editar comprobantes fiscales','FISCAL','comprobantes_fiscales','editar'),
('comprobantes_fiscales.desactivar','Cambiar estado de comprobantes','Desactivar o reactivar comprobantes','FISCAL','comprobantes_fiscales','desactivar'),
('secuencias.ver','Ver secuencias','Consultar secuencias fiscales','FISCAL','secuencias','ver'),
('secuencias.crear','Crear secuencias','Crear secuencias fiscales','FISCAL','secuencias','crear'),
('secuencias.editar','Editar secuencias','Editar rangos de secuencias fiscales','FISCAL','secuencias','editar'),
('secuencias.desactivar','Cambiar estado de secuencias','Desactivar o reactivar secuencias','FISCAL','secuencias','desactivar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT rp.empresa_id,rp.rol_id,nuevo.id
FROM rol_permisos rp
JOIN permisos anterior ON anterior.id=rp.permiso_id
JOIN permisos nuevo ON nuevo.codigo = CASE anterior.codigo
    WHEN 'IMPUESTO_VER' THEN 'impuestos.ver'
    WHEN 'IMPUESTO_CREAR' THEN 'impuestos.crear'
    WHEN 'IMPUESTO_EDITAR' THEN 'impuestos.editar'
    WHEN 'COMPROBANTE_VER' THEN 'comprobantes_fiscales.ver'
    WHEN 'COMPROBANTE_EDITAR' THEN 'comprobantes_fiscales.editar'
    WHEN 'SECUENCIA_VER' THEN 'secuencias.ver'
    WHEN 'SECUENCIA_EDITAR' THEN 'secuencias.editar'
END
WHERE anterior.codigo IN ('IMPUESTO_VER','IMPUESTO_CREAR','IMPUESTO_EDITAR','COMPROBANTE_VER','COMPROBANTE_EDITAR','SECUENCIA_VER','SECUENCIA_EDITAR')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.modulo='FISCAL'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT DISTINCT rp.empresa_id,rp.rol_id,nuevo.id
FROM rol_permisos rp
JOIN permisos anterior ON anterior.id=rp.permiso_id
JOIN permisos nuevo ON nuevo.codigo = CASE
    WHEN anterior.codigo='IMPUESTO_EDITAR' THEN 'impuestos.desactivar'
    WHEN anterior.codigo='COMPROBANTE_EDITAR' THEN 'comprobantes_fiscales.desactivar'
    WHEN anterior.codigo='SECUENCIA_EDITAR' THEN 'secuencias.desactivar'
END
WHERE anterior.codigo IN ('IMPUESTO_EDITAR','COMPROBANTE_EDITAR','SECUENCIA_EDITAR')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
