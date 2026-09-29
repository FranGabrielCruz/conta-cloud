package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "condiciones_pago")
public class CondicionPago {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;
    @Column(nullable = false, length = 30)
    private String codigo;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCondicionPago tipo;
    @Column(nullable = false)
    private int dias;
    @Column(length = 500)
    private String descripcion;
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

    protected CondicionPago() {}

    public CondicionPago(UUID tenantId, UUID empresaId, String codigo, String nombre,
                         TipoCondicionPago tipo, int dias, String descripcion, boolean activo, UUID usuarioId) {
        this.tenantId = tenantId;
        this.empresaId = empresaId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.tipo = tipo;
        this.dias = dias;
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
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public TipoCondicionPago getTipo() { return tipo; }
    public int getDias() { return dias; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
    public UUID getCreadoPor() { return creadoPor; }
    public UUID getActualizadoPor() { return actualizadoPor; }
    public void setNombre(String valor) { nombre = valor; }
    public void setTipo(TipoCondicionPago valor) { tipo = valor; }
    public void setDias(int valor) { dias = valor; }
    public void setDescripcion(String valor) { descripcion = valor; }
    public void setActivo(boolean valor) { activo = valor; }
    public void setActualizadoPor(UUID valor) { actualizadoPor = valor; }
}
