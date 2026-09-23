INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('empresas.activar','Activar empresas','Reactivar empresas previamente desactivadas','CORE','empresas','activar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
  FROM roles r
  JOIN permisos p ON p.codigo='empresas.activar'
 WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
