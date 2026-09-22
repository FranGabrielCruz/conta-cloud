package com.citacloud.springboot.contacloud.app.multitenancy;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;
public final class TenantRoutingContext {
    private static final ThreadLocal<UUID> OVERRIDE = new ThreadLocal<>();
    private TenantRoutingContext() {}
    public static UUID currentTenantId() {
        UUID tenant = currentTenantIdOrNull();
        if (tenant == null) throw new IllegalStateException("No existe un tenant para enrutar la conexión");
        return tenant;
    }
    public static UUID currentTenantIdOrNull() {
        UUID tenant = OVERRIDE.get();
        if (tenant != null) return tenant;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof TenantPrincipal principal
            ? principal.tenantId() : null;
    }
    public static Scope use(UUID tenantId) {
        UUID previous = OVERRIDE.get(); OVERRIDE.set(tenantId);
        return () -> { if (previous == null) OVERRIDE.remove(); else OVERRIDE.set(previous); };
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
}
