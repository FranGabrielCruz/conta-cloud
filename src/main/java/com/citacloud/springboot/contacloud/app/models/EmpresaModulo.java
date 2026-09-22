package com.citacloud.springboot.contacloud.app.models;
import jakarta.persistence.*;
import java.io.Serializable;import java.time.Instant;import java.util.*;
@Entity @Table(name="empresa_modulos") @IdClass(EmpresaModulo.Clave.class)
public class EmpresaModulo {
    @Id @Column(name="empresa_id") private UUID empresaId;
    @Id @Column(name="module_key",length=60) private String moduleKey;
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(nullable=false) private boolean enabled;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false,insertable=false,updatable=false) private Instant updatedAt;
    protected EmpresaModulo(){}
    public EmpresaModulo(UUID tenantId,UUID empresaId,String moduleKey,boolean enabled){this.tenantId=tenantId;this.empresaId=empresaId;this.moduleKey=moduleKey;this.enabled=enabled;}
    public UUID getEmpresaId(){return empresaId;} public String getModuleKey(){return moduleKey;} public UUID getTenantId(){return tenantId;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean value){enabled=value;}
    public static class Clave implements Serializable {public UUID empresaId;public String moduleKey;public Clave(){}public Clave(UUID e,String m){empresaId=e;moduleKey=m;}@Override public boolean equals(Object o){return o instanceof Clave c&&Objects.equals(empresaId,c.empresaId)&&Objects.equals(moduleKey,c.moduleKey);}@Override public int hashCode(){return Objects.hash(empresaId,moduleKey);}}
}
