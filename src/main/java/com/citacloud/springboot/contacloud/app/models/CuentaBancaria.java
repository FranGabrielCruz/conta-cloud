package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cuentas_bancarias", uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","empresa_id","id"}))
public class CuentaBancaria {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;
    @Column(name = "moneda_id", nullable = false)
    private UUID monedaId;
    @Column(nullable = false, length = 40)
    private String codigo;
    @Column(name = "banco_nombre", nullable = false, length = 120)
    private String bancoNombre;
    @Column(name = "nombre_cuenta", nullable = false, length = 120)
    private String nombreCuenta;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cuenta", nullable = false, length = 20)
    private TipoCuentaBancaria tipoCuenta;
    @Column(name = "numero_cuenta_cifrado", nullable = false, length = 1024)
    private String numeroCuentaCifrado;
    @Column(name = "numero_cuenta_ultimos4", nullable = false, length = 4)
    private String numeroCuentaUltimos4;
    @Column(name = "numero_cuenta_fingerprint", nullable = false, length = 64)
    private String numeroCuentaFingerprint;
    @Column(length = 500)
    private String descripcion;
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadoEn;
    @Column(name = "creado_por")
    private UUID creadoPor;
    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;
    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "empresa_id", referencedColumnName = "empresa_id", insertable = false, updatable = false),
        @JoinColumn(name = "moneda_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Moneda moneda;

    protected CuentaBancaria() {}

    public CuentaBancaria(UUID tenantId, UUID empresaId, UUID monedaId, String codigo,
                          String bancoNombre, String nombreCuenta, TipoCuentaBancaria tipoCuenta,
                          String numeroCuentaCifrado, String numeroCuentaUltimos4,
                          String numeroCuentaFingerprint, String descripcion, boolean activo,
                          UUID usuarioId) {
        this.tenantId = tenantId;
        this.empresaId = empresaId;
        this.monedaId = monedaId;
        this.codigo = codigo;
        this.bancoNombre = bancoNombre;
        this.nombreCuenta = nombreCuenta;
        this.tipoCuenta = tipoCuenta;
        this.numeroCuentaCifrado = numeroCuentaCifrado;
        this.numeroCuentaUltimos4 = numeroCuentaUltimos4;
        this.numeroCuentaFingerprint = numeroCuentaFingerprint;
        this.descripcion = descripcion;
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
    public UUID getMonedaId() { return monedaId; }
    public String getCodigo() { return codigo; }
    public String getBancoNombre() { return bancoNombre; }
    public String getNombreCuenta() { return nombreCuenta; }
    public TipoCuentaBancaria getTipoCuenta() { return tipoCuenta; }
    public String getNumeroCuentaCifrado() { return numeroCuentaCifrado; }
    public String getNumeroCuentaUltimos4() { return numeroCuentaUltimos4; }
    public String getNumeroCuentaFingerprint() { return numeroCuentaFingerprint; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
    public Moneda getMoneda() { return moneda; }
    public void asignarMoneda(Moneda value) { moneda = value; monedaId = value.getId(); }
    public void setBancoNombre(String value) { bancoNombre = value; }
    public void setNombreCuenta(String value) { nombreCuenta = value; }
    public void setTipoCuenta(TipoCuentaBancaria value) { tipoCuenta = value; }
    public void setNumeroCuenta(String cifrado, String ultimos4, String fingerprint) {
        numeroCuentaCifrado = cifrado;
        numeroCuentaUltimos4 = ultimos4;
        numeroCuentaFingerprint = fingerprint;
    }
    public void setDescripcion(String value) { descripcion = value; }
    public void setActivo(boolean value) { activo = value; }
    public void setActualizadoPor(UUID value) { actualizadoPor = value; }
}
