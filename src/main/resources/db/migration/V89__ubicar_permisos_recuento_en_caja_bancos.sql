UPDATE permisos
SET modulo = 'CAJA_BANCOS'
WHERE codigo IN ('recuentos_caja.crear','recuentos_caja.ver','recuentos_caja.imprimir')
  AND modulo <> 'CAJA_BANCOS';
