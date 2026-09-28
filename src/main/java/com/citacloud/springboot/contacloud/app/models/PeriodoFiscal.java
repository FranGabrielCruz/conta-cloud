package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "periodos_fiscales")
public class PeriodoFiscal {
    public enum Estado { ABIERTO, CERRADO, BLOQUEADO }
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "empresa_id", nullable = false) private UUID empresaId;
    @Column(nullable = false, length = 120) private String nombre;
    @Column(nullable = false) private int anio;
    @Column(nullable = false) private short mes;
    @Column(name = "fecha_inicial", nullable = false) private LocalDate fechaInicial;
    @Column(name = "fecha_final", nullable = false) private LocalDate fechaFinal;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Estado estado;
    @Column(name = "fecha_cierre") private OffsetDateTime fechaCierre;
    @Column(name = "usuario_cierre_id") private UUID usuarioCierreId;
    @Column(name = "fecha_reapertura") private OffsetDateTime fechaReapertura;
    @Column(name = "usuario_reapertura_id") private UUID usuarioReaperturaId;
    @Column(name = "creado_en", nullable = false, updatable = false) private OffsetDateTime creadoEn = OffsetDateTime.now();
    @Column(name = "actualizado_en", nullable = false) private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    protected PeriodoFiscal() {}
    public PeriodoFiscal(UUID tenantId, UUID empresaId, String nombre, LocalDate fechaInicial, LocalDate fechaFinal) {
        this.tenantId = tenantId;
        this.empresaId = empresaId;
        this.nombre = nombre;
        this.fechaInicial = fechaInicial;
        this.fechaFinal = fechaFinal;
        this.anio = fechaInicial.getYear();
        this.mes = (short) fechaInicial.getMonthValue();
        this.estado = Estado.ABIERTO;
    }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmpresaId() { return empresaId; }
    public String getNombre() { return nombre; }
    public void setNombre(String value) { nombre = value; }
    public LocalDate getFechaInicial() { return fechaInicial; }
    public void setFechaInicial(LocalDate value) { fechaInicial = value; sincronizarPeriodo(); }
    public LocalDate getFechaFinal() { return fechaFinal; }
    public void setFechaFinal(LocalDate value) { fechaFinal = value; }
    public Estado getEstado() { return estado; }
    public OffsetDateTime getFechaCierre() { return fechaCierre; }
    public UUID getUsuarioCierreId() { return usuarioCierreId; }
    public OffsetDateTime getFechaReapertura() { return fechaReapertura; }
    public UUID getUsuarioReaperturaId() { return usuarioReaperturaId; }
    public void cerrar(UUID usuarioId) { estado = Estado.CERRADO; fechaCierre = OffsetDateTime.now(); usuarioCierreId = usuarioId; }
    public void reabrir(UUID usuarioId) { estado = Estado.ABIERTO; fechaReapertura = OffsetDateTime.now(); usuarioReaperturaId = usuarioId; }
    private void sincronizarPeriodo() { if (fechaInicial != null) { anio = fechaInicial.getYear(); mes = (short) fechaInicial.getMonthValue(); } }
    @PreUpdate void actualizarFecha() { actualizadoEn = OffsetDateTime.now(); }
}
