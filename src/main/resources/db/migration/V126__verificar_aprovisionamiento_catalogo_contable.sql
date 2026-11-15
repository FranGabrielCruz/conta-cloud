ALTER TABLE cuenta_contable_plantilla ADD COLUMN cantidad_esperada INTEGER;
UPDATE cuenta_contable_plantilla SET cantidad_esperada=53 WHERE version=1;
ALTER TABLE cuenta_contable_plantilla
    ALTER COLUMN cantidad_esperada SET NOT NULL,
    ADD CONSTRAINT ck_plantilla_cantidad CHECK (cantidad_esperada>=6);

ALTER TABLE cuenta_contable_instalacion ADD COLUMN aprovisionado_en TIMESTAMPTZ;
UPDATE cuenta_contable_instalacion i SET aprovisionado_en=(
    SELECT min(a.fecha_hora) FROM auditoria a
    WHERE a.empresa_id=i.empresa_id AND a.accion='INSTALAR_CATALOGO_BASE'
);
ALTER TABLE cuenta_contable_instalacion ALTER COLUMN aprovisionado_en SET DEFAULT current_timestamp;

CREATE OR REPLACE FUNCTION instalar_catalogo_contable(p_tenant UUID,p_empresa UUID) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    version_base INTEGER;
    esperadas INTEGER;
    creadas INTEGER;
    trace_id UUID := gen_random_uuid();
BEGIN
    PERFORM 1 FROM empresas WHERE tenant_id=p_tenant AND id=p_empresa FOR NO KEY UPDATE;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT version INTO version_base FROM cuenta_contable_instalacion
    WHERE tenant_id=p_tenant AND empresa_id=p_empresa;
    IF EXISTS (SELECT 1 FROM cuenta_contable WHERE tenant_id=p_tenant AND empresa_id=p_empresa) THEN
        IF version_base IS NOT NULL THEN
            RAISE LOG '%',jsonb_build_object('event','ProvisioningSkippedAlreadyCompleted',
                'tenantId',p_tenant,'companyId',p_empresa,'templateVersion',version_base,'traceId',trace_id);
        END IF;
        RETURN;
    END IF;

    SELECT version,cantidad_esperada INTO STRICT version_base,esperadas
    FROM cuenta_contable_plantilla
    WHERE version=COALESCE(version_base,(SELECT max(version) FROM cuenta_contable_plantilla));

    RAISE LOG '%',jsonb_build_object('event','ProvisioningStarted',
        'tenantId',p_tenant,'companyId',p_empresa,'templateVersion',version_base,'traceId',trace_id);

    IF (WITH RECURSIVE alcanzables(codigo) AS (
            SELECT codigo FROM cuenta_contable_plantilla_item WHERE version=version_base AND padre_codigo IS NULL
            UNION
            SELECT i.codigo FROM cuenta_contable_plantilla_item i JOIN alcanzables a ON a.codigo=i.padre_codigo
            WHERE i.version=version_base
        ) SELECT count(*) FROM alcanzables) <> esperadas
        OR (SELECT count(*) FROM cuenta_contable_plantilla_item WHERE version=version_base) <> esperadas
        OR (SELECT count(*) FROM cuenta_contable_plantilla_item WHERE version=version_base AND padre_codigo IS NULL) <> 6
        OR EXISTS (
            SELECT 1 FROM cuenta_contable_plantilla_item i JOIN cuenta_contable_plantilla_item p
                ON p.version=i.version AND p.codigo=i.padre_codigo
            WHERE i.version=version_base AND (p.tipo<>'AGRUPADORA' OR p.clasificacion<>i.clasificacion)
        )
    THEN
        RAISE EXCEPTION 'La estructura de la plantilla contable es inválida.';
    END IF;

    WITH items AS MATERIALIZED (
        SELECT i.*,gen_random_uuid() id FROM cuenta_contable_plantilla_item i WHERE i.version=version_base
    )
    INSERT INTO cuenta_contable(id,tenant_id,empresa_id,padre_id,codigo,nombre,tipo,clasificacion,naturaleza,solicitud_id,estructural)
    SELECT i.id,p_tenant,p_empresa,p.id,i.codigo,i.nombre,i.tipo,i.clasificacion,i.naturaleza,gen_random_uuid(),i.padre_codigo IS NULL
    FROM items i LEFT JOIN items p ON p.codigo=i.padre_codigo;
    GET DIAGNOSTICS creadas=ROW_COUNT;

    IF creadas<>esperadas OR EXISTS (
        SELECT 1 FROM cuenta_contable_plantilla_item i
        LEFT JOIN cuenta_contable c ON c.tenant_id=p_tenant AND c.empresa_id=p_empresa AND c.codigo=i.codigo
        LEFT JOIN cuenta_contable p ON p.tenant_id=c.tenant_id AND p.empresa_id=c.empresa_id AND p.id=c.padre_id
        WHERE i.version=version_base AND (c.id IS NULL OR p.codigo IS DISTINCT FROM i.padre_codigo
            OR (c.nombre,c.tipo,c.clasificacion,c.naturaleza,c.activo,c.estructural)
            IS DISTINCT FROM (i.nombre,i.tipo,i.clasificacion,i.naturaleza,true,i.padre_codigo IS NULL))
    ) THEN
        RAISE EXCEPTION 'El catálogo contable instalado no coincide con la plantilla.';
    END IF;

    INSERT INTO cuenta_contable_instalacion(tenant_id,empresa_id,version,aprovisionado_en)
    VALUES (p_tenant,p_empresa,version_base,current_timestamp)
    ON CONFLICT(tenant_id,empresa_id) DO UPDATE
    SET version=EXCLUDED.version,aprovisionado_en=EXCLUDED.aprovisionado_en;

    INSERT INTO auditoria(empresa_id,accion,entidad,registro_id,detalle)
    VALUES (p_empresa,'INSTALAR_CATALOGO_BASE','Empresa',p_empresa,
        jsonb_build_object('version',version_base,'cantidad',creadas,'traceId',trace_id));
    RAISE LOG '%',jsonb_build_object('event','ProvisioningCompleted','tenantId',p_tenant,
        'companyId',p_empresa,'templateVersion',version_base,'accounts',creadas,'traceId',trace_id);
EXCEPTION WHEN OTHERS THEN
    RAISE LOG '%',jsonb_build_object('event','ProvisioningFailed','tenantId',p_tenant,
        'companyId',p_empresa,'templateVersion',version_base,'traceId',trace_id,'sqlstate',SQLSTATE);
    RAISE;
END;
$$;

DO $$
DECLARE
    empresa RECORD;
BEGIN
    FOR empresa IN
        SELECT e.tenant_id,e.id FROM empresas e
        WHERE NOT EXISTS (SELECT 1 FROM cuenta_contable c WHERE c.tenant_id=e.tenant_id AND c.empresa_id=e.id)
        ORDER BY e.tenant_id,e.id
    LOOP
        PERFORM instalar_catalogo_contable(empresa.tenant_id,empresa.id);
    END LOOP;
END;
$$;
