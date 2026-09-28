DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM secuencias
         WHERE tipo_comprobante_id IS NOT NULL AND NULLIF(TRIM(serie),'') IS NOT NULL
         GROUP BY tenant_id,empresa_id,tipo_comprobante_id
        HAVING COUNT(DISTINCT UPPER(TRIM(serie))) > 1
    ) THEN
        RAISE EXCEPTION 'Existen secuencias del mismo comprobante con prefijos diferentes. Resuelva la inconsistencia antes de continuar.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM tipos_comprobantes_fiscales c
        JOIN secuencias s ON s.tenant_id=c.tenant_id AND s.empresa_id=c.empresa_id AND s.tipo_comprobante_id=c.id
        WHERE NULLIF(TRIM(c.prefijo),'') IS NOT NULL AND NULLIF(TRIM(s.serie),'') IS NOT NULL
          AND UPPER(TRIM(c.prefijo)) <> UPPER(TRIM(s.serie))
    ) THEN
        RAISE EXCEPTION 'El prefijo del comprobante no coincide con el de sus secuencias. Resuelva la inconsistencia antes de continuar.';
    END IF;
END $$;

UPDATE tipos_comprobantes_fiscales c SET prefijo=COALESCE(
    NULLIF(UPPER(TRIM(c.prefijo)),''),
    (SELECT MIN(UPPER(TRIM(s.serie))) FROM secuencias s
      WHERE s.tenant_id=c.tenant_id AND s.empresa_id=c.empresa_id AND s.tipo_comprobante_id=c.id
        AND NULLIF(TRIM(s.serie),'') IS NOT NULL),
    UPPER(TRIM(c.codigo)));

ALTER TABLE tipos_comprobantes_fiscales ALTER COLUMN prefijo SET NOT NULL;
ALTER TABLE tipos_comprobantes_fiscales ADD COLUMN IF NOT EXISTS creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tipos_comprobantes_fiscales ADD COLUMN IF NOT EXISTS actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE secuencias ADD COLUMN IF NOT EXISTS creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE secuencias ADD COLUMN IF NOT EXISTS actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

DROP INDEX IF EXISTS uk_secuencias_empresa_comprobante_prefijo;

UPDATE secuencias SET activo=FALSE,actualizado_en=CURRENT_TIMESTAMP
 WHERE activo=TRUE AND numero_final IS NOT NULL AND valor_actual>=numero_final;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM secuencias WHERE activo=TRUE AND tipo_comprobante_id IS NOT NULL
         GROUP BY tenant_id,empresa_id,tipo_comprobante_id HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen múltiples secuencias activas para un mismo comprobante. Resuelva la inconsistencia antes de continuar.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_secuencia_activa_por_comprobante
    ON secuencias(tenant_id,empresa_id,tipo_comprobante_id)
    WHERE activo=TRUE AND tipo_comprobante_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_secuencias_comprobante
    ON secuencias(tenant_id,empresa_id,tipo_comprobante_id);

ALTER TABLE secuencias DROP COLUMN IF EXISTS serie;
