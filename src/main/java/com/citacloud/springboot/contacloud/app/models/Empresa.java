package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "empresas")
public class Empresa {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId = UUID.randomUUID();
    @Column(name = "logo_object_key", length = 512)
    private String logoObjectKey;
    @Column(nullable = false, length = 30, unique = true)
    private String codigo;
    @Column(nullable = false, length = 150)
    private String nombre;
    @Column(name = "identificacion_fiscal", length = 30)
    private String identificacionFiscal;
    @Column(name = "pais_codigo", length = 2)
    private String paisCodigo;
    @Column(name = "zona_horaria", nullable = false, length = 60)
    private String zonaHoraria = "America/Santo_Domingo";
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "limite_usuarios_habilitado", nullable = false)
    @ColumnDefault("false")
    private boolean limiteUsuariosHabilitado;
    @Column(name = "limite_usuarios")
    private Integer limiteUsuarios;
    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    protected Empresa() {}
    public Empresa(String codigo, String nombre) { this.codigo = codigo; this.nombre = nombre; }
    public Empresa(UUID tenantId,String codigo,String nombre){this.tenantId=tenantId;this.codigo=codigo;this.nombre=nombre;}
    @PreUpdate void antesDeActualizar() { actualizadoEn = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getLogoObjectKey() { return logoObjectKey; }
    public void setLogoObjectKey(String logoObjectKey) { this.logoObjectKey = logoObjectKey; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getIdentificacionFiscal() { return identificacionFiscal; }
    public void setIdentificacionFiscal(String valor) { identificacionFiscal = valor; }
    public String getPaisCodigo() { return paisCodigo; }
    public void setPaisCodigo(String valor) { paisCodigo = valor; }
    public String getZonaHoraria() { return zonaHoraria; }
    public void setZonaHoraria(String valor) { zonaHoraria = valor; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public boolean isLimiteUsuariosHabilitado() { return limiteUsuariosHabilitado; }
    public void setLimiteUsuariosHabilitado(boolean valor) { limiteUsuariosHabilitado = valor; }
    public Integer getLimiteUsuarios() { return limiteUsuarios; }
    public void setLimiteUsuarios(Integer valor) { limiteUsuarios = valor; }
}
