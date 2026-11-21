package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class ConfiguracionMotorRepository {
 private final NamedParameterJdbcTemplate jdbc;
 public ConfiguracionMotorRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
 private MapSqlParameterSource args(TenantPrincipal p) {
  return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId());
 }
 private static final String SCOPE="tenant_id=:tenant AND empresa_id=:empresa";
 public record General(long version,String metodo,boolean automatico) {}
 public boolean tieneConfiguracion(TenantPrincipal p) {
  return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM configuracion_contable WHERE "+SCOPE+")",args(p),Boolean.class));
 }
 public General general(TenantPrincipal p) {
  return jdbc.query("SELECT version,metodo_contable,contabilizacion_automatica FROM configuracion_contable WHERE "+SCOPE,
   args(p),(r,n)->new General(r.getLong(1),r.getString(2),r.getBoolean(3))).stream().findFirst()
   .orElseThrow(()->new IllegalStateException("No existe configuración contable."));
 }
 public boolean existe(TenantPrincipal p,String tabla) {
  if(!Set.of("cuenta_contable","periodos_fiscales").contains(tabla)) throw new IllegalArgumentException();
  return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+tabla+" WHERE "+SCOPE+")",args(p),Boolean.class));
 }
 private static Cuenta cuenta(java.sql.ResultSet r) throws java.sql.SQLException {
  return new Cuenta(r.getObject("id",UUID.class),r.getString("codigo"),r.getString("nombre"),r.getBoolean("activo"),
    r.getString("tipo"),r.getString("clasificacion"),r.getBoolean("permite_resultado_ejercicio"),r.getString("uso_centro_costo"));
 }
 public Map<String,Cuenta> cuentas(TenantPrincipal p) {
  Map<String,Cuenta> result=new HashMap<>();
  jdbc.query("SELECT k.clave,c.* FROM cuenta_contable_clave_empresa k JOIN cuenta_contable c ON "+
   "(c.tenant_id,c.empresa_id,c.id)=(k.tenant_id,k.empresa_id,k.cuenta_id) WHERE k.tenant_id=:tenant AND k.empresa_id=:empresa",
   args(p),r->{result.put(r.getString("clave"),cuenta(r));});
  return result;
 }
 public Optional<Cuenta> cuenta(TenantPrincipal p,UUID id) {
  return jdbc.query("SELECT * FROM cuenta_contable WHERE "+SCOPE+" AND id=:id",args(p).addValue("id",id),
   (r,n)->cuenta(r)).stream().findFirst();
 }
 public List<Cuenta> buscar(TenantPrincipal p,String texto,int offset,int limite) {
  return jdbc.query("SELECT * FROM cuenta_contable WHERE "+SCOPE+
   " AND activo AND tipo='MOVIMIENTO' AND (:q='' OR strpos(lower(codigo||' '||nombre),lower(:q))>0) ORDER BY codigo LIMIT :limite OFFSET :offset",
   args(p).addValue("q",texto).addValue("offset",offset).addValue("limite",limite),(r,n)->cuenta(r));
 }
 public Map<Modulo,Boolean> modulos(TenantPrincipal p) {
  Map<Modulo,Boolean> result=new EnumMap<>(Modulo.class);
  jdbc.query("SELECT modulo,automatico FROM configuracion_contable_modulo WHERE "+SCOPE,args(p),
   r->{result.put(Modulo.valueOf(r.getString(1)),r.getBoolean(2));});
  return result;
 }
 public boolean finanzasDisponible(TenantPrincipal p) {
  return Boolean.TRUE.equals(jdbc.queryForObject("""
   SELECT EXISTS (
    SELECT 1 FROM (
     SELECT cuenta_contable_id FROM cajas WHERE tenant_id=:tenant AND empresa_id=:empresa AND activo
     UNION ALL
     SELECT cuenta_contable_id FROM cuentas_bancarias WHERE tenant_id=:tenant AND empresa_id=:empresa AND activo
    ) r JOIN cuenta_contable c ON c.id=r.cuenta_contable_id
    WHERE c.tenant_id=:tenant AND c.empresa_id=:empresa AND c.activo AND c.tipo='MOVIMIENTO' AND c.clasificacion='ACTIVO'
   )
   """,args(p),Boolean.class));
 }
 public List<Cuenta> buscarCompatible(TenantPrincipal p,Funcion f,String texto,int offset,int limite) {
  return jdbc.query("SELECT * FROM cuenta_contable WHERE "+SCOPE+
   " AND activo AND tipo='MOVIMIENTO' AND clasificacion IN (:clases)"+
   (f.clave().equals("FISCAL_RESULT_DEFAULT")?" AND permite_resultado_ejercicio AND uso_centro_costo<>'OBLIGATORIO'":"")+
   " AND (:q='' OR strpos(lower(codigo||' '||nombre),lower(:q))>0) ORDER BY codigo LIMIT :limite OFFSET :offset",
   args(p).addValue("clases",f.clasificaciones()).addValue("q",texto).addValue("offset",offset).addValue("limite",limite),
   (r,n)->cuenta(r));
 }
 public void guardar(TenantPrincipal p,Entrada e) {
  int updated=jdbc.update("UPDATE configuracion_contable SET metodo_contable=:metodo,contabilizacion_automatica=:auto,version=version+1 WHERE "+
   SCOPE+" AND version=:version",args(p).addValue("metodo",e.metodo()).addValue("auto",e.automatico()).addValue("version",e.version()));
  if(updated!=1) throw new org.springframework.dao.OptimisticLockingFailureException("La configuración cambió. Recarga la pantalla.");
  e.cuentas().forEach((clave,id)->{
   var a=args(p).addValue("clave",clave).addValue("cuenta",id);
   if(id==null) jdbc.update("DELETE FROM cuenta_contable_clave_empresa WHERE "+SCOPE+" AND clave=:clave",a);
   else jdbc.update("""
    INSERT INTO cuenta_contable_clave_empresa(tenant_id,empresa_id,clave,cuenta_id) VALUES (:tenant,:empresa,:clave,:cuenta)
    ON CONFLICT(tenant_id,empresa_id,clave) DO UPDATE SET cuenta_id=excluded.cuenta_id
    """,a);
  });
  e.modulos().forEach((m,on)->jdbc.update("""
   INSERT INTO configuracion_contable_modulo(tenant_id,empresa_id,modulo,automatico) VALUES (:tenant,:empresa,:modulo,:on)
   ON CONFLICT(tenant_id,empresa_id,modulo) DO UPDATE SET automatico=excluded.automatico
   """,args(p).addValue("modulo",m.name()).addValue("on",on)));
 }
}

