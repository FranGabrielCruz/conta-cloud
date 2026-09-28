package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "configuracion_contable")
public class ConfiguracionContable {
    public enum MetodoContable { DEVENGADO, EFECTIVO }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;
    @Column(name = "moneda_base_id", nullable = false)
    private UUID monedaBaseId;
    @Column(name = "mes_inicio_fiscal", nullable = false)
    private short mesInicioFiscal = 1;
    @Column(name = "metodo_numeracion", nullable = false, length = 30)
    private String metodoNumeracion = "POR_SECUENCIA";
    @Column(nullable = false)
    private short decimales = 2;
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_contable", nullable = false, length = 20)
    private MetodoContable metodoContable = MetodoContable.DEVENGADO;
    @Column(name = "contabilizacion_automatica", nullable = false)
    private boolean contabilizacionAutomatica;
    @Column(name = "permitir_periodos_cerrados", nullable = false)
    private boolean permitirPeriodosCerrados;
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    protected ConfiguracionContable() {}

    public ConfiguracionContable(UUID tenantId, UUID empresaId, UUID monedaBaseId, short decimales) {
        this.tenantId = tenantId;
        this.empresaId = empresaId;
        this.monedaBaseId = monedaBaseId;
        this.decimales = decimales;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmpresaId() { return empresaId; }
    public MetodoContable getMetodoContable() { return metodoContable; }
    public void setMetodoContable(MetodoContable value) { metodoContable = value; }
    public boolean isContabilizacionAutomatica() { return contabilizacionAutomatica; }
    public void setContabilizacionAutomatica(boolean value) { contabilizacionAutomatica = value; }
    public boolean isPermitirPeriodosCerrados() { return permitirPeriodosCerrados; }
    public void setPermitirPeriodosCerrados(boolean value) { permitirPeriodosCerrados = value; }

    @PreUpdate
    void actualizarFecha() { actualizadoEn = OffsetDateTime.now(); }
}
