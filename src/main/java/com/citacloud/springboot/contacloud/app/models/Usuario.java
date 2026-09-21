package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(nullable = false, length = 60) private String usuario;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(nullable = false, length = 100) private String apellido = "";
    @Column(length = 180) private String correo;
    @Column(length = 40) private String telefono;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false) private boolean activo = true;
    @Column(name = "ultimo_acceso_en") private Instant ultimoAccesoEn;
    protected Usuario() {}
    public Usuario(UUID tenantId, UUID empresaId, String usuario, String nombre, String apellido, String passwordHash) {
        this.tenantId = tenantId; this.empresaId = empresaId; this.usuario = usuario; this.nombre = nombre;
        this.apellido = apellido; this.passwordHash = passwordHash;
    }
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public UUID getTenantId() { return tenantId; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String value) { usuario = value; }
    public String getNombre() { return nombre; }
    public void setNombre(String value) { nombre = value; }
    public String getApellido() { return apellido; }
    public void setApellido(String value) { apellido = value; }
    public String getCorreo() { return correo; }
    public void setCorreo(String value) { correo = value; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String value) { telefono = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean value) { activo = value; }
    public void registrarAcceso() { ultimoAccesoEn = Instant.now(); }
}
