ALTER TABLE usuarios
    ADD COLUMN tenant_id UUID,
    ADD COLUMN apellido VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN telefono VARCHAR(40);

UPDATE usuarios u
   SET tenant_id = e.tenant_id
  FROM empresas e
 WHERE e.id = u.empresa_id;

ALTER TABLE usuarios ALTER COLUMN tenant_id SET NOT NULL;
CREATE UNIQUE INDEX uk_usuarios_tenant_usuario_ci ON usuarios(tenant_id, LOWER(usuario));
CREATE INDEX idx_usuarios_tenant ON usuarios(tenant_id);

ALTER TABLE auditoria DROP CONSTRAINT IF EXISTS auditoria_empresa_id_usuario_id_fkey;
ALTER TABLE auditoria ADD CONSTRAINT fk_auditoria_usuario FOREIGN KEY(usuario_id) REFERENCES usuarios(id);

ALTER TABLE roles
    ADD COLUMN descripcion VARCHAR(255),
    ADD COLUMN protegido BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE roles SET protegido = TRUE WHERE UPPER(codigo) = 'ADMINISTRADOR';
CREATE UNIQUE INDEX uk_roles_empresa_nombre_ci ON roles(empresa_id, LOWER(nombre));

CREATE TABLE usuario_empresa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    rol_id UUID NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    acceso_todas_sucursales BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuario_empresa UNIQUE(usuario_id, empresa_id),
    CONSTRAINT fk_usuario_empresa_rol FOREIGN KEY(empresa_id, rol_id) REFERENCES roles(empresa_id, id)
);
CREATE INDEX idx_usuario_empresa_empresa_activo ON usuario_empresa(empresa_id, activo);
CREATE INDEX idx_usuario_empresa_rol ON usuario_empresa(rol_id);

INSERT INTO usuario_empresa(usuario_id,empresa_id,rol_id,activo,acceso_todas_sucursales)
SELECT usuario_id,empresa_id,rol_id,TRUE,TRUE
  FROM (
      SELECT ur.*, ROW_NUMBER() OVER (
          PARTITION BY ur.usuario_id, ur.empresa_id
          ORDER BY CASE WHEN r.codigo='ADMINISTRADOR' THEN 0 ELSE 1 END, r.codigo
      ) AS posicion
      FROM usuario_roles ur JOIN roles r ON r.id=ur.rol_id
  ) seleccion
 WHERE posicion=1
ON CONFLICT(usuario_id,empresa_id) DO NOTHING;

CREATE TABLE usuario_sucursal (
    usuario_empresa_id UUID NOT NULL REFERENCES usuario_empresa(id) ON DELETE CASCADE,
    sucursal_id UUID NOT NULL REFERENCES sucursales(id),
    PRIMARY KEY(usuario_empresa_id,sucursal_id)
);
CREATE INDEX idx_usuario_sucursal_sucursal ON usuario_sucursal(sucursal_id);

ALTER TABLE permisos
    ADD COLUMN modulo VARCHAR(60) NOT NULL DEFAULT 'CORE',
    ADD COLUMN recurso VARCHAR(60),
    ADD COLUMN accion VARCHAR(40);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('usuarios.ver','Ver usuarios','Consultar usuarios y sus accesos','CORE','usuarios','ver'),
('usuarios.crear','Crear usuarios','Crear identidades y accesos empresariales','CORE','usuarios','crear'),
('usuarios.editar','Editar usuarios','Modificar identidades y accesos empresariales','CORE','usuarios','editar'),
('usuarios.desactivar','Desactivar usuarios','Desactivar acceso empresarial de usuarios','CORE','usuarios','desactivar'),
('roles.ver','Ver roles','Consultar roles y permisos','CORE','roles','ver'),
('roles.crear','Crear roles','Crear roles empresariales','CORE','roles','crear'),
('roles.editar','Editar roles','Modificar roles y permisos','CORE','roles','editar'),
('roles.desactivar','Desactivar roles','Desactivar roles empresariales','CORE','roles','desactivar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('ROL_DESACTIVAR','Desactivar roles','Desactivar roles empresariales','CORE','roles','desactivar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
  FROM roles r CROSS JOIN permisos p
 WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
