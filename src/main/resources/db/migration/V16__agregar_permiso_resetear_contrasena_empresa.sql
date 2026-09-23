INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('empresas.resetear_contrasena','Restablecer contraseñas de usuarios',
        'Restablecer únicamente la contraseña de un usuario de una empresa','CORE','empresas','resetear_contrasena')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
  FROM roles r
  JOIN permisos p ON p.codigo='empresas.resetear_contrasena'
 WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
