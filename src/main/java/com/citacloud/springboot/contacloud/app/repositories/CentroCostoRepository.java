package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CentroCostoRepository {
    private static final String AMBITO="c.tenant_id=:tenant AND c.empresa_id=:empresa";
    private static final String FILTRO="""
         AND (CAST(:activo AS boolean) IS NULL OR c.activo=:activo)
         AND (:buscar='' OR strpos(lower(c.codigo || ' ' || c.nombre),lower(:buscar))>0)
        """;
    private static final String CAMPOS="""
        SELECT c.*,p.id AS padre_ref,p.codigo AS padre_codigo,p.nombre AS padre_nombre,p.activo AS padre_activo
        FROM centro_costo c LEFT JOIN centro_costo p ON (p.tenant_id,p.empresa_id,p.id)=(c.tenant_id,c.empresa_id,c.padre_id)
        WHERE
        """;
    private final NamedParameterJdbcTemplate jdbc;
    public CentroCostoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Resultado listar(TenantPrincipal p,Filtro f,long offset,int limite) {
        var args=filtro(p,f);
        long total=jdbc.queryForObject("SELECT count(*) FROM centro_costo c WHERE "+AMBITO+FILTRO,args,Long.class);
        var filas=total==0?List.<Centro>of():jdbc.query(CAMPOS+AMBITO+FILTRO+" ORDER BY c.numero LIMIT :limite OFFSET :offset",
            args.addValue("limite",limite).addValue("offset",offset),CentroCostoRepository::centro);
        return new Resultado(filas,total);
    }

    public List<Referencia> opciones(TenantPrincipal p,Filtro f,int offset,int limite) {
        return jdbc.query("SELECT c.id,c.codigo,c.nombre,c.activo FROM centro_costo c WHERE "+AMBITO+FILTRO
            +" ORDER BY c.numero LIMIT :limite OFFSET :offset",filtro(p,f).addValue("limite",limite).addValue("offset",offset),
            (r,n)->referencia(r,"id",""));
    }

    public Optional<Centro> detalle(TenantPrincipal p,UUID id) {
        return jdbc.query(CAMPOS+AMBITO+" AND c.id=:id",ambito(p).addValue("id",id),CentroCostoRepository::centro)
            .stream().findFirst();
    }

    public Optional<UUID> solicitud(TenantPrincipal p,UUID clave) {
        return jdbc.query("SELECT c.id FROM centro_costo c WHERE "+AMBITO+" AND c.solicitud=:clave",
            ambito(p).addValue("clave",clave),(r,n)->r.getObject(1,UUID.class)).stream().findFirst();
    }

    public boolean ciclo(TenantPrincipal p,UUID id,UUID padre) {
        return jdbc.queryForObject("""
            WITH RECURSIVE ancestros(id,padre_id) AS (
                SELECT c.id,c.padre_id FROM centro_costo c WHERE %s AND c.id=:padre
                UNION
                SELECT c.id,c.padre_id FROM centro_costo c JOIN ancestros a ON c.id=a.padre_id WHERE %s
            )
            SELECT EXISTS (SELECT 1 FROM ancestros WHERE id=:id)
            """.formatted(AMBITO,AMBITO),ambito(p).addValue("id",id).addValue("padre",padre),Boolean.class);
    }

    public long siguiente(TenantPrincipal p) {
        return jdbc.queryForObject("""
            INSERT INTO centro_costo_secuencia(tenant_id,empresa_id,ultimo) VALUES (:tenant,:empresa,1)
            ON CONFLICT (tenant_id,empresa_id) DO UPDATE SET ultimo=centro_costo_secuencia.ultimo+1
            RETURNING ultimo
            """,ambito(p),Long.class);
    }

    public void crear(TenantPrincipal p,UUID id,UUID clave,Entrada e,long numero,String codigo) {
        jdbc.update("""
            INSERT INTO centro_costo(id,tenant_id,empresa_id,padre_id,numero,codigo,nombre,descripcion,solicitud,creado_por)
            VALUES(:id,:tenant,:empresa,:padre,:numero,:codigo,:nombre,:descripcion,:clave,:usuario)
            """,datos(p,id,e).addValue("numero",numero).addValue("codigo",codigo).addValue("clave",clave));
    }

    public void editar(TenantPrincipal p,UUID id,Entrada e) {
        jdbc.update("""
            UPDATE centro_costo c SET padre_id=:padre,nombre=:nombre,descripcion=:descripcion,
                actualizado_en=current_timestamp,actualizado_por=:usuario,version=version+1
            WHERE c.id=:id AND
            """+AMBITO,datos(p,id,e));
    }

    public void estado(TenantPrincipal p,UUID id,boolean activo) {
        jdbc.update("""
            UPDATE centro_costo c SET activo=:activo,actualizado_en=current_timestamp,actualizado_por=:usuario,version=version+1
            WHERE c.id=:id AND
            """+AMBITO,ambito(p).addValue("id",id).addValue("activo",activo).addValue("usuario",p.usuarioId()));
    }

    private static Centro centro(ResultSet r,int n) throws SQLException {
        return new Centro(referencia(r,"id",""),referencia(r,"padre_ref","padre_"),r.getString("descripcion"),r.getLong("version"));
    }

    private static Referencia referencia(ResultSet r,String id,String prefijo) throws SQLException {
        UUID uuid=r.getObject(id,UUID.class);
        return uuid==null?null:new Referencia(uuid,r.getString(prefijo+"codigo"),r.getString(prefijo+"nombre"),r.getBoolean(prefijo+"activo"));
    }

    private static MapSqlParameterSource filtro(TenantPrincipal p,Filtro f) {
        return ambito(p).addValue("activo",f.activo()).addValue("buscar",f.buscar());
    }

    private static MapSqlParameterSource datos(TenantPrincipal p,UUID id,Entrada e) {
        return ambito(p).addValue("id",id).addValue("padre",e.padre()).addValue("nombre",e.nombre())
            .addValue("descripcion",e.descripcion()).addValue("usuario",p.usuarioId());
    }

    private static MapSqlParameterSource ambito(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId());
    }
}
