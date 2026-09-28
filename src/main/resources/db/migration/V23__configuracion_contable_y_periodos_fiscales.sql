ALTER TABLE configuracion_contable
    ADD COLUMN IF NOT EXISTS metodo_contable VARCHAR(20) NOT NULL DEFAULT 'DEVENGADO',
    ADD COLUMN IF NOT EXISTS contabilizacion_automatica BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS permitir_periodos_cerrados BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE configuracion_contable DROP CONSTRAINT IF EXISTS ck_configuracion_metodo_contable;
ALTER TABLE configuracion_contable ADD CONSTRAINT ck_configuracion_metodo_contable
    CHECK (metodo_contable IN ('DEVENGADO', 'EFECTIVO'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_configuracion_contable_tenant_empresa
    ON configuracion_contable(tenant_id, empresa_id);

INSERT INTO configuracion_contable(
    tenant_id, empresa_id, moneda_base_id, mes_inicio_fiscal, metodo_numeracion,
    decimales, metodo_contable, contabilizacion_automatica, permitir_periodos_cerrados)
SELECT e.tenant_id, e.id, m.id, 1, 'POR_SECUENCIA', m.decimales,
       'DEVENGADO', FALSE, FALSE
  FROM empresas e
  JOIN monedas m ON m.tenant_id=e.tenant_id AND m.empresa_id=e.id AND m.moneda_base=TRUE
 WHERE NOT EXISTS (
       SELECT 1 FROM configuracion_contable c
        WHERE c.tenant_id=e.tenant_id AND c.empresa_id=e.id)
ON CONFLICT (empresa_id) DO NOTHING;

CREATE OR REPLACE FUNCTION contacloud_inicializar_configuracion_contable()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.moneda_base THEN
        INSERT INTO configuracion_contable(
            tenant_id, empresa_id, moneda_base_id, mes_inicio_fiscal,
            metodo_numeracion, decimales, metodo_contable,
            contabilizacion_automatica, permitir_periodos_cerrados)
        VALUES (NEW.tenant_id, NEW.empresa_id, NEW.id, 1,
                'POR_SECUENCIA', NEW.decimales, 'DEVENGADO', FALSE, FALSE)
        ON CONFLICT (empresa_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_monedas_inicializar_configuracion_contable ON monedas;
CREATE TRIGGER trg_monedas_inicializar_configuracion_contable
AFTER INSERT OR UPDATE OF moneda_base ON monedas
FOR EACH ROW EXECUTE FUNCTION contacloud_inicializar_configuracion_contable();

ALTER TABLE periodos_fiscales
    ADD COLUMN IF NOT EXISTS nombre VARCHAR(120),
    ADD COLUMN IF NOT EXISTS fecha_cierre TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS usuario_cierre_id UUID,
    ADD COLUMN IF NOT EXISTS fecha_reapertura TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS usuario_reapertura_id UUID,
    ADD COLUMN IF NOT EXISTS creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE periodos_fiscales
   SET nombre = 'Período ' || anio || '-' || LPAD(mes::TEXT, 2, '0')
 WHERE nombre IS NULL OR BTRIM(nombre)='';

ALTER TABLE periodos_fiscales ALTER COLUMN nombre SET NOT NULL;
ALTER TABLE periodos_fiscales DROP CONSTRAINT IF EXISTS uk_periodos_empresa_anio_mes;

CREATE INDEX IF NOT EXISTS idx_periodos_tenant_empresa_fechas
    ON periodos_fiscales(tenant_id, empresa_id, fecha_inicial, fecha_final);
CREATE INDEX IF NOT EXISTS idx_periodos_tenant_empresa_estado
    ON periodos_fiscales(tenant_id, empresa_id, estado);

CREATE OR REPLACE FUNCTION contacloud_validar_solapamiento_periodo()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM periodos_fiscales p
         WHERE p.tenant_id=NEW.tenant_id
           AND p.empresa_id=NEW.empresa_id
           AND p.id<>NEW.id
           AND p.fecha_inicial<=NEW.fecha_final
           AND p.fecha_final>=NEW.fecha_inicial
    ) THEN
        RAISE EXCEPTION 'El período fiscal se solapa con otro período existente';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_periodos_fiscales_no_solapamiento ON periodos_fiscales;
CREATE TRIGGER trg_periodos_fiscales_no_solapamiento
BEFORE INSERT OR UPDATE OF tenant_id, empresa_id, fecha_inicial, fecha_final
ON periodos_fiscales FOR EACH ROW
EXECUTE FUNCTION contacloud_validar_solapamiento_periodo();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('configuracion_contable.ver','Ver configuración contable','Consultar la configuración contable de la empresa','CONTABILIDAD','configuracion_contable','ver'),
('configuracion_contable.editar','Editar configuración contable','Actualizar la configuración contable de la empresa','CONTABILIDAD','configuracion_contable','editar'),
('periodos_fiscales.ver','Ver períodos fiscales','Consultar períodos fiscales de la empresa','CONTABILIDAD','periodos_fiscales','ver'),
('periodos_fiscales.crear','Crear períodos fiscales','Crear períodos fiscales de la empresa','CONTABILIDAD','periodos_fiscales','crear'),
('periodos_fiscales.editar','Editar períodos fiscales','Editar períodos fiscales abiertos','CONTABILIDAD','periodos_fiscales','editar'),
('periodos_fiscales.cerrar','Cerrar períodos fiscales','Cerrar períodos fiscales abiertos','CONTABILIDAD','periodos_fiscales','cerrar'),
('periodos_fiscales.reabrir','Reabrir períodos fiscales','Reabrir períodos fiscales cerrados','CONTABILIDAD','periodos_fiscales','reabrir')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT DISTINCT rp.empresa_id,rp.rol_id,nuevo.id
  FROM rol_permisos rp
  JOIN permisos anterior ON anterior.id=rp.permiso_id
  JOIN permisos nuevo ON nuevo.codigo=CASE anterior.codigo
      WHEN 'CONFIGURACION_CONTABLE_VER' THEN 'configuracion_contable.ver'
      WHEN 'CONFIGURACION_CONTABLE_EDITAR' THEN 'configuracion_contable.editar'
      WHEN 'PERIODO_VER' THEN 'periodos_fiscales.ver'
      WHEN 'PERIODO_CREAR' THEN 'periodos_fiscales.crear'
      WHEN 'PERIODO_CERRAR' THEN 'periodos_fiscales.cerrar'
  END
 WHERE anterior.codigo IN ('CONFIGURACION_CONTABLE_VER','CONFIGURACION_CONTABLE_EDITAR',
                            'PERIODO_VER','PERIODO_CREAR','PERIODO_CERRAR')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
  FROM roles r CROSS JOIN permisos p
 WHERE r.codigo='ADMINISTRADOR' AND p.modulo='CONTABILIDAD'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
