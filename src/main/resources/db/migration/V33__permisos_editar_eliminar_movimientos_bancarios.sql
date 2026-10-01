INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('conciliacion_bancaria.movimiento_banco_editar','Editar movimiento bancario',
 'Corregir líneas bancarias manuales pendientes en conciliaciones en proceso',
 'CAJA_BANCOS','conciliacion_bancaria','movimiento_banco_editar'),
('conciliacion_bancaria.movimiento_banco_eliminar','Eliminar movimiento bancario',
 'Eliminar líneas bancarias manuales pendientes capturadas por error',
 'CAJA_BANCOS','conciliacion_bancaria','movimiento_banco_eliminar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r
JOIN permisos p ON p.codigo IN (
    'conciliacion_bancaria.movimiento_banco_editar',
    'conciliacion_bancaria.movimiento_banco_eliminar')
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
