CREATE TABLE module_catalog (
    module_key VARCHAR(60) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    implemented BOOLEAN NOT NULL DEFAULT TRUE,
    core BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL,
    required_module_key VARCHAR(60) REFERENCES module_catalog(module_key)
);

INSERT INTO module_catalog(module_key,name,description,core,display_order) VALUES
('EMPRESA','Empresa','Datos generales y configuración empresarial',TRUE,10),
('USUARIOS','Usuarios','Usuarios y accesos empresariales',TRUE,20),
('ROLES','Roles y permisos','Roles y autorización',TRUE,30),
('SUCURSALES','Sucursales','Organización por sucursales',TRUE,40),
('MONEDAS','Monedas','Monedas y moneda base',TRUE,50),
('TASAS_CAMBIO','Tasas de cambio','Histórico de tasas de cambio',FALSE,60),
('IMPUESTOS','Impuestos','Configuración fiscal de impuestos',FALSE,70),
('COMPROBANTES_FISCALES','Comprobantes fiscales','Tipos de comprobantes fiscales',FALSE,80),
('SECUENCIAS','Secuencias','Numeración segura de documentos',FALSE,90),
('CONDICIONES_PAGO','Condiciones de pago','Configuración comercial',FALSE,100),
('PERIODOS_FISCALES','Períodos fiscales','Períodos contables y fiscales',FALSE,110),
('CONFIGURACION_CONTABLE','Configuración contable','Parámetros contables generales',FALSE,120)
ON CONFLICT(module_key) DO NOTHING;

CREATE TABLE empresa_modulos (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    module_key VARCHAR(60) NOT NULL REFERENCES module_catalog(module_key),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(empresa_id,module_key),
    CONSTRAINT fk_empresa_modulo_empresa FOREIGN KEY(empresa_id) REFERENCES empresas(id)
);
CREATE INDEX idx_empresa_modulos_tenant_empresa ON empresa_modulos(tenant_id,empresa_id,enabled);

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,m.module_key,TRUE FROM empresas e CROSS JOIN module_catalog m
WHERE m.active AND m.implemented
ON CONFLICT(empresa_id,module_key) DO NOTHING;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('empresas.ver','Ver empresas','Consultar empresas autorizadas','CORE','empresas','ver'),
('empresas.crear','Crear empresas','Crear empresas y ejecutar su configuración inicial','CORE','empresas','crear'),
('empresas.editar','Editar empresas','Modificar datos empresariales','CORE','empresas','editar'),
('empresas.desactivar','Desactivar empresas','Desactivar empresas sin eliminar datos','CORE','empresas','desactivar'),
('empresas.configurar_modulos','Configurar módulos','Habilitar o deshabilitar módulos empresariales','CORE','empresas','configurar_modulos'),
('empresas.seleccionar_base','Seleccionar base','Seleccionar manualmente un nodo elegible','INFRAESTRUCTURA','empresas','seleccionar_base'),
('bases_datos.ver','Ver bases de datos','Consultar nodos sin exponer secretos','INFRAESTRUCTURA','bases_datos','ver'),
('bases_datos.crear','Crear bases de datos','Registrar nuevos nodos operacionales','INFRAESTRUCTURA','bases_datos','crear'),
('bases_datos.editar','Editar bases de datos','Editar configuración administrativa de nodos','INFRAESTRUCTURA','bases_datos','editar'),
('bases_datos.cambiar_estado','Cambiar estado de bases','Administrar el ciclo de vida de nodos','INFRAESTRUCTURA','bases_datos','cambiar_estado'),
('migraciones.ver','Ver migraciones','Consultar migraciones de tenants','INFRAESTRUCTURA','migraciones','ver'),
('migraciones.ejecutar','Ejecutar migraciones','Ejecutar migraciones controladas de tenants','INFRAESTRUCTURA','migraciones','ejecutar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

