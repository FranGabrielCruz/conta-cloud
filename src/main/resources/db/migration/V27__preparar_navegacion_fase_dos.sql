INSERT INTO module_catalog(module_key,name,description,active,implemented,core,display_order) VALUES
('VENTAS','Ventas','Navegación de ventas, clientes y cuentas por cobrar',TRUE,TRUE,FALSE,200),
('COMPRAS','Compras','Navegación de compras, proveedores y cuentas por pagar',TRUE,TRUE,FALSE,210),
('CAJA_BANCOS','Caja y Bancos','Navegación de cajas, bancos y conciliación',TRUE,TRUE,FALSE,220)
ON CONFLICT(module_key) DO UPDATE SET
    name=EXCLUDED.name,
    description=EXCLUDED.description,
    active=TRUE,
    implemented=TRUE,
    core=FALSE,
    display_order=EXCLUDED.display_order;

-- Los módulos quedan disponibles para administración, pero no se habilitan
-- silenciosamente en empresas existentes.
INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,m.module_key,FALSE
FROM empresas e
CROSS JOIN module_catalog m
WHERE m.module_key IN ('VENTAS','COMPRAS','CAJA_BANCOS')
ON CONFLICT(empresa_id,module_key) DO NOTHING;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('clientes.ver','Ver clientes','Consultar la opción Clientes','VENTAS','clientes','ver'),
('cotizaciones.ver','Ver cotizaciones','Consultar la opción Cotizaciones','VENTAS','cotizaciones','ver'),
('facturas.ver','Ver facturas','Consultar la opción Facturas','VENTAS','facturas','ver'),
('notas_credito.ver','Ver notas de crédito','Consultar notas de crédito de clientes','VENTAS','notas_credito','ver'),
('notas_debito.ver','Ver notas de débito','Consultar notas de débito de clientes','VENTAS','notas_debito','ver'),
('recibos.ver','Ver recibos','Consultar la opción Recibos','VENTAS','recibos','ver'),
('cuentas_cobrar.ver','Ver cuentas por cobrar','Consultar cuentas por cobrar','VENTAS','cuentas_cobrar','ver'),
('proveedores.ver','Ver proveedores','Consultar la opción Proveedores','COMPRAS','proveedores','ver'),
('ordenes_compra.ver','Ver órdenes de compra','Consultar órdenes de compra','COMPRAS','ordenes_compra','ver'),
('facturas_proveedores.ver','Ver facturas de proveedores','Consultar facturas de proveedores','COMPRAS','facturas_proveedores','ver'),
('notas_credito_proveedores.ver','Ver notas de crédito de proveedores','Consultar notas de crédito de proveedores','COMPRAS','notas_credito_proveedores','ver'),
('pagos_proveedores.ver','Ver pagos a proveedores','Consultar pagos a proveedores','COMPRAS','pagos_proveedores','ver'),
('cuentas_pagar.ver','Ver cuentas por pagar','Consultar cuentas por pagar','COMPRAS','cuentas_pagar','ver'),
('cajas.ver','Ver cajas','Consultar la opción Cajas','CAJA_BANCOS','cajas','ver'),
('cuentas_bancarias.ver','Ver cuentas bancarias','Consultar cuentas bancarias','CAJA_BANCOS','cuentas_bancarias','ver'),
('ingresos.ver','Ver ingresos','Consultar movimientos de ingreso','CAJA_BANCOS','ingresos','ver'),
('egresos.ver','Ver egresos','Consultar movimientos de egreso','CAJA_BANCOS','egresos','ver'),
('transferencias.ver','Ver transferencias','Consultar transferencias','CAJA_BANCOS','transferencias','ver'),
('conciliacion_bancaria.ver','Ver conciliación bancaria','Consultar conciliación bancaria','CAJA_BANCOS','conciliacion_bancaria','ver')
ON CONFLICT(codigo) DO NOTHING;

-- El administrador conserva administración completa una vez que el módulo
-- correspondiente sea habilitado para su empresa.
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR'
  AND p.modulo IN ('VENTAS','COMPRAS','CAJA_BANCOS')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
