package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ExistenciaInventarioConsultaService {
    private static final Set<Integer> SIZES=Set.of(10,25,50,100);
    private static final Set<String> STOCK=Set.of("TODOS","CON_EXISTENCIA","SIN_EXISTENCIA","NEGATIVA");
    private final ExistenciaInventarioConsultaRepository query;private final SucursalRepository branches;
    private final AlmacenRepository warehouses;private final ProductRepository products;private final ProductoCategoriaRepository categories;
    public ExistenciaInventarioConsultaService(ExistenciaInventarioConsultaRepository query,SucursalRepository branches,
            AlmacenRepository warehouses,ProductRepository products,ProductoCategoriaRepository categories){
        this.query=query;this.branches=branches;this.warehouses=warehouses;this.products=products;this.categories=categories;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_existencias.ver')")
    public ExistenciaInventarioCatalogosDto catalogos(){
        var principal=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        var sucursales=branches.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresa).stream()
            .filter(x->principal.accesoTodasSucursales()||principal.sucursalIds().contains(x.getId()))
            .map(x->new ExistenciaInventarioCatalogosDto.Opcion(x.getId(),x.getNombre())).toList();
        var almacenes=warehouses.findAllByTenantIdAndEmpresaIdOrderByNombre(principal.tenantId(),empresa).stream()
            .filter(x->principal.accesoTodasSucursales()||principal.sucursalIds().contains(x.getSucursalId()))
            .sorted(Comparator.comparing(Almacen::getNombre)).map(x->new ExistenciaInventarioCatalogosDto.AlmacenOpcion(
                x.getId(),x.getSucursalId(),x.getNombre()+(x.getSucursal()==null?"":" · "+x.getSucursal().getNombre()))).toList();
        var categorias=categories.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(principal.tenantId(),empresa).stream()
            .map(x->new ExistenciaInventarioCatalogosDto.Opcion(x.getId(),x.getNombre())).toList();
        return new ExistenciaInventarioCatalogosDto(sucursales,almacenes,categorias);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_existencias.ver')")
    public PaginaExistenciasInventarioDto buscar(String buscar,UUID sucursal,UUID almacen,UUID categoria,
            String estado,String stock,int pagina,int tamano,String ordenar,String direccion){
        validar(pagina,tamano,stock);validarAlcance(sucursal,almacen,categoria,null);
        var p=TenantContext.principalActual();Sort sort=Sort.by("DESC".equalsIgnoreCase(direccion)?Sort.Direction.DESC:Sort.Direction.ASC,
            Set.of("producto","codigo","almacen","existencia").contains(ordenar)?ordenar:"producto");
        return new PaginaExistenciasInventarioDto(query.buscar(p.tenantId(),EmpresaContext.requerirEmpresaId(),p.accesoTodasSucursales(),
            p.sucursalIds(),buscar,sucursal,almacen,categoria,estado(estado),normalizarStock(stock),PageRequest.of(pagina,tamano,sort)));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_existencias.ver')")
    public ExistenciaInventarioDetalleDto detalle(UUID producto){
        validarAlcance(null,null,null,producto);var p=TenantContext.principalActual();
        Producto item=products.findByIdAndTenantIdAndEmpresaId(producto,p.tenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow();
        var rows=query.distribucion(p.tenantId(),EmpresaContext.requerirEmpresaId(),p.accesoTodasSucursales(),p.sucursalIds(),producto);
        BigDecimal total=rows.stream().map(ExistenciaInventarioDto::existencia).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new ExistenciaInventarioDetalleDto(item.getId(),item.getNombre(),item.getCodigo(),
            item.getUnidadMedida()==null?"—":item.getUnidadMedida().getNombre(),total,rows.stream().map(x->
            new ExistenciaInventarioDetalleDto.Distribucion(x.almacenId(),x.sucursal(),x.almacen(),x.existencia())).toList());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_existencias.ver') and hasAuthority('inventario_existencias.pdf')")
    public List<ExistenciaInventarioDto> reporte(String buscar,UUID sucursal,UUID almacen,UUID categoria,String estado,String stock){
        validarAlcance(sucursal,almacen,categoria,null);var p=TenantContext.principalActual();
        return query.buscarCompleto(p.tenantId(),EmpresaContext.requerirEmpresaId(),p.accesoTodasSucursales(),p.sucursalIds(),
            buscar,sucursal,almacen,categoria,estado(estado),normalizarStock(stock));
    }

    private void validarAlcance(UUID sucursal,UUID almacen,UUID categoria,UUID producto){var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        if(sucursal!=null){branches.findByIdAndEmpresaId(sucursal,empresa).orElseThrow(()->new ReglaNegocioException("La sucursal seleccionada no es válida."));if(!EmpresaContext.permiteSucursal(sucursal))throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");}
        if(almacen!=null){Almacen w=warehouses.findByIdAndTenantIdAndEmpresaId(almacen,p.tenantId(),empresa).orElseThrow(()->new ReglaNegocioException("El almacén seleccionado no es válido."));if(!EmpresaContext.permiteSucursal(w.getSucursalId()))throw new ReglaNegocioException("No tienes acceso al almacén seleccionado.");if(sucursal!=null&&!sucursal.equals(w.getSucursalId()))throw new ReglaNegocioException("El almacén no pertenece a la sucursal seleccionada.");}
        if(categoria!=null)categories.findByIdAndTenantIdAndEmpresaId(categoria,p.tenantId(),empresa).orElseThrow(()->new ReglaNegocioException("La categoría seleccionada no es válida."));
        if(producto!=null){Producto x=products.findByIdAndTenantIdAndEmpresaId(producto,p.tenantId(),empresa).orElseThrow(()->new RecursoNoEncontradoException("Producto no encontrado."));if(!x.isControlaExistencia()||x.getTipo()!=TipoProducto.PRODUCT)throw new ReglaNegocioException("El producto no controla inventario.");}}
    static void validar(int pagina,int tamano,String stock){if(pagina<0||!SIZES.contains(tamano))throw new ReglaNegocioException("Paginación inválida.");normalizarStock(stock);}
    static String normalizarStock(String stock){String value=stock==null?"TODOS":stock.toUpperCase(Locale.ROOT);if(!STOCK.contains(value))throw new ReglaNegocioException("Filtro de stock inválido.");return value;}
    static Boolean estado(String estado){return switch(estado==null?"ACTIVOS":estado.toUpperCase(Locale.ROOT)){case"TODOS"->null;case"INACTIVOS"->false;default->true;};}
}
