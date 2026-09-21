package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "permisos")
public class Permiso {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String codigo;
    @Column(nullable = false, length = 120) private String nombre;
    @Column(length = 255) private String descripcion;
    @Column(nullable = false, length = 60) private String modulo = "CORE";
    @Column(length = 60) private String recurso;
    @Column(length = 40) private String accion;
    protected Permiso() {}
    public UUID getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getModulo() { return modulo; }
    public String getRecurso() { return recurso; }
    public String getAccion() { return accion; }
}
