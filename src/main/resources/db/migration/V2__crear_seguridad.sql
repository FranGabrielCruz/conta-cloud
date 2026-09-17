CREATE TABLE usuarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    usuario VARCHAR(60) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    correo VARCHAR(180),
    password_hash VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_acceso_en TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuarios_empresa_usuario UNIQUE (empresa_id, usuario),
    CONSTRAINT uk_usuarios_empresa_id UNIQUE (empresa_id, id)
);
CREATE INDEX idx_usuarios_empresa ON usuarios(empresa_id);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    codigo VARCHAR(50) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_roles_empresa_codigo UNIQUE (empresa_id, codigo),
    CONSTRAINT uk_roles_empresa_id UNIQUE (empresa_id, id)
);
CREATE INDEX idx_roles_empresa ON roles(empresa_id);

CREATE TABLE permisos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(80) NOT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(255)
);

CREATE TABLE usuario_roles (
    empresa_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    rol_id UUID NOT NULL,
    PRIMARY KEY (usuario_id, rol_id),
    FOREIGN KEY (empresa_id, usuario_id) REFERENCES usuarios(empresa_id, id),
    FOREIGN KEY (empresa_id, rol_id) REFERENCES roles(empresa_id, id)
);
CREATE INDEX idx_usuario_roles_empresa ON usuario_roles(empresa_id);

CREATE TABLE rol_permisos (
    empresa_id UUID NOT NULL,
    rol_id UUID NOT NULL,
    permiso_id UUID NOT NULL REFERENCES permisos(id),
    PRIMARY KEY (rol_id, permiso_id),
    FOREIGN KEY (empresa_id, rol_id) REFERENCES roles(empresa_id, id)
);
CREATE INDEX idx_rol_permisos_empresa ON rol_permisos(empresa_id);

INSERT INTO permisos (codigo, nombre) VALUES
('EMPRESA_VER', 'Ver datos de empresa'),
('EMPRESA_EDITAR', 'Editar datos de empresa'),
('USUARIO_VER', 'Ver usuarios'), ('USUARIO_CREAR', 'Crear usuarios'),
('USUARIO_EDITAR', 'Editar usuarios'), ('USUARIO_DESACTIVAR', 'Desactivar usuarios'),
('ROL_VER', 'Ver roles'), ('ROL_CREAR', 'Crear roles'), ('ROL_EDITAR', 'Editar roles'),
('SUCURSAL_VER', 'Ver sucursales'), ('SUCURSAL_CREAR', 'Crear sucursales'), ('SUCURSAL_EDITAR', 'Editar sucursales'),
('MONEDA_VER', 'Ver monedas'), ('MONEDA_CREAR', 'Crear monedas'), ('MONEDA_EDITAR', 'Editar monedas'),
('TASA_CAMBIO_VER', 'Ver tasas de cambio'), ('TASA_CAMBIO_CREAR', 'Crear tasas de cambio'),
('IMPUESTO_VER', 'Ver impuestos'), ('IMPUESTO_CREAR', 'Crear impuestos'), ('IMPUESTO_EDITAR', 'Editar impuestos'),
('COMPROBANTE_VER', 'Ver comprobantes'), ('COMPROBANTE_EDITAR', 'Editar comprobantes'),
('SECUENCIA_VER', 'Ver secuencias'), ('SECUENCIA_EDITAR', 'Editar secuencias'),
('CONDICION_PAGO_VER', 'Ver condiciones de pago'), ('CONDICION_PAGO_EDITAR', 'Editar condiciones de pago'),
('PERIODO_VER', 'Ver periodos'), ('PERIODO_CREAR', 'Crear periodos'), ('PERIODO_CERRAR', 'Cerrar periodos'),
('CONFIGURACION_CONTABLE_VER', 'Ver configuracion contable'),
('CONFIGURACION_CONTABLE_EDITAR', 'Editar configuracion contable'),
('AUDITORIA_VER', 'Ver auditoria');
