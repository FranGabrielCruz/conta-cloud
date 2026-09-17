package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity @Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(nullable = false, length = 60) private String usuario;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(length = 180) private String correo;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private boolean activo = true;
    @Column(name = "ultimo_acceso_en") private Instant ultimoAccesoEn;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_roles", joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles = new LinkedHashSet<>();
    protected Usuario() {}
    public Usuario(UUID empresaId, String usuario, String nombre, String passwordHash) {
        this.empresaId = empresaId; this.usuario = usuario; this.nombre = nombre; this.passwordHash = passwordHash;
    }
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public String getUsuario() { return usuario; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActivo() { return activo; }
    public Set<Rol> getRoles() { return roles; }
    public void registrarAcceso() { ultimoAccesoEn = Instant.now(); }
}
