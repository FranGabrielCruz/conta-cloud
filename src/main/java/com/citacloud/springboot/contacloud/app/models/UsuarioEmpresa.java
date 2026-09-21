package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "usuario_empresa")
public class UsuarioEmpresa {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "rol_id") private Rol rol;
    @Column(nullable = false) private boolean activo = true;
    @Column(name = "acceso_todas_sucursales", nullable = false) private boolean accesoTodasSucursales = true;
    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false) private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false) private Instant actualizadoEn;
    protected UsuarioEmpresa() {}
    public UsuarioEmpresa(Usuario usuario, UUID empresaId, Rol rol) { this.usuario=usuario; this.empresaId=empresaId; this.rol=rol; }
    public UUID getId(){ return id; } public Usuario getUsuario(){ return usuario; }
    public UUID getEmpresaId(){ return empresaId; } public Rol getRol(){ return rol; }
    public void setRol(Rol value){ rol=value; } public boolean isActivo(){ return activo; }
    public void setActivo(boolean value){ activo=value; } public boolean isAccesoTodasSucursales(){ return accesoTodasSucursales; }
    public void setAccesoTodasSucursales(boolean value){ accesoTodasSucursales=value; }
}
