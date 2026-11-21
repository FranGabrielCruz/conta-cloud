ALTER TABLE periodos_fiscales ADD CONSTRAINT uk_periodo_fiscal_ambito UNIQUE (tenant_id,empresa_id,id);

CREATE TABLE cierre_contable (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    periodo_id UUID NOT NULL,
    solicitud UUID NOT NULL,
    cerrado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    cerrado_por UUID NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT true,
    asiento_id UUID,
    resumen JSONB NOT NULL,
    UNIQUE (tenant_id,empresa_id,id),
    UNIQUE (tenant_id,empresa_id,solicitud),
    FOREIGN KEY (tenant_id,empresa_id,periodo_id) REFERENCES periodos_fiscales(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,cerrado_por) REFERENCES usuarios(empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,asiento_id) REFERENCES asiento_contable(tenant_id,empresa_id,id)
);
CREATE UNIQUE INDEX uk_cierre_contable_activo ON cierre_contable(tenant_id,empresa_id,periodo_id) WHERE activo;
CREATE INDEX idx_cierre_contable_periodo ON cierre_contable(tenant_id,empresa_id,periodo_id,cerrado_en DESC);

CREATE TABLE reapertura_contable (
    cierre_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    solicitud UUID NOT NULL,
    motivo VARCHAR(1000) NOT NULL CHECK (btrim(motivo)<>''),
    usuario_id UUID NOT NULL,
    usuario VARCHAR(250) NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    trace_id VARCHAR(100) NOT NULL,
    reversa_id UUID,
    UNIQUE (tenant_id,empresa_id,solicitud),
    FOREIGN KEY (tenant_id,empresa_id,cierre_id) REFERENCES cierre_contable(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,usuario_id) REFERENCES usuarios(empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,reversa_id) REFERENCES asiento_contable(tenant_id,empresa_id,id)
);

CREATE FUNCTION proteger_periodo_con_cierre() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.estado = 'CERRADO' AND NEW.estado <> 'CERRADO' AND EXISTS (
        SELECT 1 FROM cierre_contable c WHERE (c.tenant_id,c.empresa_id,c.periodo_id)=(OLD.tenant_id,OLD.empresa_id,OLD.id)
            AND c.activo
    ) THEN
        RAISE EXCEPTION 'El cierre contable requiere una reapertura controlada.';
    END IF;
    IF (OLD.fecha_inicial,OLD.fecha_final) IS DISTINCT FROM (NEW.fecha_inicial,NEW.fecha_final) AND EXISTS (
        SELECT 1 FROM cierre_contable c WHERE (c.tenant_id,c.empresa_id,c.periodo_id)=(OLD.tenant_id,OLD.empresa_id,OLD.id)
    ) THEN
        RAISE EXCEPTION 'Las fechas del período con historial de cierre son inmutables.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_proteger_periodo_con_cierre BEFORE UPDATE ON periodos_fiscales
FOR EACH ROW EXECUTE FUNCTION proteger_periodo_con_cierre();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
SELECT 'cierres_contables.'||accion,nombre,nombre,'CONTABILIDAD','cierres_contables',accion FROM
(VALUES ('ejecutar','Ejecutar cierre contable'),('reabrir','Reabrir cierre contable'),('pdf','Ver PDF del cierre contable')) p(accion,nombre)
ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='cierres_contables'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
