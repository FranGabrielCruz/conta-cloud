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
    public Sucursal(UUID empresaId, String codigo, String nombre) {
        this.empresaId = empresaId; this.codigo = codigo; this.nombre = nombre;
    }
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String value) { nombre = value; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String value) { direccion = value; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String value) { telefono = value; }
    public boolean isPrincipal() { return principal; }
    public void setPrincipal(boolean value) { principal = value; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean value) { activo = value; }
}
