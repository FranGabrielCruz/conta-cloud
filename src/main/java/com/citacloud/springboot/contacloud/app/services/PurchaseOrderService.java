package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.OrdenCompraMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class PurchaseOrderService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final PurchaseOrderRepository orders; private final SupplierRepository suppliers;
    private final SucursalRepository branches; private final MonedaRepository currencies;
    private final CondicionPagoRepository terms; private final ImpuestoRepository taxes;
    private final PurchaseOrderCalculationService calculations; private final PurchaseOrderNumberService numbers;
    private final OrdenCompraMapper mapper; private final AuditoriaService audit;

    public PurchaseOrderService(PurchaseOrderRepository orders,SupplierRepository suppliers,SucursalRepository branches,
            MonedaRepository currencies,CondicionPagoRepository terms,ImpuestoRepository taxes,
            PurchaseOrderCalculationService calculations,PurchaseOrderNumberService numbers,OrdenCompraMapper mapper,
            AuditoriaService audit){this.orders=orders;this.suppliers=suppliers;this.branches=branches;this.currencies=currencies;
        this.terms=terms;this.taxes=taxes;this.calculations=calculations;this.numbers=numbers;this.mapper=mapper;this.audit=audit;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('ordenes_compra.ver')")
    public Page<OrdenCompraDto> search(String text,LocalDate from,LocalDate to,EstadoOrdenCompra status,int page,int size){
        if(page<0||!PAGE_SIZES.contains(size))throw new ReglaNegocioException("Paginación inválida.");
        if(from!=null&&to!=null&&to.isBefore(from))throw new ReglaNegocioException("La fecha final no puede ser anterior a la fecha inicial.");
        var p=TenantContext.principalActual();return orders.buscar(p.tenantId(),p.empresaId(),clean(text),from,to,status,
            PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"fecha").and(Sort.by(Sort.Direction.DESC,"creadaEn")))).map(mapper::toSummaryDto);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('ordenes_compra.ver')")
    public OrdenCompraDto get(UUID id){return mapper.toDto(safe(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('ordenes_compra.crear','ordenes_compra.editar')")
    public OrdenCompraCatalogosDto catalogs(){var p=TenantContext.principalActual();UUID company=p.empresaId();
        var availableBranches=branches.findAllByEmpresaIdAndActivoTrueOrderByNombre(company).stream().filter(s->EmpresaContext.permiteSucursal(s.getId()))
            .map(s->new OrdenCompraCatalogosDto.SucursalOpcion(s.getId(),s.getNombre())).toList();
        var money=currencies.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(company).stream()
            .map(m->new OrdenCompraCatalogosDto.MonedaOpcion(m.getId(),m.getCodigoIso(),m.getNombre())).toList();
        var paymentTerms=terms.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),company).stream()
            .map(t->new OrdenCompraCatalogosDto.CondicionOpcion(t.getId(),SupplierService.nombreCondicion(t))).toList();
        var taxOptions=taxes.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),company).stream()
            .map(t->new OrdenCompraCatalogosDto.ImpuestoOpcion(t.getId(),t.getNombre(),t.getPorcentaje())).toList();
        UUID base=currencies.findByEmpresaIdAndMonedaBaseTrue(company).filter(Moneda::isActivo).map(Moneda::getId).orElse(null);
        return new OrdenCompraCatalogosDto(List.of(),availableBranches,money,paymentTerms,taxOptions,base);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('ordenes_compra.crear','ordenes_compra.editar')")
    public List<OrdenCompraCatalogosDto.ProveedorOpcion> searchSupplierOptions(String filter,int offset,int limit){
        if(offset<0||limit<1||limit>50)throw new ReglaNegocioException("Paginación de proveedores inválida.");var p=TenantContext.principalActual();String text=clean(filter);
        return suppliers.buscar(p.tenantId(),p.empresaId(),text,text.replaceAll("[^\\p{Alnum}]",""),true,
            PageRequest.of(offset/limit,limit,Sort.by("nombreComercial"))).stream().map(PurchaseOrderService::supplierOption).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('ordenes_compra.crear','ordenes_compra.editar')")
    public OrdenCompraCatalogosDto.ProveedorOpcion supplierOption(UUID id){var p=TenantContext.principalActual();return supplierOption(activeSupplier(id,p.tenantId(),p.empresaId()));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('ordenes_compra.crear')")
    public OrdenCompraDto create(OrdenCompraInput input){var p=TenantContext.principalActual();Validated v=validate(input,p.tenantId(),p.empresaId());
        OrdenCompra order=new OrdenCompra(p.tenantId(),p.empresaId(),numbers.next(p.tenantId(),p.empresaId()),v.supplier().getId(),
            providerName(v.supplier()),v.supplier().getIdentificacionFiscal(),v.branch().getId(),input.fecha(),input.fechaEntrega(),
            v.currency().getId(),v.term()==null?null:v.term().getId(),optional(input.referencia(),100,"La referencia excede 100 caracteres."),
            optional(input.notas(),1000,"Las notas exceden 1000 caracteres."),p.usuarioId());
        applyLines(order,v,p);order=orders.saveAndFlush(order);audit.registrar("PURCHASE_ORDER_CREATED","OrdenCompra",order.getId(),detail(order));
        return mapper.toDto(order);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('ordenes_compra.editar')")
    public OrdenCompraDto update(UUID id,OrdenCompraInput input){OrdenCompra order=safe(id);expected(order,input==null?null:input.version());draft(order);
        var p=TenantContext.principalActual();Validated v=validate(input,p.tenantId(),p.empresaId());
        order.actualizar(v.supplier().getId(),providerName(v.supplier()),v.supplier().getIdentificacionFiscal(),v.branch().getId(),input.fecha(),
            input.fechaEntrega(),v.currency().getId(),v.term()==null?null:v.term().getId(),optional(input.referencia(),100,"La referencia excede 100 caracteres."),
            optional(input.notas(),1000,"Las notas exceden 1000 caracteres."),p.usuarioId());applyLines(order,v,p);
        try{order=orders.saveAndFlush(order);}catch(OptimisticLockingFailureException ex){throw concurrency(ex);}
        audit.registrar("PURCHASE_ORDER_UPDATED","OrdenCompra",order.getId(),detail(order));return mapper.toDto(order);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('ordenes_compra.emitir')")
    public OrdenCompraDto issue(UUID id,long version){OrdenCompra order=locked(id);expected(order,version);draft(order);
        if(order.getLineas().isEmpty())throw new ReglaNegocioException("La orden debe tener al menos una línea antes de emitirla.");
        Proveedor supplier=activeSupplier(order.getProveedorId(),order.getTenantId(),order.getEmpresaId());
        recalculateStored(order);order.emitir(providerName(supplier),supplier.getIdentificacionFiscal(),TenantContext.principalActual().usuarioId());
        order=orders.saveAndFlush(order);audit.registrar("PURCHASE_ORDER_ISSUED","OrdenCompra",order.getId(),detail(order));return mapper.toDto(order);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('ordenes_compra.anular')")
    public OrdenCompraDto voidOrder(UUID id,long version,String reason){OrdenCompra order=locked(id);expected(order,version);
        if(order.getEstado()!=EstadoOrdenCompra.DRAFT&&order.getEstado()!=EstadoOrdenCompra.ISSUED)
            throw new ReglaNegocioException("Solo se pueden anular órdenes en borrador o emitidas.");
        String cleanReason=required(reason,500,"El motivo de anulación es obligatorio.","El motivo de anulación excede 500 caracteres.");
        order.anular(cleanReason,TenantContext.principalActual().usuarioId());order=orders.saveAndFlush(order);
        audit.registrar("PURCHASE_ORDER_VOIDED","OrdenCompra",order.getId(),"{\"numero\":\""+escape(order.getNumero())+"\",\"motivo\":\""+escape(cleanReason)+"\"}");return mapper.toDto(order);
    }

    private Validated validate(OrdenCompraInput input,UUID tenant,UUID company){if(input==null)throw new ReglaNegocioException("Los datos de la orden son obligatorios.");
        if(input.fecha()==null)throw new ReglaNegocioException("La fecha de la orden es obligatoria.");
        if(input.fechaEntrega()!=null&&input.fechaEntrega().isBefore(input.fecha()))throw new ReglaNegocioException("La fecha esperada de entrega no puede ser anterior a la fecha de la orden.");
        Proveedor supplier=activeSupplier(input.proveedorId(),tenant,company);
        Sucursal branch=branches.findByIdAndEmpresaId(input.sucursalId(),company).filter(Sucursal::isActivo).orElseThrow(()->new ReglaNegocioException("La sucursal seleccionada no está disponible."));
        if(!EmpresaContext.permiteSucursal(branch.getId()))throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");
        Moneda currency=currencies.findByIdAndEmpresaId(input.monedaId(),company).filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("La moneda seleccionada no está disponible."));
        CondicionPago term=input.condicionPagoId()==null?null:terms.findByIdAndTenantIdAndEmpresaId(input.condicionPagoId(),tenant,company).filter(CondicionPago::isActivo).orElseThrow(()->new ReglaNegocioException("La condición de pago seleccionada no está disponible."));
        if(input.lineas()==null||input.lineas().isEmpty())throw new ReglaNegocioException("Agrega al menos una línea a la orden de compra.");
        List<LineValidated> lines=new ArrayList<>();for(var line:input.lineas()){Impuesto tax=line.impuestoId()==null?null:taxes.findByIdAndTenantIdAndEmpresaId(line.impuestoId(),tenant,company).filter(Impuesto::isActivo).orElseThrow(()->new ReglaNegocioException("El impuesto seleccionado no está disponible."));
            var calculated=calculations.calculate(line,tax==null?null:tax.getNombre(),tax==null?java.math.BigDecimal.ZERO:tax.getPorcentaje());lines.add(new LineValidated(line.impuestoId(),calculated));}
        return new Validated(supplier,branch,currency,term,lines);
    }
    private void applyLines(OrdenCompra order,Validated v,com.citacloud.springboot.contacloud.app.security.TenantPrincipal p){List<LineaOrdenCompra> entities=new ArrayList<>();int number=1;
        for(var l:v.lines()){var c=l.value();entities.add(new LineaOrdenCompra(p.tenantId(),p.empresaId(),number++,c.description(),c.quantity(),c.unitPrice(),c.discount(),l.taxId(),c.taxName(),c.taxRate(),c.grossSubtotal(),c.taxableBase(),c.taxAmount(),c.total()));}
        var totals=calculations.totals(v.lines().stream().map(LineValidated::value).toList());order.reemplazarLineas(entities);order.totales(totals.subtotal(),totals.discount(),totals.tax(),totals.total());}
    private void recalculateStored(OrdenCompra order){var values=order.getLineas().stream().map(l->calculations.calculate(new LineaOrdenCompraInput(l.getDescripcion(),l.getCantidad(),l.getPrecioUnitario(),l.getDescuento(),l.getImpuestoId()),l.getImpuestoNombre(),l.getTasaImpuesto())).toList();
        var totals=calculations.totals(values);order.totales(totals.subtotal(),totals.discount(),totals.tax(),totals.total());}
    private Proveedor activeSupplier(UUID id,UUID tenant,UUID company){if(id==null)throw new ReglaNegocioException("El proveedor es obligatorio.");return suppliers.findByIdAndTenantIdAndEmpresaId(id,tenant,company).filter(Proveedor::isActivo).orElseThrow(()->new ReglaNegocioException("El proveedor seleccionado no está disponible."));}
    private OrdenCompra safe(UUID id){if(id==null)throw new RecursoNoEncontradoException("Orden de compra no encontrada.");var p=TenantContext.principalActual();return orders.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Orden de compra no encontrada."));}
    private OrdenCompra locked(UUID id){if(id==null)throw new RecursoNoEncontradoException("Orden de compra no encontrada.");var p=TenantContext.principalActual();return orders.bloquear(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Orden de compra no encontrada."));}
    private static void draft(OrdenCompra o){if(o.getEstado()!=EstadoOrdenCompra.DRAFT)throw new ReglaNegocioException("Solo se pueden editar órdenes en borrador.");}
    private static void expected(OrdenCompra o,Long expected){if(expected==null||o.getVersion()!=expected)throw concurrency(null);}
    private static ReglaNegocioException concurrency(Exception ex){return new ReglaNegocioException("La orden fue modificada por otro usuario. Actualiza la pantalla e inténtalo nuevamente.",ex);}
    private static String providerName(Proveedor p){return p.getRazonSocial()==null||p.getRazonSocial().isBlank()?p.getNombreComercial():p.getRazonSocial();}
    private static OrdenCompraCatalogosDto.ProveedorOpcion supplierOption(Proveedor p){return new OrdenCompraCatalogosDto.ProveedorOpcion(p.getId(),p.getNombreComercial(),p.getIdentificacionFiscal(),p.getCondicionPagoId(),p.getMonedaId());}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String optional(String s,int max,String message){String v=clean(s);if(v.isEmpty())return null;if(v.length()>max)throw new ReglaNegocioException(message);return v;}
    private static String required(String s,int max,String empty,String tooLong){String v=clean(s);if(v.isEmpty())throw new ReglaNegocioException(empty);if(v.length()>max)throw new ReglaNegocioException(tooLong);return v;}
    private static String detail(OrdenCompra o){return "{\"numero\":\""+escape(o.getNumero())+"\",\"estado\":\""+o.getEstado()+"\",\"total\":"+o.getTotal()+"}";}
    private static String escape(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"");}
    private record LineValidated(UUID taxId,PurchaseOrderCalculationService.CalculatedLine value){}
    private record Validated(Proveedor supplier,Sucursal branch,Moneda currency,CondicionPago term,List<LineValidated> lines){}
}
