INSERT INTO rol_permisos(empresa_id, rol_id, permiso_id)
SELECT r.empresa_id, r.id, p.id
FROM roles r
JOIN empresas e ON e.id = r.empresa_id
CROSS JOIN permisos p
WHERE upper(r.codigo) = 'ADMINISTRADOR'
  AND (upper(e.codigo) = 'DEMO' OR upper(e.nombre) = 'DEMO')
  AND p.codigo = 'inventario_movimientos.pdf'
ON CONFLICT(rol_id, permiso_id) DO NOTHING;
