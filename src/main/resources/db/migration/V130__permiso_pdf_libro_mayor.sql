INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('libro_mayor.pdf','Ver PDF del Libro Mayor','Consultar el PDF del Libro Mayor','CONTABILIDAD','libro_mayor','pdf')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo='libro_mayor.pdf'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
