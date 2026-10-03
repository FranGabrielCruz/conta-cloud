INSERT INTO module_catalog(module_key,name,description,active,implemented,core,display_order) VALUES
('INVENTARIO','Inventario','Opciones de inventario y control de existencias',TRUE,TRUE,FALSE,230),
('CONTABILIDAD','Contabilidad','Opciones operativas y consultas contables',TRUE,TRUE,FALSE,240),
('FISCAL','Fiscal','Operaciones, consultas y reportes fiscales',TRUE,TRUE,FALSE,250),
('ACTIVOS_FIJOS','Activos fijos','Administración de activos fijos',TRUE,TRUE,FALSE,260),
('FINANZAS','Finanzas','Presupuestos, proyecciones y análisis financiero',TRUE,TRUE,FALSE,270),
('REPORTES','Reportes','Reportes operativos y financieros',TRUE,TRUE,FALSE,280)
ON CONFLICT(module_key) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,m.module_key,FALSE
FROM empresas e
CROSS JOIN module_catalog m
WHERE m.module_key IN ('INVENTARIO','CONTABILIDAD','FISCAL','ACTIVOS_FIJOS','FINANZAS','REPORTES')
ON CONFLICT(empresa_id,module_key) DO NOTHING;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('productos.ver','Ver productos','Acceder a Productos','INVENTARIO','productos','ver'),
('categorias.ver','Ver categorías','Acceder a Categorías','INVENTARIO','categorias','ver'),
('almacenes.ver','Ver almacenes','Acceder a Almacenes','INVENTARIO','almacenes','ver'),
('inventario_movimientos.ver','Ver movimientos de inventario','Acceder a Movimientos de inventario','INVENTARIO','inventario_movimientos','ver'),
('inventario_transferencias.ver','Ver transferencias de inventario','Acceder a Transferencias de inventario','INVENTARIO','inventario_transferencias','ver'),
('inventario_ajustes.ver','Ver ajustes de inventario','Acceder a Ajustes de inventario','INVENTARIO','inventario_ajustes','ver'),
('kardex.ver','Ver kardex','Acceder a Kardex','INVENTARIO','kardex','ver'),
('conteo_fisico.ver','Ver conteo físico','Acceder a Conteo físico','INVENTARIO','conteo_fisico','ver'),
('catalogo_cuentas.ver','Ver catálogo de cuentas','Acceder al Catálogo de cuentas','CONTABILIDAD','catalogo_cuentas','ver'),
('asientos.ver','Ver asientos','Acceder a Asientos','CONTABILIDAD','asientos','ver'),
('libro_diario.ver','Ver libro diario','Acceder al Libro diario','CONTABILIDAD','libro_diario','ver'),
('libro_mayor.ver','Ver libro mayor','Acceder al Libro mayor','CONTABILIDAD','libro_mayor','ver'),
('balanza.ver','Ver balanza','Acceder a Balanza','CONTABILIDAD','balanza','ver'),
('centros_costos.ver','Ver centros de costos','Acceder a Centros de costos','CONTABILIDAD','centros_costos','ver'),
('cierres_contables.ver','Ver cierres contables','Acceder a Cierres contables','CONTABILIDAD','cierres_contables','ver'),
('fiscal_ncf.ver','Ver NCF','Acceder a NCF','FISCAL','fiscal_ncf','ver'),
('fiscal_ecf.ver','Ver e-CF','Acceder a e-CF','FISCAL','fiscal_ecf','ver'),
('fiscal_impuestos.ver','Ver impuestos fiscales','Acceder a Impuestos del módulo Fiscal','FISCAL','fiscal_impuestos','ver'),
('fiscal_retenciones.ver','Ver retenciones','Acceder a Retenciones','FISCAL','fiscal_retenciones','ver'),
('fiscal_606.ver','Ver 606','Acceder a 606','FISCAL','fiscal_606','ver'),
('fiscal_607.ver','Ver 607','Acceder a 607','FISCAL','fiscal_607','ver'),
('fiscal_608.ver','Ver 608','Acceder a 608','FISCAL','fiscal_608','ver'),
('fiscal_609.ver','Ver 609','Acceder a 609','FISCAL','fiscal_609','ver'),
('fiscal_it1.ver','Ver IT-1','Acceder a IT-1','FISCAL','fiscal_it1','ver'),
('fiscal_ir17.ver','Ver IR-17','Acceder a IR-17','FISCAL','fiscal_ir17','ver'),
('activos_fijos.ver','Ver activos fijos','Acceder a Activos','ACTIVOS_FIJOS','activos_fijos','ver'),
('depreciaciones.ver','Ver depreciaciones','Acceder a Depreciaciones','ACTIVOS_FIJOS','depreciaciones','ver'),
('mantenimiento_activos.ver','Ver mantenimiento de activos','Acceder a Mantenimiento de activos','ACTIVOS_FIJOS','mantenimiento_activos','ver'),
('bajas_activos.ver','Ver bajas de activos','Acceder a Bajas de activos','ACTIVOS_FIJOS','bajas_activos','ver'),
('presupuestos.ver','Ver presupuestos','Acceder a Presupuestos','FINANZAS','presupuestos','ver'),
('flujo_efectivo.ver','Ver flujo de efectivo','Acceder a Flujo de efectivo','FINANZAS','flujo_efectivo','ver'),
('proyecciones.ver','Ver proyecciones','Acceder a Proyecciones','FINANZAS','proyecciones','ver'),
('rentabilidad.ver','Ver rentabilidad','Acceder a Rentabilidad','FINANZAS','rentabilidad','ver'),
('reportes_financieros.ver','Ver reportes financieros','Acceder a Reportes financieros','REPORTES','reportes_financieros','ver'),
('reportes_ventas.ver','Ver reportes de ventas','Acceder a Reportes de ventas','REPORTES','reportes_ventas','ver'),
('reportes_compras.ver','Ver reportes de compras','Acceder a Reportes de compras','REPORTES','reportes_compras','ver'),
('reportes_inventario.ver','Ver reportes de inventario','Acceder a Reportes de inventario','REPORTES','reportes_inventario','ver'),
('reportes_fiscal.ver','Ver reportes fiscales','Acceder a Reportes fiscales','REPORTES','reportes_fiscal','ver'),
('reportes_cuentas_cobrar.ver','Ver reportes de cuentas por cobrar','Acceder a Reportes de cuentas por cobrar','REPORTES','reportes_cuentas_cobrar','ver'),
('reportes_cuentas_pagar.ver','Ver reportes de cuentas por pagar','Acceder a Reportes de cuentas por pagar','REPORTES','reportes_cuentas_pagar','ver')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
JOIN permisos p ON p.modulo IN ('INVENTARIO','CONTABILIDAD','FISCAL','ACTIVOS_FIJOS','FINANZAS','REPORTES')
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,m.module_key,TRUE
FROM empresas e
CROSS JOIN module_catalog m
WHERE upper(e.codigo)='DEMO'
  AND m.module_key IN ('INVENTARIO','CONTABILIDAD','FISCAL','ACTIVOS_FIJOS','FINANZAS','REPORTES')
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
