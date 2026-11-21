ALTER TABLE cuenta_contable ADD COLUMN permite_resultado_ejercicio BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_cuenta_resultado_ejercicio CHECK (
        NOT permite_resultado_ejercicio OR (tipo='MOVIMIENTO' AND clasificacion='PATRIMONIO')
    );

ALTER TABLE cuenta_contable_plantilla_item ADD COLUMN permite_resultado_ejercicio BOOLEAN NOT NULL DEFAULT false;
UPDATE cuenta_contable_plantilla_item SET permite_resultado_ejercicio=true
WHERE version=1 AND tipo='MOVIMIENTO' AND clasificacion='PATRIMONIO' AND nombre='Resultado del ejercicio';

UPDATE cuenta_contable c SET permite_resultado_ejercicio=true
FROM cuenta_contable_instalacion i JOIN cuenta_contable_plantilla_item t ON t.version=i.version
WHERE (c.tenant_id,c.empresa_id)=(i.tenant_id,i.empresa_id)
    AND c.codigo=t.codigo AND c.creado_por IS NULL AND c.tipo='MOVIMIENTO'
    AND c.clasificacion='PATRIMONIO' AND t.permite_resultado_ejercicio;

CREATE FUNCTION instalar_funcion_resultado_ejercicio() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE cuenta_contable c SET permite_resultado_ejercicio=true
    FROM cuenta_contable_plantilla_item t
    WHERE c.tenant_id=NEW.tenant_id AND c.empresa_id=NEW.empresa_id AND t.version=NEW.version
      AND c.codigo=t.codigo AND c.creado_por IS NULL AND c.tipo='MOVIMIENTO'
      AND c.clasificacion='PATRIMONIO' AND t.permite_resultado_ejercicio;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_instalar_funcion_resultado_ejercicio AFTER INSERT ON cuenta_contable_instalacion
FOR EACH ROW EXECUTE FUNCTION instalar_funcion_resultado_ejercicio();
