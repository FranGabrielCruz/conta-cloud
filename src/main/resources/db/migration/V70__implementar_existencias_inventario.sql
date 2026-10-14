CREATE INDEX IF NOT EXISTS idx_inventory_movement_balance_query
    ON inventory_movement(tenant_id,empresa_id,product_id,warehouse_id,direction)
    INCLUDE(base_quantity);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('inventario_existencias.ver','Ver existencias de inventario','Consultar el stock actual por producto y almacén','INVENTARIO','inventario_existencias','ver'),
('inventario_existencias.pdf','Generar PDF de existencias','Generar el reporte filtrado de existencias de inventario','INVENTARIO','inventario_existencias','pdf')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p JOIN empresas e ON e.id=r.empresa_id
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='inventario_existencias'
  AND (upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE FROM empresas e
WHERE upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
