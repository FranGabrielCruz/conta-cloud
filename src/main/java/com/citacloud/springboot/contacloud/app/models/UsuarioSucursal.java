package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity @Table(name="usuario_sucursal") @IdClass(UsuarioSucursal.Clave.class)
public class UsuarioSucursal {
    @Id @Column(name="usuario_empresa_id") private UUID usuarioEmpresaId;
    @Id @Column(name="sucursal_id") private UUID sucursalId;
    protected UsuarioSucursal() {}
    public UsuarioSucursal(UUID accesoId, UUID sucursalId){ this.usuarioEmpresaId=accesoId; this.sucursalId=sucursalId; }
    public UUID getUsuarioEmpresaId(){ return usuarioEmpresaId; } public UUID getSucursalId(){ return sucursalId; }
    public static class Clave implements Serializable {
        public UUID usuarioEmpresaId; public UUID sucursalId; public Clave() {}
        public Clave(UUID a, UUID s){ usuarioEmpresaId=a; sucursalId=s; }
        @Override public boolean equals(Object o){ return o instanceof Clave c && Objects.equals(usuarioEmpresaId,c.usuarioEmpresaId) && Objects.equals(sucursalId,c.sucursalId); }
        @Override public int hashCode(){ return Objects.hash(usuarioEmpresaId,sucursalId); }
    }
}
