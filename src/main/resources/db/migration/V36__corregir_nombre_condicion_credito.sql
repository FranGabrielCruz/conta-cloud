UPDATE condiciones_pago c
SET nombre='Crédito',
    actualizado_en=CURRENT_TIMESTAMP
WHERE c.tipo='CREDIT'
  AND lower(btrim(c.nombre)) IN ('fiao','fíao','fia','fiado')
  AND NOT EXISTS (
      SELECT 1
      FROM condiciones_pago existente
      WHERE existente.tenant_id=c.tenant_id
        AND existente.empresa_id=c.empresa_id
        AND lower(btrim(existente.nombre))='crédito'
        AND existente.id<>c.id
  );
