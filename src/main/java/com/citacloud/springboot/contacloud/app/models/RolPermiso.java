package com.citacloud.springboot.contacloud.app.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "rol_permisos")
public class RolPermiso {
    @EmbeddedId
    private RolPermisoId id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    protected RolPermiso() {}

    public RolPermiso(UUID tenantId, UUID empresaId, UUID rolId, UUID permisoId) {
        this.id = new RolPermisoId(rolId, permisoId);
        this.tenantId = tenantId;
        this.empresaId = empresaId;
    }

    public RolPermisoId getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getEmpresaId() { return empresaId; }
}
