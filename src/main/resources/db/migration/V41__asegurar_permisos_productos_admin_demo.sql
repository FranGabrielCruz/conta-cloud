INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('productos.ver','Ver productos','Consultar productos y servicios','INVENTARIO','productos','ver'),
('productos.crear','Crear productos','Crear productos y servicios','INVENTARIO','productos','crear'),
('productos.editar','Editar productos','Modificar productos y servicios','INVENTARIO','productos','editar'),
('productos.desactivar','Desactivar productos','Desactivar productos y servicios','INVENTARIO','productos','desactivar'),
('productos.reactivar','Reactivar productos','Reactivar productos y servicios','INVENTARIO','productos','reactivar')
ON CONFLICT(codigo) DO UPDATE SET
    nombre=EXCLUDED.nombre,
    descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,
    recurso=EXCLUDED.recurso,
    accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM empresas e
JOIN roles r ON r.empresa_id=e.id
JOIN permisos p ON p.recurso='productos'
WHERE upper(e.codigo)='DEMO'
  AND upper(r.codigo)='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE
FROM empresas e
WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET
    enabled=TRUE,
    updated_at=CURRENT_TIMESTAMP;
