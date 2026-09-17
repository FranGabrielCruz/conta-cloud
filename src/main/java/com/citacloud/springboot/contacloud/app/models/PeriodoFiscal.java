package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "periodos_fiscales")
public class PeriodoFiscal {
    public enum Estado { ABIERTO, CERRADO, BLOQUEADO }
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(nullable = false) private int anio;
    @Column(nullable = false) private short mes;
    @Column(name = "fecha_inicial", nullable = false) private LocalDate fechaInicial;
    @Column(name = "fecha_final", nullable = false) private LocalDate fechaFinal;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Estado estado;
    protected PeriodoFiscal() {}
    public UUID getId() { return id; }
    public UUID getEmpresaId() { return empresaId; }
    public Estado getEstado() { return estado; }
}
