package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class MovimientoInventarioConsultaService {
    private static final Set<Integer> SIZES = Set.of(10, 25, 50, 100);
    private final MovimientoInventarioConsultaRepository query;
    private final SucursalRepository branches;
    private final AlmacenRepository warehouses;
    private final ProductRepository products;

    public MovimientoInventarioConsultaService(MovimientoInventarioConsultaRepository query,
            SucursalRepository branches, AlmacenRepository warehouses, ProductRepository products) {
        this.query = query;
        this.branches = branches;
        this.warehouses = warehouses;
        this.products = products;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.ver')")
    public MovimientoInventarioCatalogosDto catalogos() {
        var principal = TenantContext.principalActual();
        UUID empresa = EmpresaContext.requerirEmpresaId();
        var branchList = branches.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresa).stream()
            .filter(branch -> principal.accesoTodasSucursales() || principal.sucursalIds().contains(branch.getId()))
            .map(branch -> new MovimientoInventarioCatalogosDto.Opcion(branch.getId(), branch.getNombre())).toList();
        var warehouseList = warehouses.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(principal.tenantId(), empresa)
            .stream().filter(warehouse -> principal.accesoTodasSucursales()
                || principal.sucursalIds().contains(warehouse.getSucursalId()))
            .map(warehouse -> new MovimientoInventarioCatalogosDto.AlmacenOpcion(warehouse.getId(),
                warehouse.getSucursalId(), warehouse.getNombre())).toList();
        var productList = products.findAllByTenantIdAndEmpresaIdAndActivoTrueAndControlaExistenciaTrueOrderByNombre(
                principal.tenantId(), empresa).stream()
            .map(product -> new MovimientoInventarioCatalogosDto.ProductoOpcion(product.getId(), product.getCodigo(),
                product.getNombre(), product.getUnidadMedida() == null ? "" : product.getUnidadMedida().getAbreviatura()))
            .toList();
        return new MovimientoInventarioCatalogosDto(branchList, warehouseList, productList);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.ver')")
    public PaginaMovimientosInventarioDto general(String buscar, LocalDate desde, LocalDate hasta, UUID sucursal,
            UUID almacen, UUID producto, String tipo, OrigenMovimientoInventario origen, int page, int size) {
        validate(page, size, desde, hasta);
        validateScope(sucursal, almacen, producto);
        var principal = TenantContext.principalActual();
        return query.general(principal.tenantId(), EmpresaContext.requerirEmpresaId(),
            principal.accesoTodasSucursales(), principal.sucursalIds(), buscar, desde, hasta, sucursal, almacen,
            producto, direction(tipo), origen, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.kardex')")
    public PaginaKardexDto kardex(UUID producto, UUID sucursal, UUID almacen, LocalDate desde, LocalDate hasta,
            int page, int size) {
        validate(page, size, desde, hasta);
        requireProduct(producto);
        validateScope(sucursal, almacen, producto);
        var principal = TenantContext.principalActual();
        return query.kardex(principal.tenantId(), EmpresaContext.requerirEmpresaId(),
            principal.accesoTodasSucursales(), principal.sucursalIds(), producto, sucursal, almacen, desde, hasta,
            PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.ver') and hasAuthority('inventario_movimientos.pdf')")
    public MovimientosInventarioReporteConsultaDto generalReporte(String buscar, LocalDate desde, LocalDate hasta,
            UUID sucursal, UUID almacen, UUID producto, String tipo, OrigenMovimientoInventario origen) {
        validateDates(desde, hasta);
        validateScope(sucursal, almacen, producto);
        var principal = TenantContext.principalActual();
        return query.generalCompleto(principal.tenantId(), EmpresaContext.requerirEmpresaId(),
            principal.accesoTodasSucursales(), principal.sucursalIds(), buscar, desde, hasta, sucursal, almacen,
            producto, direction(tipo), origen);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.kardex') and hasAuthority('inventario_movimientos.pdf')")
    public KardexInventarioReporteConsultaDto kardexReporte(UUID producto, UUID sucursal, UUID almacen,
            LocalDate desde, LocalDate hasta) {
        validateDates(desde, hasta);
        requireProduct(producto);
        validateScope(sucursal, almacen, producto);
        var principal = TenantContext.principalActual();
        return query.kardexCompleto(principal.tenantId(), EmpresaContext.requerirEmpresaId(),
            principal.accesoTodasSucursales(), principal.sucursalIds(), producto, sucursal, almacen, desde, hasta);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_movimientos.detalle')")
    public MovimientoInventarioDetalleDto detalle(UUID id) {
        if (id == null) throw new RecursoNoEncontradoException("Movimiento no encontrado.");
        var principal = TenantContext.principalActual();
        var result = query.detalle(principal.tenantId(), EmpresaContext.requerirEmpresaId(),
            principal.accesoTodasSucursales(), principal.sucursalIds(), id);
        if (result == null) throw new RecursoNoEncontradoException("Movimiento no encontrado.");
        return result;
    }

    private void validateScope(UUID branchId, UUID warehouseId, UUID productId) {
        var principal = TenantContext.principalActual();
        UUID companyId = EmpresaContext.requerirEmpresaId();
        if (branchId != null) {
            branches.findByIdAndEmpresaId(branchId, companyId)
                .orElseThrow(() -> new ReglaNegocioException("La sucursal seleccionada no es válida."));
            if (!EmpresaContext.permiteSucursal(branchId))
                throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");
        }
        if (warehouseId != null) {
            Almacen warehouse = warehouses.findByIdAndTenantIdAndEmpresaId(warehouseId, principal.tenantId(), companyId)
                .orElseThrow(() -> new ReglaNegocioException("El almacén seleccionado no es válido."));
            if (!EmpresaContext.permiteSucursal(warehouse.getSucursalId()))
                throw new ReglaNegocioException("No tienes acceso al almacén seleccionado.");
            if (branchId != null && !branchId.equals(warehouse.getSucursalId()))
                throw new ReglaNegocioException("El almacén no pertenece a la sucursal seleccionada.");
        }
        if (productId != null) {
            Producto product = products.findByIdAndTenantIdAndEmpresaId(productId, principal.tenantId(), companyId)
                .orElseThrow(() -> new ReglaNegocioException("El producto seleccionado no es válido."));
            if (!product.isControlaExistencia() || product.getTipo() != TipoProducto.PRODUCT)
                throw new ReglaNegocioException("El producto seleccionado no controla inventario.");
        }
    }

    private static void requireProduct(UUID productId) {
        if (productId == null) throw new ReglaNegocioException("Selecciona un producto para consultar el Kardex.");
    }

    private static String direction(String type) {
        return switch (type == null ? "TODOS" : type) {
            case "ENTRADA" -> "IN";
            case "SALIDA" -> "OUT";
            default -> null;
        };
    }

    static void validate(int page, int size, LocalDate from, LocalDate to) {
        if (page < 0 || !SIZES.contains(size)) throw new ReglaNegocioException("Paginación inválida.");
        validateDates(from, to);
    }

    static void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from))
            throw new ReglaNegocioException("La fecha final no puede ser anterior a la fecha inicial.");
    }
}
