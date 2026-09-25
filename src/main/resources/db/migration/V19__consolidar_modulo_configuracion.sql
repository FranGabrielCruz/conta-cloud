INSERT INTO module_catalog(module_key,name,description,active,implemented,core,display_order)
VALUES ('CONFIGURACION','Configuración','Datos de empresa, sucursales y monedas',TRUE,TRUE,TRUE,10)
ON CONFLICT(module_key) DO UPDATE SET
    name=EXCLUDED.name,
    description=EXCLUDED.description,
    active=TRUE,
    implemented=TRUE,
    core=TRUE,
    display_order=EXCLUDED.display_order;

UPDATE module_catalog
   SET active=FALSE,
       implemented=FALSE,
       core=FALSE
 WHERE module_key IN ('EMPRESA','SUCURSALES','MONEDAS');

UPDATE empresa_modulos
   SET enabled=FALSE,
       updated_at=CURRENT_TIMESTAMP
 WHERE module_key IN ('EMPRESA','SUCURSALES','MONEDAS');

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT tenant_id,id,'CONFIGURACION',TRUE
  FROM empresas
ON CONFLICT(empresa_id,module_key) DO UPDATE SET
    enabled=TRUE,
    updated_at=CURRENT_TIMESTAMP;
