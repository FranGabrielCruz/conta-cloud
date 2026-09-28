DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM periodos_fiscales a
          JOIN periodos_fiscales b
            ON b.tenant_id=a.tenant_id AND b.empresa_id=a.empresa_id AND b.id>a.id
           AND b.fecha_inicial<=a.fecha_final AND b.fecha_final>=a.fecha_inicial
    ) THEN
        RAISE EXCEPTION 'Existen períodos fiscales solapados; deben corregirse antes de aplicar la migración';
    END IF;
END $$;

UPDATE empresa_modulos destino
   SET enabled=TRUE, updated_at=CURRENT_TIMESTAMP
  FROM empresa_modulos origen
 WHERE destino.tenant_id=origen.tenant_id
   AND destino.empresa_id=origen.empresa_id
   AND destino.module_key='CONFIGURACION_CONTABLE'
   AND origen.module_key='PERIODOS_FISCALES'
   AND origen.enabled=TRUE;

UPDATE empresa_modulos
   SET enabled=FALSE, updated_at=CURRENT_TIMESTAMP
 WHERE module_key='PERIODOS_FISCALES';

UPDATE module_catalog
   SET active=FALSE, implemented=FALSE
 WHERE module_key='PERIODOS_FISCALES';
