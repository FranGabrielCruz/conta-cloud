CREATE TABLE asiento_secuencia (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    ultimo BIGINT NOT NULL CHECK (ultimo>0),
    PRIMARY KEY (tenant_id,empresa_id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE asiento_contable (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    numero VARCHAR(30) NOT NULL,
    fecha DATE NOT NULL,
    tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('DIARIO','APERTURA','AJUSTE','CIERRE','REVERSION','AUTOMATICO')),
    origen VARCHAR(16) NOT NULL CHECK (origen IN ('MANUAL','VENTAS','COMPRAS','INVENTARIO','CXC','CXP','ACTIVOS_FIJOS','FINANZAS','SISTEMA')),
    moneda_id UUID NOT NULL,
    moneda VARCHAR(3) NOT NULL,
    concepto VARCHAR(500) NOT NULL CHECK (btrim(concepto)<>''),
    referencia VARCHAR(150),
    observacion VARCHAR(2000),
    estado VARCHAR(16) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR','CONTABILIZADO','REVERSADO')),
    fuente_tipo VARCHAR(80),
    fuente_id UUID,
    fuente_numero VARCHAR(120),
    fuente_evento VARCHAR(80),
    reversa_de UUID,
    motivo VARCHAR(1000),
    solicitud UUID NOT NULL,
    contabilizacion UUID,
    version BIGINT NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    creado_por UUID NOT NULL,
    creador VARCHAR(250) NOT NULL,
    contabilizado_en TIMESTAMPTZ,
    contabilizado_por UUID,
    contabilizador VARCHAR(250),
    emisor JSONB,
    UNIQUE (tenant_id,empresa_id,id),
    UNIQUE (tenant_id,empresa_id,numero),
    UNIQUE (tenant_id,empresa_id,solicitud),
    UNIQUE (tenant_id,empresa_id,reversa_de),
    UNIQUE (tenant_id,empresa_id,origen,fuente_tipo,fuente_id,fuente_evento),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    FOREIGN KEY (empresa_id,moneda_id) REFERENCES monedas(empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,reversa_de) REFERENCES asiento_contable(tenant_id,empresa_id,id),
    FOREIGN KEY (creado_por) REFERENCES usuarios(id),
    FOREIGN KEY (contabilizado_por) REFERENCES usuarios(id),
    CHECK ((tipo='REVERSION')=(reversa_de IS NOT NULL AND motivo IS NOT NULL)),
    CHECK ((tipo='AUTOMATICO')=(fuente_tipo IS NOT NULL AND fuente_id IS NOT NULL AND fuente_numero IS NOT NULL AND fuente_evento IS NOT NULL)),
    CHECK (tipo<>'AUTOMATICO' OR origen<>'MANUAL'),
    CHECK ((estado<>'BORRADOR')=(contabilizacion IS NOT NULL AND contabilizado_en IS NOT NULL
        AND contabilizado_por IS NOT NULL AND contabilizador IS NOT NULL AND emisor IS NOT NULL))
);
CREATE INDEX idx_asiento_fecha ON asiento_contable(tenant_id,empresa_id,fecha DESC,numero DESC);
CREATE INDEX idx_asiento_estado_fecha ON asiento_contable(tenant_id,empresa_id,estado,fecha);

CREATE TABLE asiento_contable_linea (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    asiento_id UUID NOT NULL,
    orden INTEGER NOT NULL CHECK (orden>0),
    cuenta_id UUID NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    cuenta VARCHAR(160) NOT NULL,
    descripcion VARCHAR(500),
    debito NUMERIC(19,2) NOT NULL CHECK (debito>=0),
    credito NUMERIC(19,2) NOT NULL CHECK (credito>=0),
    CHECK (debito=0 OR credito=0),
    PRIMARY KEY (tenant_id,empresa_id,asiento_id,orden),
    FOREIGN KEY (tenant_id,empresa_id,asiento_id) REFERENCES asiento_contable(tenant_id,empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,cuenta_id) REFERENCES cuenta_contable(tenant_id,empresa_id,id)
);
CREATE INDEX idx_asiento_linea_cuenta ON asiento_contable_linea(tenant_id,empresa_id,cuenta_id,asiento_id);

CREATE VIEW asiento_movimiento_contable AS
SELECT l.*,a.numero,a.fecha,a.moneda_id,a.moneda,a.tipo,a.origen,a.estado
FROM asiento_contable_linea l JOIN asiento_contable a
ON (a.tenant_id,a.empresa_id,a.id)=(l.tenant_id,l.empresa_id,l.asiento_id)
WHERE a.estado IN ('CONTABILIZADO','REVERSADO');

CREATE FUNCTION proteger_asiento_contable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.estado<>'BORRADOR' THEN RAISE EXCEPTION 'El asiento debe iniciarse como borrador.'; END IF;
        RETURN NEW;
    END IF;
    IF OLD.estado<>'BORRADOR' THEN
        IF TG_OP='UPDATE' AND OLD.estado='CONTABILIZADO' AND NEW.estado='REVERSADO'
            AND (to_jsonb(OLD)-'estado'-'version')=(to_jsonb(NEW)-'estado'-'version')
            AND EXISTS (SELECT 1 FROM asiento_contable r
                WHERE (r.tenant_id,r.empresa_id,r.reversa_de)=(OLD.tenant_id,OLD.empresa_id,OLD.id)
                  AND r.estado='CONTABILIZADO') THEN
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'El asiento contabilizado es inmutable.';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF NEW.estado='CONTABILIZADO' AND NOT EXISTS (
        SELECT 1 FROM asiento_contable_linea l
        WHERE (l.tenant_id,l.empresa_id,l.asiento_id)=(NEW.tenant_id,NEW.empresa_id,NEW.id)
        GROUP BY l.asiento_id
        HAVING count(*)>=2 AND sum(l.debito)=sum(l.credito) AND bool_and(l.debito+l.credito>0)
    ) THEN
        RAISE EXCEPTION 'El asiento debe cumplir partida doble.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_asiento_inmutable BEFORE INSERT OR UPDATE OR DELETE ON asiento_contable
FOR EACH ROW EXECUTE FUNCTION proteger_asiento_contable();

CREATE FUNCTION proteger_asiento_linea() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    asunto asiento_contable_linea%ROWTYPE;
    situacion VARCHAR(16);
BEGIN
    IF TG_OP='UPDATE' AND (OLD.tenant_id,OLD.empresa_id,OLD.asiento_id) IS DISTINCT FROM
        (NEW.tenant_id,NEW.empresa_id,NEW.asiento_id) THEN RAISE EXCEPTION 'El movimiento pertenece a otro asiento.'; END IF;
    IF TG_OP='DELETE' THEN asunto := OLD; ELSE asunto := NEW; END IF;
    SELECT a.estado INTO situacion FROM asiento_contable a
    WHERE (a.tenant_id,a.empresa_id,a.id)=(asunto.tenant_id,asunto.empresa_id,asunto.asiento_id) FOR UPDATE;
    IF situacion<>'BORRADOR' THEN RAISE EXCEPTION 'Los movimientos contabilizados son inmutables.'; END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_asiento_linea_inmutable BEFORE INSERT OR UPDATE OR DELETE ON asiento_contable_linea
FOR EACH ROW EXECUTE FUNCTION proteger_asiento_linea();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
SELECT 'asientos.'||accion,nombre,nombre,'CONTABILIDAD','asientos',accion FROM
(VALUES ('crear','Crear asiento'),('editar','Editar borrador de asiento'),('contabilizar','Contabilizar asiento'),
    ('reversar','Reversar asiento'),('pdf','Ver PDF del asiento')) p(accion,nombre)
ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='asientos'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
