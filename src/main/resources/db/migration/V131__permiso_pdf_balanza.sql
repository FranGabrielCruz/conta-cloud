INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('balanza.pdf','Ver PDF de la Balanza de Comprobación','Consultar el PDF de la Balanza de Comprobación','CONTABILIDAD','balanza','pdf')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo='balanza.pdf'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
