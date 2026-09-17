package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "monedas")
public class Moneda {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(name = "codigo_iso", nullable = false, length = 3) private String codigoIso;
    @Column(nullable = false, length = 80) private String nombre;
    @Column(nullable = false, length = 10) private String simbolo;
    @Column(nullable = false) private short decimales = 2;
    @Column(name = "moneda_base", nullable = false) private boolean monedaBase;
    @Column(nullable = false) private boolean activo = true;
    protected Moneda() {}
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public String getCodigoIso() { return codigoIso; }
    public boolean isMonedaBase() { return monedaBase; }
}
