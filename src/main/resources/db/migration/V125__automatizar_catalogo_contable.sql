ALTER TABLE cuenta_contable
    ALTER COLUMN creado_por DROP NOT NULL,
    ADD COLUMN estructural BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_cuenta_contable_estructural CHECK (NOT estructural OR (padre_id IS NULL AND tipo='AGRUPADORA' AND activo));

CREATE TABLE cuenta_contable_plantilla (
    version INTEGER PRIMARY KEY,
    anchos SMALLINT[] NOT NULL
);
INSERT INTO cuenta_contable_plantilla VALUES (1,ARRAY[1,1,2,2]::smallint[]);

CREATE TABLE cuenta_contable_plantilla_item (
    version INTEGER NOT NULL REFERENCES cuenta_contable_plantilla(version),
    codigo VARCHAR(40) NOT NULL,
    padre_codigo VARCHAR(40),
    nombre VARCHAR(160) NOT NULL,
    tipo VARCHAR(16) NOT NULL,
    clasificacion VARCHAR(16) NOT NULL,
    naturaleza VARCHAR(12) NOT NULL,
    PRIMARY KEY (version,codigo),
    FOREIGN KEY (version,padre_codigo) REFERENCES cuenta_contable_plantilla_item(version,codigo)
);
INSERT INTO cuenta_contable_plantilla_item VALUES
    (1,'1',NULL,'ACTIVOS','AGRUPADORA','ACTIVO','DEUDORA'),
    (1,'1.1','1','ACTIVOS CORRIENTES','AGRUPADORA','ACTIVO','DEUDORA'),
    (1,'1.1.01','1.1','EFECTIVO Y EQUIVALENTES','AGRUPADORA','ACTIVO','DEUDORA'),
    (1,'1.1.01.01','1.1.01','Caja General','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.1.01.02','1.1.01','Bancos','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.1.02','1.1','CUENTAS POR COBRAR','AGRUPADORA','ACTIVO','DEUDORA'),
    (1,'1.1.02.01','1.1.02','Cuentas por cobrar clientes','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.1.03','1.1','INVENTARIOS','AGRUPADORA','ACTIVO','DEUDORA'),
    (1,'1.1.03.01','1.1.03','Inventario de mercancías','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.2','1','ACTIVOS NO CORRIENTES','AGRUPADORA','ACTIVO','DEUDORA'),
    (1,'1.2.01','1.2','PROPIEDAD, PLANTA Y EQUIPO','AGRUPADORA','ACTIVO','DEUDORA'),
    (1,'1.2.01.01','1.2.01','Terrenos','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.2.01.02','1.2.01','Edificios','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.2.01.03','1.2.01','Vehículos','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.2.01.04','1.2.01','Mobiliario y equipos','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.2.01.05','1.2.01','Equipos de cómputo','MOVIMIENTO','ACTIVO','DEUDORA'),
    (1,'1.2.02','1.2','DEPRECIACIÓN ACUMULADA','AGRUPADORA','ACTIVO','ACREEDORA'),
    (1,'1.2.02.01','1.2.02','Depreciación acumulada edificios','MOVIMIENTO','ACTIVO','ACREEDORA'),
    (1,'1.2.02.02','1.2.02','Depreciación acumulada vehículos','MOVIMIENTO','ACTIVO','ACREEDORA'),
    (1,'1.2.02.03','1.2.02','Depreciación acumulada mobiliario','MOVIMIENTO','ACTIVO','ACREEDORA'),
    (1,'1.2.02.04','1.2.02','Depreciación acumulada equipos de cómputo','MOVIMIENTO','ACTIVO','ACREEDORA'),
    (1,'2',NULL,'PASIVOS','AGRUPADORA','PASIVO','ACREEDORA'),
    (1,'2.1','2','PASIVOS CORRIENTES','AGRUPADORA','PASIVO','ACREEDORA'),
    (1,'2.1.01','2.1','CUENTAS POR PAGAR','AGRUPADORA','PASIVO','ACREEDORA'),
    (1,'2.1.01.01','2.1.01','Cuentas por pagar proveedores','MOVIMIENTO','PASIVO','ACREEDORA'),
    (1,'2.1.02','2.1','IMPUESTOS POR PAGAR','AGRUPADORA','PASIVO','ACREEDORA'),
    (1,'2.1.03','2.1','OTRAS CUENTAS POR PAGAR','AGRUPADORA','PASIVO','ACREEDORA'),
    (1,'2.2','2','PASIVOS NO CORRIENTES','AGRUPADORA','PASIVO','ACREEDORA'),
    (1,'3',NULL,'PATRIMONIO','AGRUPADORA','PATRIMONIO','ACREEDORA'),
    (1,'3.1','3','Capital','MOVIMIENTO','PATRIMONIO','ACREEDORA'),
    (1,'3.2','3','Resultados acumulados','MOVIMIENTO','PATRIMONIO','ACREEDORA'),
    (1,'3.3','3','Resultado del ejercicio','MOVIMIENTO','PATRIMONIO','ACREEDORA'),
    (1,'4',NULL,'INGRESOS','AGRUPADORA','INGRESOS','ACREEDORA'),
    (1,'4.1','4','INGRESOS OPERACIONALES','AGRUPADORA','INGRESOS','ACREEDORA'),
    (1,'4.1.01','4.1','VENTAS','AGRUPADORA','INGRESOS','ACREEDORA'),
    (1,'4.1.01.01','4.1.01','Ventas','MOVIMIENTO','INGRESOS','ACREEDORA'),
    (1,'4.2','4','OTROS INGRESOS','AGRUPADORA','INGRESOS','ACREEDORA'),
    (1,'4.2.01','4.2','GANANCIA EN DISPOSICIÓN DE ACTIVOS','AGRUPADORA','INGRESOS','ACREEDORA'),
    (1,'4.2.01.01','4.2.01','Ganancia en disposición de activos','MOVIMIENTO','INGRESOS','ACREEDORA'),
    (1,'5',NULL,'COSTOS','AGRUPADORA','COSTOS','DEUDORA'),
    (1,'5.1','5','COSTO DE VENTAS','AGRUPADORA','COSTOS','DEUDORA'),
    (1,'5.1.01','5.1','COSTO DE MERCANCÍAS VENDIDAS','AGRUPADORA','COSTOS','DEUDORA'),
    (1,'5.1.01.01','5.1.01','Costo de mercancías vendidas','MOVIMIENTO','COSTOS','DEUDORA'),
    (1,'6',NULL,'GASTOS','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.1','6','GASTOS OPERACIONALES','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.1.01','6.1','SUELDOS Y SALARIOS','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.1.02','6.1','ALQUILERES','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.1.03','6.1','SERVICIOS','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.1.04','6.1','DEPRECIACIÓN','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.1.04.01','6.1.04','Gasto de depreciación','MOVIMIENTO','GASTOS','DEUDORA'),
    (1,'6.2','6','OTROS GASTOS','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.2.01','6.2','PÉRDIDA EN DISPOSICIÓN DE ACTIVOS','AGRUPADORA','GASTOS','DEUDORA'),
    (1,'6.2.01.01','6.2.01','Pérdida en disposición de activos','MOVIMIENTO','GASTOS','DEUDORA');

CREATE TABLE cuenta_contable_instalacion (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    version INTEGER NOT NULL REFERENCES cuenta_contable_plantilla(version),
    PRIMARY KEY (tenant_id,empresa_id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id) ON DELETE CASCADE
);

CREATE TABLE cuenta_contable_secuencia (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    padre_id UUID NOT NULL,
    ultimo NUMERIC(40,0) NOT NULL CHECK (ultimo>0),
    PRIMARY KEY (tenant_id,empresa_id,padre_id),
    FOREIGN KEY (tenant_id,empresa_id,padre_id) REFERENCES cuenta_contable(tenant_id,empresa_id,id) ON DELETE CASCADE
);

CREATE TABLE cuenta_contable_codigo_historico (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    PRIMARY KEY (tenant_id,empresa_id,codigo),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id) ON DELETE CASCADE
);
INSERT INTO cuenta_contable_codigo_historico(tenant_id,empresa_id,codigo)
SELECT e.tenant_id,e.id,a.detalle->'anterior'->>'codigo'
FROM auditoria a JOIN empresas e ON e.id=a.empresa_id
WHERE a.entidad='CuentaContable' AND a.detalle->'anterior'->>'codigo' ~ '^[0-9]+([.-][0-9]+)*$'
  AND char_length(a.detalle->'anterior'->>'codigo')<=40
ON CONFLICT DO NOTHING;

CREATE FUNCTION instalar_catalogo_contable(p_tenant UUID,p_empresa UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    version_base INTEGER;
BEGIN
    PERFORM 1 FROM empresas WHERE tenant_id=p_tenant AND id=p_empresa FOR NO KEY UPDATE;
    IF NOT FOUND OR EXISTS (SELECT 1 FROM cuenta_contable WHERE tenant_id=p_tenant AND empresa_id=p_empresa)
        OR EXISTS (SELECT 1 FROM cuenta_contable_instalacion WHERE tenant_id=p_tenant AND empresa_id=p_empresa) THEN
        RETURN;
    END IF;

    SELECT max(version) INTO STRICT version_base FROM cuenta_contable_plantilla;
    IF NOT EXISTS (SELECT 1 FROM cuenta_contable_plantilla_item WHERE version=version_base) THEN
        RAISE EXCEPTION 'La plantilla contable no contiene cuentas.';
    END IF;
    INSERT INTO cuenta_contable_instalacion VALUES (p_tenant,p_empresa,version_base);
    WITH items AS MATERIALIZED (
        SELECT i.*,gen_random_uuid() id FROM cuenta_contable_plantilla_item i WHERE i.version=version_base
    )
    INSERT INTO cuenta_contable(id,tenant_id,empresa_id,padre_id,codigo,nombre,tipo,clasificacion,naturaleza,solicitud_id,estructural)
    SELECT i.id,p_tenant,p_empresa,p.id,i.codigo,i.nombre,i.tipo,i.clasificacion,i.naturaleza,gen_random_uuid(),i.padre_codigo IS NULL
    FROM items i LEFT JOIN items p ON p.codigo=i.padre_codigo;

    INSERT INTO auditoria(empresa_id,accion,entidad,registro_id,detalle)
    VALUES (p_empresa,'INSTALAR_CATALOGO_BASE','Empresa',p_empresa,jsonb_build_object('version',version_base));
END;
$$;

CREATE FUNCTION instalar_catalogo_contable_empresa_nueva() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM instalar_catalogo_contable(NEW.tenant_id,NEW.id);
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_empresa_catalogo_contable AFTER INSERT ON empresas
FOR EACH ROW EXECUTE FUNCTION instalar_catalogo_contable_empresa_nueva();

SELECT instalar_catalogo_contable(tenant_id,id) FROM empresas;
