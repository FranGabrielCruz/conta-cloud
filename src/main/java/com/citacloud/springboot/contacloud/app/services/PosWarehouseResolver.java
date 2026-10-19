package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.Almacen;
import com.citacloud.springboot.contacloud.app.models.Sucursal;
import com.citacloud.springboot.contacloud.app.models.TipoAlmacen;
import com.citacloud.springboot.contacloud.app.repositories.AlmacenRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class PosWarehouseResolver {
    static final String SIN_ALMACEN = "No hay un almacén disponible para ventas en la sucursal de esta caja. "
            + "Configure un almacén válido antes de facturar.";

    private final AlmacenRepository almacenes;

    public PosWarehouseResolver(AlmacenRepository almacenes) {
        this.almacenes = almacenes;
    }

    public Almacen resolver(UUID tenantId, UUID empresaId, Sucursal sucursal) {
        if (sucursal == null || !sucursal.isActivo()) {
            throw new ReglaNegocioException("La sucursal configurada para esta caja no está disponible.");
        }
        UUID predeterminadoId = sucursal.getAlmacenPredeterminadoId();
        if (predeterminadoId != null) {
            Almacen predeterminado = almacenes.findByIdAndTenantIdAndEmpresaId(predeterminadoId, tenantId, empresaId)
                    .orElse(null);
            if (validoParaVentas(predeterminado, tenantId, empresaId, sucursal.getId())) return predeterminado;
        }
        return almacenes.findFirstForPos(tenantId, empresaId, sucursal.getId(), TipoAlmacen.GENERAL)
                .orElseThrow(() -> new ReglaNegocioException(SIN_ALMACEN));
    }

    static boolean validoParaVentas(Almacen almacen, UUID tenantId, UUID empresaId, UUID sucursalId) {
        return almacen != null
                && Objects.equals(almacen.getTenantId(), tenantId)
                && Objects.equals(almacen.getEmpresaId(), empresaId)
                && Objects.equals(almacen.getSucursalId(), sucursalId)
                && almacen.isActivo()
                && !almacen.isAdministradoSistema()
                && almacen.getTipo() == TipoAlmacen.GENERAL;
    }
}
