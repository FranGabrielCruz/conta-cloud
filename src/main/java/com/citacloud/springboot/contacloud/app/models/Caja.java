package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cajas", uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","empresa_id","id"}))
public class Caja {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;
    @Column(name = "sucursal_id", nullable = false)
    private UUID sucursalId;
    @Column(name = "moneda_id", nullable = false)
    private UUID monedaId;
    @Column(nullable = false, length = 40)
    private String codigo;
    @Column(nullable = false, length = 120)
    private String nombre;
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
        @JoinColumn(name = "sucursal_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Sucursal sucursal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "empresa_id", referencedColumnName = "empresa_id", insertable = false, updatable = false),
        @JoinColumn(name = "moneda_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private Moneda moneda;

    protected Caja() {}

    public Caja(UUID tenantId, UUID empresaId, UUID sucursalId, UUID monedaId, String codigo,
                String nombre, String descripcion, boolean activo, UUID usuarioId) {
        this.tenantId = tenantId;
        this.empresaId = empresaId;
        this.sucursalId = sucursalId;
        this.monedaId = monedaId;
        this.codigo = codigo;
        this.nombre = nombre;
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
    public UUID getSucursalId() { return sucursalId; }
    public UUID getMonedaId() { return monedaId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
    public Sucursal getSucursal() { return sucursal; }
    public Moneda getMoneda() { return moneda; }
    public void setSucursalId(UUID value) { sucursalId = value; }
    public void setMonedaId(UUID value) { monedaId = value; }
    public void asignarSucursal(Sucursal value) { sucursal = value; sucursalId = value.getId(); }
    public void asignarMoneda(Moneda value) { moneda = value; monedaId = value.getId(); }
    public void setNombre(String value) { nombre = value; }
    public void setDescripcion(String value) { descripcion = value; }
    public void setActivo(boolean value) { activo = value; }
    public void setActualizadoPor(UUID value) { actualizadoPor = value; }
}

