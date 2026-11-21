ALTER TABLE configuracion_contable ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE configuracion_contable SET permitir_periodos_cerrados=false;
ALTER TABLE configuracion_contable ADD CONSTRAINT ck_configuracion_periodos_cerrados CHECK (NOT permitir_periodos_cerrados);
CREATE TABLE configuracion_contable_modulo (
 tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
 modulo VARCHAR(20) NOT NULL CHECK (modulo IN ('VENTAS','COMPRAS','INVENTARIO','CXC','CXP','FINANZAS','ACTIVOS_FIJOS')),
 automatico BOOLEAN NOT NULL DEFAULT false,
 PRIMARY KEY (tenant_id,empresa_id,modulo),
 FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);
INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('configuracion_contable.automatizacion','Actualizar automatización contable','Activar o desactivar contabilización global y por módulo',
 'CONFIGURACION_CONTABLE','configuracion_contable','automatizacion') ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo='configuracion_contable.automatizacion'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

