package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasas_cambio", uniqueConstraints = @UniqueConstraint(
    name = "uk_tasas_tenant_empresa_monedas_fecha",
    columnNames = {"tenant_id", "empresa_id", "moneda_origen_id", "moneda_destino_id", "fecha"}))
public class TasaCambio {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;
    @Column(name = "moneda_origen_id", nullable = false)
    private UUID monedaOrigenId;
    @Column(name = "moneda_destino_id", nullable = false)
    private UUID monedaDestinoId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_origen_id", insertable = false, updatable = false)
    private Moneda monedaOrigen;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moneda_destino_id", insertable = false, updatable = false)
    private Moneda monedaDestino;
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal tasa;
    @Column(nullable = false)
    private LocalDate fecha;
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadoEn;
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;
    @Column(name = "creado_por")
    private UUID creadoPor;
    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    protected TasaCambio() {}

    public TasaCambio(UUID tenantId, UUID empresaId, UUID monedaOrigenId, UUID monedaDestinoId,
                      BigDecimal tasa, LocalDate fecha, boolean activo, UUID usuarioId) {
        this.tenantId = tenantId;
        this.empresaId = empresaId;
        this.monedaOrigenId = monedaOrigenId;
        this.monedaDestinoId = monedaDestinoId;
        this.tasa = tasa;
        this.fecha = fecha;
        this.activo = activo;
        this.creadoPor = usuarioId;
        this.actualizadoPor = usuarioId;
        this.actualizadoEn = OffsetDateTime.now();
    }

    @PreUpdate
    void actualizarMarcaTiempo() { actualizadoEn = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmpresaId() { return empresaId; }
    public UUID getMonedaOrigenId() { return monedaOrigenId; }
    public UUID getMonedaDestinoId() { return monedaDestinoId; }
    public Moneda getMonedaOrigen() { return monedaOrigen; }
    public Moneda getMonedaDestino() { return monedaDestino; }
    public BigDecimal getTasa() { return tasa; }
    public LocalDate getFecha() { return fecha; }
    public boolean isActivo() { return activo; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
    public UUID getCreadoPor() { return creadoPor; }
    public UUID getActualizadoPor() { return actualizadoPor; }
    public void setMonedaOrigenId(UUID valor) { monedaOrigenId = valor; }
    public void setMonedaDestinoId(UUID valor) { monedaDestinoId = valor; }
    public void setTasa(BigDecimal valor) { tasa = valor; }
    public void setFecha(LocalDate valor) { fecha = valor; }
    public void setActivo(boolean valor) { activo = valor; }
    public void setActualizadoPor(UUID valor) { actualizadoPor = valor; }
}
