package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "sucursales")
public class Sucursal {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(nullable = false, length = 30) private String codigo;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(length = 255) private String direccion;
    @Column(length = 40) private String telefono;
    @Column(nullable = false) private boolean principal;
    @Column(nullable = false) private boolean activo = true;
    protected Sucursal() {}
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public boolean isPrincipal() { return principal; }
    public boolean isActivo() { return activo; }
}
