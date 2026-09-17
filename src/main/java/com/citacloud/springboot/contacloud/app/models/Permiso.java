package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "permisos")
public class Permiso {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String codigo;
    @Column(nullable = false, length = 120) private String nombre;
    protected Permiso() {}
    public UUID getId() { return id; }
    public String getCodigo() { return codigo; }
}
