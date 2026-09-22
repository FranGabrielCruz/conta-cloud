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
    @Column(length = 255) private String descripcion;
    @Column(nullable = false) private boolean protegido;
    @Column(nullable = false) private boolean activo = true;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "rol_permisos", joinColumns = @JoinColumn(name = "rol_id"),
        inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new LinkedHashSet<>();
    protected Rol() {}
    public Rol(UUID empresaId, String codigo, String nombre) { this.empresaId = empresaId; this.codigo = codigo; this.nombre = nombre; }
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String value) { nombre = value; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String value) { descripcion = value; }
    public boolean isProtegido() { return protegido; }
    public void setProtegido(boolean value) { protegido = value; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean value) { activo = value; }
    public Set<Permiso> getPermisos() { return permisos; }
}
