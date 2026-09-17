package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "roles")
public class Rol {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(nullable = false, length = 50) private String codigo;
    @Column(nullable = false, length = 100) private String nombre;
    @Column(nullable = false) private boolean activo = true;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "rol_permisos", joinColumns = @JoinColumn(name = "rol_id"),
        inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new LinkedHashSet<>();
    protected Rol() {}
    public Rol(UUID empresaId, String codigo, String nombre) { this.empresaId = empresaId; this.codigo = codigo; this.nombre = nombre; }
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public boolean isActivo() { return activo; }
    public Set<Permiso> getPermisos() { return permisos; }
}
