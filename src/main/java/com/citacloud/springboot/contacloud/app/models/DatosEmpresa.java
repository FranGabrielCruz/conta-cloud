package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "datos_empresa")
public class DatosEmpresa {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "empresa_id", nullable = false, unique = true) private UUID empresaId;
    @Column(name = "nombre_comercial", length = 150) private String nombreComercial;
    @Column(name = "razon_social", length = 150) private String razonSocial;
    @Column(length = 255) private String direccion;
    @Column(length = 40) private String telefono;
    @Column(length = 180) private String correo;
    @Column(name = "actualizado_en", nullable = false) private Instant actualizadoEn = Instant.now();

    protected DatosEmpresa() {}
    public DatosEmpresa(UUID empresaId, String nombreComercial) {
        this.empresaId = empresaId; this.nombreComercial = nombreComercial;
    }
    @PreUpdate void beforeUpdate() { actualizadoEn = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String value) { nombreComercial = value; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String value) { razonSocial = value; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String value) { direccion = value; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String value) { telefono = value; }
    public String getCorreo() { return correo; }
    public void setCorreo(String value) { correo = value; }
}
