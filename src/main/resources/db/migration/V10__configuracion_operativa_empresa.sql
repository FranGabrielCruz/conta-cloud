ALTER TABLE datos_empresa
    ADD COLUMN razon_social VARCHAR(150);

INSERT INTO datos_empresa (empresa_id, nombre_comercial, razon_social)
SELECT e.id, e.nombre, e.nombre
  FROM empresas e
 WHERE NOT EXISTS (SELECT 1 FROM datos_empresa d WHERE d.empresa_id = e.id);

UPDATE datos_empresa d
   SET nombre_comercial = COALESCE(NULLIF(TRIM(d.nombre_comercial), ''), e.nombre),
       razon_social = COALESCE(NULLIF(TRIM(d.razon_social), ''), e.nombre)
  FROM empresas e
 WHERE e.id = d.empresa_id;

CREATE TABLE catalogo_monedas (
    codigo_iso VARCHAR(3) PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    simbolo VARCHAR(10) NOT NULL,
    decimales SMALLINT NOT NULL DEFAULT 2 CHECK (decimales BETWEEN 0 AND 6),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO catalogo_monedas(codigo_iso,nombre,simbolo,decimales) VALUES
('DOP','Peso dominicano','RD$',2),
('USD','Dólar estadounidense','US$',2),
('EUR','Euro','€',2),
('GBP','Libra esterlina','£',2),
('CAD','Dólar canadiense','CA$',2);

INSERT INTO monedas(empresa_id,codigo_iso,nombre,simbolo,decimales,moneda_base,activo)
SELECT e.id,'DOP','Peso dominicano','RD$',2,TRUE,TRUE
  FROM empresas e
 WHERE NOT EXISTS (SELECT 1 FROM monedas m WHERE m.empresa_id=e.id);

INSERT INTO sucursales(empresa_id,codigo,nombre,principal,activo)
SELECT e.id,'PRINCIPAL','Sucursal Principal',TRUE,TRUE
  FROM empresas e
 WHERE NOT EXISTS (SELECT 1 FROM sucursales s WHERE s.empresa_id=e.id);

INSERT INTO permisos(codigo,nombre) VALUES
('SUCURSAL_DESACTIVAR','Desactivar sucursales'),
('MONEDA_DESACTIVAR','Desactivar monedas')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
  FROM roles r
  JOIN permisos p ON p.codigo IN ('SUCURSAL_DESACTIVAR','MONEDA_DESACTIVAR')
 WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT (rol_id,permiso_id) DO NOTHING;
