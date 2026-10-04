package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.FacturaProveedorMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

@Service
public class PurchaseInvoiceService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final PurchaseInvoiceRepository invoices; private final AccountsPayableRepository payables;
    private final SupplierRepository suppliers; private final SucursalRepository branches; private final MonedaRepository currencies;
    private final CondicionPagoRepository terms; private final ImpuestoRepository taxes; private final ProductRepository products;
    private final PurchaseOrderRepository orders; private final PurchaseOrderCalculationService calculations;
    private final FacturaProveedorMapper mapper; private final AuditoriaService audit;
    public PurchaseInvoiceService(PurchaseInvoiceRepository invoices,AccountsPayableRepository payables,SupplierRepository suppliers,
            SucursalRepository branches,MonedaRepository currencies,CondicionPagoRepository terms,ImpuestoRepository taxes,
            ProductRepository products,PurchaseOrderRepository orders,PurchaseOrderCalculationService calculations,
            FacturaProveedorMapper mapper,AuditoriaService audit){this.invoices=invoices;this.payables=payables;this.suppliers=suppliers;
        this.branches=branches;this.currencies=currencies;this.terms=terms;this.taxes=taxes;this.products=products;
        this.orders=orders;this.calculations=calculations;this.mapper=mapper;this.audit=audit;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.ver')")
    public Page<FacturaProveedorDto> search(String text,LocalDate from,LocalDate to,EstadoFacturaProveedor status,int page,int size){
        page(page,size);dates(from,to);var p=TenantContext.principalActual();return invoices.buscar(p.tenantId(),p.empresaId(),clean(text),from,to,status,
            PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"fecha"))).map(f->mapper.toSummaryDto(f,payable(f)));
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.ver')")
    public FacturaProveedorDto get(UUID id){FacturaProveedor f=safe(id);return mapper.toDto(f,payable(f));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('facturas_proveedores.crear','facturas_proveedores.editar')")
    public ComprasCatalogosDto catalogs(UUID proveedorId){var p=TenantContext.principalActual();UUID e=p.empresaId();
        var proveedores=suppliers.buscar(p.tenantId(),e,"","",true,PageRequest.of(0,100,Sort.by("nombreComercial"))).stream().map(x->opt(x.getId(),x.getNombreComercial())).toList();
        var sucursales=branches.findAllByEmpresaIdAndActivoTrueOrderByNombre(e).stream().filter(x->EmpresaContext.permiteSucursal(x.getId())).map(x->opt(x.getId(),x.getNombre())).toList();
        var monedas=currencies.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(e).stream().map(x->opt(x.getId(),x.getCodigoIso()+" · "+x.getNombre())).toList();
        UUID monedaBaseId=currencies.findByEmpresaIdAndMonedaBaseTrue(e).filter(Moneda::isActivo).map(Moneda::getId).orElse(null);
        var condiciones=terms.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),e).stream().map(x->opt(x.getId(),SupplierService.nombreCondicion(x))).toList();
        var ordenes=proveedorId==null?List.<ComprasCatalogosDto.Opcion>of():orders.disponibles(p.tenantId(),e,proveedorId,
            List.of(EstadoOrdenCompra.ISSUED,EstadoOrdenCompra.PARTIALLY_RECEIVED,EstadoOrdenCompra.RECEIVED)).stream().map(x->opt(x.getId(),x.getNumero())).toList();
        return new ComprasCatalogosDto(proveedores,sucursales,monedas,condiciones,List.of(),ordenes,monedaBaseId);
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('facturas_proveedores.crear','facturas_proveedores.editar')")
    public List<OrdenCompraCatalogosDto.ProductoOpcion> searchProducts(String filter,int offset,int limit){
        if(offset<0||limit<1||limit>50)throw new ReglaNegocioException("Paginación de productos inválida.");var p=TenantContext.principalActual();
        return products.buscar(p.tenantId(),p.empresaId(),clean(filter),null,null,true,PageRequest.of(offset/limit,limit,Sort.by("nombre"))).stream().map(PurchaseInvoiceService::productOption).toList();
    }
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('facturas_proveedores.crear','facturas_proveedores.editar')")
    public OrdenCompraCatalogosDto.ProductoOpcion productOption(UUID id){var p=TenantContext.principalActual();return productOption(products.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Producto no encontrado.")));}
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('facturas_proveedores.crear','facturas_proveedores.editar')")
    public List<OrdenCompraCatalogosDto.ImpuestoOpcion> taxOptions(){var p=TenantContext.principalActual();return taxes.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),p.empresaId()).stream().map(t->new OrdenCompraCatalogosDto.ImpuestoOpcion(t.getId(),t.getNombre(),t.getPorcentaje())).toList();}
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('facturas_proveedores.crear','facturas_proveedores.editar')")
    public List<LineaFacturaProveedorInput> linesFromOrder(UUID orderId){var p=TenantContext.principalActual();OrdenCompra o=orders.findByIdAndTenantIdAndEmpresaId(orderId,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Orden de compra no encontrada."));
        return o.getLineas().stream().map(l->new LineaFacturaProveedorInput(l.getProductoId(),l.getDescripcion(),l.getCantidad(),l.getPrecioUnitario(),l.getDescuento(),l.getImpuestoId())).toList();}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.crear')")
    public FacturaProveedorDto create(FacturaProveedorInput input){var p=TenantContext.principalActual();Validated v=validate(input,p.tenantId(),p.empresaId(),null);
        FacturaProveedor f=mapper.toEntity(cleanInput(input,v),p.tenantId(),p.empresaId(),v.normalizedNumber(),p.usuarioId());apply(f,v,p);f=invoices.saveAndFlush(f);
        audit.registrar("PURCHASE_INVOICE_CREATED","FacturaProveedor",f.getId(),detail(f));return mapper.toDto(f,null);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.crear') and hasAuthority('facturas_proveedores.registrar')")
    public FacturaProveedorDto createAndRegister(FacturaProveedorInput input){var p=TenantContext.principalActual();Validated v=validate(input,p.tenantId(),p.empresaId(),null);
        FacturaProveedor f=mapper.toEntity(cleanInput(input,v),p.tenantId(),p.empresaId(),v.normalizedNumber(),p.usuarioId());apply(f,v,p);f=invoices.saveAndFlush(f);
        audit.registrar("PURCHASE_INVOICE_CREATED","FacturaProveedor",f.getId(),detail(f));return registerNew(f,p);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.editar')")
    public FacturaProveedorDto update(UUID id,FacturaProveedorInput input){FacturaProveedor f=safe(id);draft(f);expected(f,input==null?null:input.version());var p=TenantContext.principalActual();Validated v=validate(input,p.tenantId(),p.empresaId(),id);
        var i=cleanInput(input,v);flushExistingLines(f);f.actualizar(i.proveedorId(),i.sucursalId(),i.numeroFactura(),v.normalizedNumber(),i.numeroFiscal(),i.fecha(),i.vencimiento(),i.condicionPagoId(),i.monedaId(),i.ordenCompraId(),i.referencia(),i.notas(),p.usuarioId());apply(f,v,p);f=invoices.saveAndFlush(f);
        audit.registrar("PURCHASE_INVOICE_UPDATED","FacturaProveedor",f.getId(),detail(f));return mapper.toDto(f,null);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.editar') and hasAuthority('facturas_proveedores.registrar')")
    public FacturaProveedorDto updateAndRegister(UUID id,FacturaProveedorInput input){FacturaProveedor f=locked(id);draft(f);expected(f,input==null?null:input.version());var p=TenantContext.principalActual();Validated v=validate(input,p.tenantId(),p.empresaId(),id);
        var i=cleanInput(input,v);flushExistingLines(f);f.actualizar(i.proveedorId(),i.sucursalId(),i.numeroFactura(),v.normalizedNumber(),i.numeroFiscal(),i.fecha(),i.vencimiento(),i.condicionPagoId(),i.monedaId(),i.ordenCompraId(),i.referencia(),i.notas(),p.usuarioId());apply(f,v,p);f=invoices.saveAndFlush(f);
        audit.registrar("PURCHASE_INVOICE_UPDATED","FacturaProveedor",f.getId(),detail(f));return registerNew(f,p);}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.registrar')")
    public FacturaProveedorDto register(UUID id,long version){FacturaProveedor f=locked(id);expected(f,version);
        if(f.getEstado()==EstadoFacturaProveedor.REGISTERED)return mapper.toDto(f,payable(f));draft(f);
        return registerNew(f,TenantContext.principalActual());}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('facturas_proveedores.anular')")
    public FacturaProveedorDto voidInvoice(UUID id,long version,String reason){FacturaProveedor f=locked(id);expected(f,version);
        if(f.getEstado()==EstadoFacturaProveedor.VOIDED)return mapper.toDto(f,payable(f));String r=required(reason,500,"El motivo de anulación es obligatorio.");var p=TenantContext.principalActual();
        CuentaPagar c=payable(f);if(c!=null&&c.getMontoAplicado().signum()>0)throw new ReglaNegocioException("No se puede anular una factura con pagos aplicados.");
        if(c!=null){c.anular();payables.save(c);}f.anular(r,p.usuarioId());f=invoices.saveAndFlush(f);audit.registrar("PURCHASE_INVOICE_VOIDED","FacturaProveedor",f.getId(),detail(f));return mapper.toDto(f,c);}

    private Validated validate(FacturaProveedorInput i,UUID tenant,UUID empresa,UUID current){if(i==null)throw new ReglaNegocioException("Los datos de la factura son obligatorios.");
        Proveedor supplier=suppliers.findByIdAndTenantIdAndEmpresaId(i.proveedorId(),tenant,empresa).filter(Proveedor::isActivo).orElseThrow(()->new ReglaNegocioException("El proveedor seleccionado no está disponible."));
        Sucursal branch=branches.findByIdAndEmpresaId(i.sucursalId(),empresa).filter(Sucursal::isActivo).orElseThrow(()->new ReglaNegocioException("La sucursal seleccionada no está disponible."));if(!EmpresaContext.permiteSucursal(branch.getId()))throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");
        Moneda currency=currencies.findByIdAndEmpresaId(i.monedaId(),empresa).filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("La moneda seleccionada no está disponible."));
        if(i.fecha()==null||i.vencimiento()==null)throw new ReglaNegocioException("Las fechas de factura y vencimiento son obligatorias.");if(i.vencimiento().isBefore(i.fecha()))throw new ReglaNegocioException("La fecha de vencimiento no puede ser anterior a la factura.");
        CondicionPago term=i.condicionPagoId()==null?null:terms.findByIdAndTenantIdAndEmpresaId(i.condicionPagoId(),tenant,empresa).filter(CondicionPago::isActivo).orElseThrow(()->new ReglaNegocioException("La condición de pago no está disponible."));
        OrdenCompra order=i.ordenCompraId()==null?null:orders.findByIdAndTenantIdAndEmpresaId(i.ordenCompraId(),tenant,empresa).filter(o->o.getProveedorId().equals(supplier.getId())).orElseThrow(()->new ReglaNegocioException("La orden no pertenece al proveedor seleccionado."));
        String number=required(i.numeroFactura(),100,"El número de factura del proveedor es obligatorio.");String normalized=normalize(number);if(normalized.isEmpty())throw new ReglaNegocioException("El número de factura debe contener letras o números.");
        boolean duplicate=current==null?invoices.existsByTenantIdAndEmpresaIdAndProveedorIdAndNumeroNormalizado(tenant,empresa,supplier.getId(),normalized):invoices.existsByTenantIdAndEmpresaIdAndProveedorIdAndNumeroNormalizadoAndIdNot(tenant,empresa,supplier.getId(),normalized,current);
        if(duplicate)throw new ReglaNegocioException("Ya existe una factura con ese número para el proveedor.");if(i.lineas()==null||i.lineas().isEmpty())throw new ReglaNegocioException("Agrega al menos una línea a la factura.");
        Set<UUID> unique=new HashSet<>();List<ValidLine> lines=new ArrayList<>();for(var l:i.lineas()){if(l==null||l.productoId()==null)throw new ReglaNegocioException("Selecciona un producto o servicio en cada línea.");if(!unique.add(l.productoId()))throw new ReglaNegocioException("El producto ya existe en la factura.");
            Producto product=products.findByIdAndTenantIdAndEmpresaId(l.productoId(),tenant,empresa).filter(Producto::isActivo).orElseThrow(()->new ReglaNegocioException("El producto seleccionado no está disponible."));
            Impuesto tax=l.impuestoId()==null?null:taxes.findByIdAndTenantIdAndEmpresaId(l.impuestoId(),tenant,empresa).filter(Impuesto::isActivo).orElseThrow(()->new ReglaNegocioException("El impuesto seleccionado no está disponible."));
            var c=calculations.calculate(new LineaOrdenCompraInput(l.descripcion(),l.cantidad(),l.precioUnitario(),l.descuento(),l.impuestoId()),tax==null?null:tax.getNombre(),tax==null?BigDecimal.ZERO:tax.getPorcentaje());lines.add(new ValidLine(product,tax,c));}
        return new Validated(supplier,branch,currency,term,order,normalized,lines);
    }
    private void apply(FacturaProveedor f,Validated v,TenantPrincipal p){List<LineaFacturaProveedor> result=new ArrayList<>();int n=1;for(var x:v.lines()){var c=x.calculated();var u=x.product().getUnidadMedida();result.add(new LineaFacturaProveedor(p.tenantId(),p.empresaId(),x.product().getId(),x.product().getCodigo(),c.description(),u==null?null:u.getNombre(),c.quantity(),c.unitPrice(),c.discount(),x.tax()==null?null:x.tax().getId(),c.taxName(),c.taxRate(),c.taxAmount(),c.grossSubtotal(),c.total(),n++));}var totals=calculations.totals(v.lines().stream().map(ValidLine::calculated).toList());f.reemplazarLineas(result);f.totales(totals.subtotal(),totals.discount(),totals.tax(),totals.total());}
    private void flushExistingLines(FacturaProveedor f){if(f.getId()==null||f.getLineas().isEmpty())return;f.reemplazarLineas(List.of());invoices.flush();}
    private FacturaProveedorDto registerNew(FacturaProveedor f,TenantPrincipal p){if(f.getLineas().isEmpty())throw new ReglaNegocioException("La factura debe tener al menos una línea.");f.registrar(p.usuarioId());f=invoices.saveAndFlush(f);CuentaPagar c=payables.saveAndFlush(new CuentaPagar(f.getTenantId(),f.getEmpresaId(),f.getId(),f.getProveedorId(),f.getMonedaId(),f.getVencimiento(),f.getTotal(),p.usuarioId()));audit.registrar("PURCHASE_INVOICE_REGISTERED","FacturaProveedor",f.getId(),detail(f));return mapper.toDto(f,c);}
    private FacturaProveedorInput cleanInput(FacturaProveedorInput i,Validated v){return new FacturaProveedorInput(i.proveedorId(),i.sucursalId(),required(i.numeroFactura(),100,"El número de factura es obligatorio."),optional(i.numeroFiscal(),50),i.fecha(),i.vencimiento(),i.condicionPagoId(),i.monedaId(),i.ordenCompraId(),optional(i.referencia(),100),optional(i.notas(),1000),i.lineas(),i.version());}
    private FacturaProveedor safe(UUID id){var p=TenantContext.principalActual();return invoices.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Factura de proveedor no encontrada."));}
    private FacturaProveedor locked(UUID id){var p=TenantContext.principalActual();return invoices.bloquear(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Factura de proveedor no encontrada."));}
    private CuentaPagar payable(FacturaProveedor f){return f.getId()==null?null:payables.findByFacturaIdAndTenantIdAndEmpresaId(f.getId(),f.getTenantId(),f.getEmpresaId()).orElse(null);}
    private static void draft(FacturaProveedor f){if(f.getEstado()!=EstadoFacturaProveedor.DRAFT)throw new ReglaNegocioException("Solo se pueden editar facturas en borrador.");}
    private static void expected(FacturaProveedor f,Long version){if(version==null||f.getVersion()!=version)throw new ReglaNegocioException("La factura fue modificada por otro usuario. Actualiza la pantalla.");}
    private static void page(int page,int size){if(page<0||!PAGE_SIZES.contains(size))throw new ReglaNegocioException("Paginación inválida.");}
    private static void dates(LocalDate a,LocalDate b){if(a!=null&&b!=null&&b.isBefore(a))throw new ReglaNegocioException("La fecha final no puede ser anterior a la inicial.");}
    private static ComprasCatalogosDto.Opcion opt(UUID id,String name){return new ComprasCatalogosDto.Opcion(id,name);}
    private static OrdenCompraCatalogosDto.ProductoOpcion productOption(Producto p){return new OrdenCompraCatalogosDto.ProductoOpcion(p.getId(),p.getCodigo(),p.getNombre(),p.getUnidadMedida()==null?null:p.getUnidadMedida().getNombre(),p.getCostoCompra(),p.getMonedaId(),p.getImpuestoCompraId(),p.getImpuestoCompra()==null?null:p.getImpuestoCompra().getNombre(),p.getImpuestoCompra()==null?null:p.getImpuestoCompra().getPorcentaje());}
    private static String normalize(String value){String n=Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","");return n.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);}
    private static String clean(String s){return s==null?"":s.trim();}private static String required(String s,int max,String msg){String v=clean(s);if(v.isEmpty())throw new ReglaNegocioException(msg);if(v.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return v;}private static String optional(String s,int max){String v=clean(s);if(v.isEmpty())return null;if(v.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return v;}
    private static String detail(FacturaProveedor f){return "{\"numero\":\""+f.getNumeroProveedor().replace("\"","\\\"")+"\",\"estado\":\""+f.getEstado()+"\",\"total\":"+f.getTotal()+"}";}
    private record ValidLine(Producto product,Impuesto tax,PurchaseOrderCalculationService.CalculatedLine calculated){}
    private record Validated(Proveedor supplier,Sucursal branch,Moneda currency,CondicionPago term,OrdenCompra order,String normalizedNumber,List<ValidLine> lines){}
}
