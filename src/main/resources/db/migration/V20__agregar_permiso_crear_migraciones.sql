INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('migraciones.crear','Crear migraciones','Programar migraciones controladas de tenants','INFRAESTRUCTURA','migraciones','crear')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
  FROM roles r
  JOIN permisos p ON p.codigo='migraciones.crear'
 WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
