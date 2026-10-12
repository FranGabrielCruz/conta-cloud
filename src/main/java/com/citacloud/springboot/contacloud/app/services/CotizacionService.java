package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.CotizacionMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class CotizacionService {
    private static final int DIAS_VIGENCIA=15;
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final CotizacionRepository cotizaciones; private final ClienteRepository clientes;
    private final DireccionClienteRepository direcciones; private final SucursalRepository sucursales;
    private final MonedaRepository monedas; private final ImpuestoRepository impuestos; private final ProductRepository productos;
    private final CotizacionCalculoService calculos; private final CotizacionNumeroService numeros; private final CotizacionMapper mapper;
    private final AuditoriaService auditoria; private final EmpresaRepository empresas; private final DatosEmpresaRepository datosEmpresa;
    private final UsuarioRepository usuarios; private final LocalFileStorageService almacenamiento; private final CotizacionPdfService pdf;

    public CotizacionService(CotizacionRepository cotizaciones,ClienteRepository clientes,DireccionClienteRepository direcciones,
            SucursalRepository sucursales,MonedaRepository monedas,ImpuestoRepository impuestos,ProductRepository productos,
            CotizacionCalculoService calculos,CotizacionNumeroService numeros,CotizacionMapper mapper,AuditoriaService auditoria,
            EmpresaRepository empresas,DatosEmpresaRepository datosEmpresa,UsuarioRepository usuarios,
            LocalFileStorageService almacenamiento,CotizacionPdfService pdf){
        this.cotizaciones=cotizaciones;this.clientes=clientes;this.direcciones=direcciones;this.sucursales=sucursales;
        this.monedas=monedas;this.impuestos=impuestos;this.productos=productos;this.calculos=calculos;this.numeros=numeros;
        this.mapper=mapper;this.auditoria=auditoria;this.empresas=empresas;this.datosEmpresa=datosEmpresa;this.usuarios=usuarios;
        this.almacenamiento=almacenamiento;this.pdf=pdf;
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.ver')")
    public Page<CotizacionDto> buscar(String texto,LocalDate desde,LocalDate hasta,EstadoCotizacion estado,UUID clienteId,
            UUID monedaId,int pagina,int tamano){
        if(pagina<0||!PAGE_SIZES.contains(tamano))throw new ReglaNegocioException("Paginación inválida.");
        if(desde!=null&&hasta!=null&&hasta.isBefore(desde))throw new ReglaNegocioException("La fecha final no puede ser anterior a la fecha inicial.");
        var p=TenantContext.principalActual();cotizaciones.marcarVencidas(p.tenantId(),p.empresaId(),LocalDate.now(),OffsetDateTime.now());
        return cotizaciones.buscar(p.tenantId(),p.empresaId(),limpiar(texto),desde,hasta,estado,clienteId,monedaId,
            PageRequest.of(pagina,tamano,Sort.by(Sort.Direction.DESC,"fecha").and(Sort.by(Sort.Direction.DESC,"creadaEn"))))
            .map(mapper::toSummaryDto);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.ver')")
    public CotizacionDto obtener(UUID id){var p=TenantContext.principalActual();cotizaciones.marcarVencidas(p.tenantId(),p.empresaId(),LocalDate.now(),OffsetDateTime.now());return mapper.toDto(segura(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAnyAuthority('cotizaciones.ver','cotizaciones.crear','cotizaciones.editar')")
    public CotizacionCatalogosDto catalogos(){var p=TenantContext.principalActual();UUID empresa=p.empresaId();
        var branches=sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresa).stream().filter(s->EmpresaContext.permiteSucursal(s.getId()))
            .map(s->new CotizacionCatalogosDto.SucursalOpcion(s.getId(),s.getNombre())).toList();
        var currencies=monedas.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(empresa).stream()
            .map(m->new CotizacionCatalogosDto.MonedaOpcion(m.getId(),m.getCodigoIso(),m.getNombre(),m.getSimbolo())).toList();
        var taxes=impuestos.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),empresa).stream()
            .map(t->new CotizacionCatalogosDto.ImpuestoOpcion(t.getId(),t.getNombre(),t.getPorcentaje())).toList();
        UUID base=monedas.findByEmpresaIdAndMonedaBaseTrue(empresa).filter(Moneda::isActivo).map(Moneda::getId).orElse(null);
        return new CotizacionCatalogosDto(branches,currencies,taxes,base,DIAS_VIGENCIA);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAnyAuthority('cotizaciones.ver','cotizaciones.crear','cotizaciones.editar')")
    public List<CotizacionCatalogosDto.ClienteOpcion> buscarClientes(String filtro,int offset,int limit){
        if(offset<0||limit<1||limit>50)throw new ReglaNegocioException("Paginación de clientes inválida.");var p=TenantContext.principalActual();String text=limpiar(filtro);
        return clientes.buscar(p.tenantId(),p.empresaId(),text,normalizar(text),normalizar(text),true,
            PageRequest.of(offset/limit,limit,Sort.by("razonSocial").and(Sort.by("nombres"))))
            .stream().map(CotizacionService::opcionCliente).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAnyAuthority('cotizaciones.ver','cotizaciones.crear','cotizaciones.editar')")
    public CotizacionCatalogosDto.ClienteOpcion clienteOpcion(UUID id){var p=TenantContext.principalActual();return opcionCliente(clienteActivo(id,p.tenantId(),p.empresaId()));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAnyAuthority('cotizaciones.crear','cotizaciones.editar')")
    public List<CotizacionCatalogosDto.ProductoOpcion> buscarProductos(String filtro,int offset,int limit){
        if(offset<0||limit<1||limit>50)throw new ReglaNegocioException("Paginación de productos inválida.");var p=TenantContext.principalActual();
        return productos.buscar(p.tenantId(),p.empresaId(),limpiar(filtro),null,null,true,
            PageRequest.of(offset/limit,limit,Sort.by("nombre"))).stream().map(CotizacionService::opcionProducto).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAnyAuthority('cotizaciones.crear','cotizaciones.editar')")
    public CotizacionCatalogosDto.ProductoOpcion productoOpcion(UUID id){var p=TenantContext.principalActual();return opcionProducto(productoActivo(id,p.tenantId(),p.empresaId()));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.crear')")
    public CotizacionDto crear(CotizacionInput input){var p=TenantContext.principalActual();Validada v=validar(input,p.tenantId(),p.empresaId(),null);
        SnapshotCliente snapshot=snapshot(v.cliente(),p.tenantId(),p.empresaId());Cotizacion c=new Cotizacion(p.tenantId(),p.empresaId(),
            numeros.siguiente(p.tenantId(),p.empresaId()),v.cliente().getId(),v.sucursal().getId(),input.fecha(),input.validaHasta(),
            v.moneda().getId(),snapshot.nombre(),snapshot.identificacion(),snapshot.direccion(),snapshot.telefono(),snapshot.correo(),
            opcional(input.referencia(),100,"La referencia excede 100 caracteres."),opcional(input.condiciones(),2000,"Las condiciones exceden 2000 caracteres."),p.usuarioId(),null);
        aplicarLineas(c,v,p);c.registrarEvento("CREADA","Cotización creada en borrador.",p.usuarioId());c=cotizaciones.saveAndFlush(c);
        auditoria.registrar("SALES_QUOTE_CREATED","Cotizacion",c.getId(),detalle(c));return mapper.toDto(c);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.editar')")
    public CotizacionDto actualizar(UUID id,CotizacionInput input){Cotizacion c=bloqueada(id);esperada(c,input==null?null:input.version());borrador(c);
        var p=TenantContext.principalActual();Validada v=validar(input,p.tenantId(),p.empresaId(),c);SnapshotCliente snapshot=snapshot(v.cliente(),p.tenantId(),p.empresaId());
        registrarCambios(c,input,p.usuarioId());
        c.actualizar(v.cliente().getId(),v.sucursal().getId(),input.fecha(),input.validaHasta(),v.moneda().getId(),snapshot.nombre(),
            snapshot.identificacion(),snapshot.direccion(),snapshot.telefono(),snapshot.correo(),opcional(input.referencia(),100,"La referencia excede 100 caracteres."),
            opcional(input.condiciones(),2000,"Las condiciones exceden 2000 caracteres."),p.usuarioId());aplicarLineas(c,v,p);
        c.registrarEvento("EDITADA","Cotización actualizada.",p.usuarioId());
        try{c=cotizaciones.saveAndFlush(c);}catch(OptimisticLockingFailureException ex){throw concurrencia(ex);}
        auditoria.registrar("SALES_QUOTE_UPDATED","Cotizacion",c.getId(),detalle(c));return mapper.toDto(c);
    }

    private void registrarCambios(Cotizacion c,CotizacionInput input,UUID usuario){
        if(!Objects.equals(c.getClienteId(),input.clienteId())){c.registrarEvento("CLIENTE_MODIFICADO","Se cambió el cliente de la cotización.",usuario);auditoria.registrar("SALES_QUOTE_CUSTOMER_CHANGED","Cotizacion",c.getId(),"{}");}
        Map<UUID,LineaCotizacion> anteriores=new HashMap<>();for(LineaCotizacion l:c.getLineas())anteriores.put(l.getProductoId(),l);
        boolean precio=false,descuento=false,impuesto=false;for(LineaCotizacionInput nueva:input.lineas()){LineaCotizacion anterior=anteriores.get(nueva.productoId());if(anterior==null)continue;
            if(comparar(anterior.getPrecioUnitario(),nueva.precioUnitario())!=0)precio=true;
            TipoDescuentoCotizacion tipo=nueva.tipoDescuento()==null?TipoDescuentoCotizacion.MONTO:nueva.tipoDescuento();
            if(anterior.getTipoDescuento()!=tipo||comparar(anterior.getValorDescuento(),nueva.valorDescuento())!=0)descuento=true;
            if(!Objects.equals(anterior.getImpuestoId(),nueva.impuestoId()))impuesto=true;
        }
        if(precio){c.registrarEvento("PRECIO_MODIFICADO","Se modificaron precios de la cotización.",usuario);auditoria.registrar("SALES_QUOTE_PRICE_CHANGED","Cotizacion",c.getId(),"{}");}
        if(descuento){c.registrarEvento("DESCUENTO_MODIFICADO","Se modificaron descuentos de la cotización.",usuario);auditoria.registrar("SALES_QUOTE_DISCOUNT_CHANGED","Cotizacion",c.getId(),"{}");}
        if(impuesto){c.registrarEvento("IMPUESTO_MODIFICADO","Se modificaron impuestos de la cotización.",usuario);auditoria.registrar("SALES_QUOTE_TAX_CHANGED","Cotizacion",c.getId(),"{}");}
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.enviar')")
    public CotizacionDto enviar(UUID id,long version){Cotizacion c=bloqueada(id);esperada(c,version);borrador(c);validarVigencia(c);
        if(c.getLineas().isEmpty())throw new ReglaNegocioException("La cotización debe tener al menos una línea.");UUID user=TenantContext.principalActual().usuarioId();
        c.enviar(user);c.registrarEvento("ENVIADA","Cotización marcada como enviada.",user);c=cotizaciones.saveAndFlush(c);
        auditoria.registrar("SALES_QUOTE_SENT","Cotizacion",c.getId(),detalle(c));return mapper.toDto(c);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.aceptar')")
    public CotizacionDto aceptar(UUID id,long version,String nota){Cotizacion c=bloqueada(id);esperada(c,version);estado(c,EstadoCotizacion.ENVIADA,"Solo se pueden aceptar cotizaciones enviadas.");validarVigencia(c);
        UUID user=TenantContext.principalActual().usuarioId();String clean=opcional(nota,500,"La nota excede 500 caracteres.");c.aceptar(clean,user);
        c.registrarEvento("ACEPTADA",clean==null?"Cotización aceptada.":clean,user);c=cotizaciones.saveAndFlush(c);
        auditoria.registrar("SALES_QUOTE_ACCEPTED","Cotizacion",c.getId(),detalle(c));return mapper.toDto(c);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.rechazar')")
    public CotizacionDto rechazar(UUID id,long version,String motivo){Cotizacion c=bloqueada(id);esperada(c,version);estado(c,EstadoCotizacion.ENVIADA,"Solo se pueden rechazar cotizaciones enviadas.");
        String clean=requerido(motivo,500,"El motivo de rechazo es obligatorio.","El motivo de rechazo excede 500 caracteres.");UUID user=TenantContext.principalActual().usuarioId();
        c.rechazar(clean,user);c.registrarEvento("RECHAZADA",clean,user);c=cotizaciones.saveAndFlush(c);
        auditoria.registrar("SALES_QUOTE_REJECTED","Cotizacion",c.getId(),detalle(c));return mapper.toDto(c);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.anular')")
    public CotizacionDto anular(UUID id,long version,String motivo){Cotizacion c=bloqueada(id);esperada(c,version);
        if(c.getEstado()==EstadoCotizacion.ANULADA||c.getEstado()==EstadoCotizacion.CONVERTIDA)throw new ReglaNegocioException("La cotización no puede anularse en su estado actual.");
        String clean=requerido(motivo,500,"El motivo de anulación es obligatorio.","El motivo de anulación excede 500 caracteres.");UUID user=TenantContext.principalActual().usuarioId();
        c.anular(clean,user);c.registrarEvento("ANULADA",clean,user);c=cotizaciones.saveAndFlush(c);
        auditoria.registrar("SALES_QUOTE_VOIDED","Cotizacion",c.getId(),detalle(c));return mapper.toDto(c);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.duplicar')")
    public CotizacionDto duplicar(UUID id){Cotizacion original=segura(id);var p=TenantContext.principalActual();LocalDate hoy=LocalDate.now();
        Cliente cliente=clienteActivo(original.getClienteId(),p.tenantId(),p.empresaId());Sucursal sucursal=sucursales.findByIdAndEmpresaId(original.getSucursalId(),p.empresaId())
            .filter(Sucursal::isActivo).filter(s->EmpresaContext.permiteSucursal(s.getId())).orElseThrow(()->new ReglaNegocioException("La sucursal original ya no está disponible."));
        Moneda moneda=monedas.findByIdAndEmpresaId(original.getMonedaId(),p.empresaId()).filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("La moneda original ya no está disponible."));
        Cotizacion nueva=new Cotizacion(p.tenantId(),p.empresaId(),numeros.siguiente(p.tenantId(),p.empresaId()),cliente.getId(),sucursal.getId(),hoy,
            hoy.plusDays(DIAS_VIGENCIA),moneda.getId(),original.getClienteNombre(),original.getClienteIdentificacion(),original.getClienteDireccion(),
            original.getClienteTelefono(),original.getClienteCorreo(),original.getReferencia(),original.getCondiciones(),p.usuarioId(),original.getId());
        List<LineaCotizacion> lines=new ArrayList<>();int n=1;for(LineaCotizacion l:original.getLineas())lines.add(copiarLinea(l,p,n++));
        nueva.reemplazarLineas(lines);nueva.totales(original.getSubtotal(),original.getDescuento(),original.getImpuesto(),original.getTotal());
        nueva.registrarEvento("DUPLICADA","Creada desde "+original.getNumero()+".",p.usuarioId());nueva=cotizaciones.saveAndFlush(nueva);
        auditoria.registrar("SALES_QUOTE_DUPLICATED","Cotizacion",nueva.getId(),"{\"origen\":\""+escape(original.getNumero())+"\"}");return mapper.toDto(nueva);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.convertir')")
    public UUID prepararFactura(UUID id,long version){Cotizacion c=bloqueada(id);esperada(c,version);estado(c,EstadoCotizacion.ACEPTADA,"Solo una cotización aceptada puede convertirse en factura.");
        if(c.getLineas().isEmpty())throw new ReglaNegocioException("La cotización no tiene líneas disponibles para convertir.");
        auditoria.registrar("SALES_QUOTE_CONVERSION_STARTED","Cotizacion",c.getId(),detalle(c));return c.getId();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('cotizaciones.imprimir')")
    public byte[] generarPdf(UUID id){Cotizacion c=segura(id);return pdf.generar(datosReporte(c));}

    private Validada validar(CotizacionInput input,UUID tenant,UUID empresa,Cotizacion existente){
        if(input==null)throw new ReglaNegocioException("Los datos de la cotización son obligatorios.");
        if(input.fecha()==null)throw new ReglaNegocioException("La fecha es obligatoria.");
        if(input.validaHasta()==null)throw new ReglaNegocioException("La fecha de vigencia es obligatoria.");
        if(input.validaHasta().isBefore(input.fecha()))throw new ReglaNegocioException("La vigencia no puede finalizar antes de la fecha de la cotización.");
        Cliente cliente=clienteActivo(input.clienteId(),tenant,empresa);Sucursal sucursal=sucursales.findByIdAndEmpresaId(input.sucursalId(),empresa)
            .filter(Sucursal::isActivo).orElseThrow(()->new ReglaNegocioException("La sucursal seleccionada no está disponible."));
        if(!EmpresaContext.permiteSucursal(sucursal.getId()))throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");
        Moneda moneda=monedas.findByIdAndEmpresaId(input.monedaId(),empresa).filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("La moneda seleccionada no está disponible."));
        if(input.lineas()==null||input.lineas().isEmpty())throw new ReglaNegocioException("Agrega al menos una línea a la cotización.");
        Set<UUID> ids=new HashSet<>();List<LineaValidada> lines=new ArrayList<>();for(LineaCotizacionInput line:input.lineas()){
            if(line==null||line.productoId()==null)throw new ReglaNegocioException("Selecciona un producto o servicio en cada línea.");
            if(!ids.add(line.productoId()))throw new ReglaNegocioException("El producto ya existe en la cotización.");
            Producto product=productoActivo(line.productoId(),tenant,empresa);Impuesto tax=line.impuestoId()==null?null:impuestos.findByIdAndTenantIdAndEmpresaId(line.impuestoId(),tenant,empresa)
                .filter(Impuesto::isActivo).orElseThrow(()->new ReglaNegocioException("El impuesto seleccionado no está disponible."));
            validarPermisosComerciales(line,product,existente);var calc=calculos.calcular(line,tax==null?null:tax.getNombre(),tax==null?BigDecimal.ZERO:tax.getPorcentaje());
            lines.add(new LineaValidada(product,tax,calc));
        }
        return new Validada(cliente,sucursal,moneda,lines);
    }

    private void validarPermisosComerciales(LineaCotizacionInput line,Producto product,Cotizacion existente){Set<String> permisos=TenantContext.principalActual().permisos();
        LineaCotizacion almacenada=existente==null?null:existente.getLineas().stream().filter(l->Objects.equals(l.getProductoId(),line.productoId())).findFirst().orElse(null);
        BigDecimal base=almacenada==null?(product.getPrecioVenta()==null?BigDecimal.ZERO:product.getPrecioVenta()):almacenada.getPrecioUnitario();
        BigDecimal price=line.precioUnitario()==null?BigDecimal.ZERO:line.precioUnitario();
        if(price.compareTo(base)!=0&&!permisos.contains("cotizaciones.precios"))throw new ReglaNegocioException("No tienes permiso para modificar precios.");
        TipoDescuentoCotizacion tipo=line.tipoDescuento()==null?TipoDescuentoCotizacion.MONTO:line.tipoDescuento();
        boolean descuentoCambiado=almacenada==null?(line.valorDescuento()!=null&&line.valorDescuento().signum()>0)
            :almacenada.getTipoDescuento()!=tipo||comparar(almacenada.getValorDescuento(),line.valorDescuento())!=0;
        if(descuentoCambiado&&!permisos.contains("cotizaciones.descuentos"))throw new ReglaNegocioException("No tienes permiso para modificar descuentos.");
    }

    private void aplicarLineas(Cotizacion c,Validada v,TenantPrincipal p){List<LineaCotizacion> result=new ArrayList<>();int n=1;
        for(var line:v.lineas()){Producto product=line.producto();UnidadMedida unit=product.getUnidadMedida();var x=line.calculada();result.add(new LineaCotizacion(
            p.tenantId(),p.empresaId(),n++,product.getId(),product.getCodigo(),unit==null?null:unit.getId(),unit==null?null:unit.getNombre(),
            unit==null?null:unit.getAbreviatura(),x.descripcion(),x.cantidad(),x.precioUnitario(),x.tipoDescuento(),x.valorDescuento(),x.descuento(),
            line.impuesto()==null?null:line.impuesto().getId(),x.impuestoNombre(),x.tasaImpuesto(),x.subtotalBruto(),x.baseImponible(),x.impuesto(),x.total()));}
        var totals=calculos.totales(v.lineas().stream().map(LineaValidada::calculada).toList());c.reemplazarLineas(result);
        c.totales(totals.subtotal(),totals.descuento(),totals.impuesto(),totals.total());
    }

    private CotizacionReportData datosReporte(Cotizacion c){Empresa company=empresas.findByIdAndTenantId(c.getEmpresaId(),c.getTenantId())
            .orElseThrow(()->new ReglaNegocioException("No fue posible identificar la empresa."));DatosEmpresa data=datosEmpresa.findByEmpresaId(c.getEmpresaId()).orElse(null);
        var p=TenantContext.principalActual();Usuario user=usuarios.findByIdAndTenantId(p.usuarioId(),p.tenantId()).orElseThrow(()->new ReglaNegocioException("No fue posible identificar al usuario."));
        byte[] logo=company.getLogoObjectKey()==null?null:almacenamiento.load(company.getLogoObjectKey(),5L*1024*1024).orElse(null);
        List<CotizacionReportLine> lines=c.getLineas().stream().map(l->new CotizacionReportLine(l.getNumeroLinea(),l.getDescripcion(),unidad(l),
            l.getCantidad(),l.getPrecioUnitario(),l.getDescuento(),l.getImpuesto(),l.getTotal())).toList();
        return new CotizacionReportData(c.getTenantId(),c.getEmpresaId(),c.getId(),c.getNumero(),data==null?company.getNombre():primero(data.getNombreComercial(),company.getNombre()),
            data==null?company.getNombre():primero(data.getRazonSocial(),company.getNombre()),company.getIdentificacionFiscal(),data==null?null:data.getDireccion(),
            data==null?null:data.getTelefono(),data==null?null:data.getCorreo(),logo,c.getClienteNombre(),c.getClienteIdentificacion(),c.getClienteDireccion(),
            c.getClienteTelefono(),c.getClienteCorreo(),c.getFecha(),c.getValidaHasta(),c.getSucursal()==null?null:c.getSucursal().getNombre(),
            c.getMoneda()==null?null:c.getMoneda().getCodigoIso(),c.getReferencia(),c.getCondiciones(),c.getSubtotal(),c.getDescuento(),c.getImpuesto(),c.getTotal(),
            c.getEstado().name(),nombreUsuario(user),OffsetDateTime.now(),lines);
    }

    private SnapshotCliente snapshot(Cliente c,UUID tenant,UUID empresa){String direccion=direcciones.findFirstByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueAndPrincipalTrue(tenant,empresa,c.getId())
        .map(DireccionCliente::getDireccion).orElse(null);return new SnapshotCliente(nombreCliente(c),c.getIdentificacion(),direccion,c.getTelefono(),c.getCorreo());}
    private Cliente clienteActivo(UUID id,UUID tenant,UUID empresa){if(id==null)throw new ReglaNegocioException("El cliente es obligatorio.");return clientes.findByIdAndTenantIdAndEmpresaId(id,tenant,empresa)
        .filter(Cliente::isActivo).orElseThrow(()->new ReglaNegocioException("El cliente seleccionado no está disponible."));}
    private Producto productoActivo(UUID id,UUID tenant,UUID empresa){return productos.findByIdAndTenantIdAndEmpresaId(id,tenant,empresa).filter(Producto::isActivo)
        .orElseThrow(()->new ReglaNegocioException("El producto o servicio seleccionado no está disponible."));}
    private Cotizacion segura(UUID id){if(id==null)throw new RecursoNoEncontradoException("Cotización no encontrada.");var p=TenantContext.principalActual();return cotizaciones.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
        .orElseThrow(()->new RecursoNoEncontradoException("Cotización no encontrada."));}
    private Cotizacion bloqueada(UUID id){if(id==null)throw new RecursoNoEncontradoException("Cotización no encontrada.");var p=TenantContext.principalActual();cotizaciones.marcarVencidas(p.tenantId(),p.empresaId(),LocalDate.now(),OffsetDateTime.now());
        return cotizaciones.bloquear(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Cotización no encontrada."));}
    private static void borrador(Cotizacion c){estado(c,EstadoCotizacion.BORRADOR,"Solo se pueden editar cotizaciones en borrador.");}
    private static void estado(Cotizacion c,EstadoCotizacion expected,String message){if(c.getEstado()!=expected)throw new ReglaNegocioException(message);}
    private static void validarVigencia(Cotizacion c){if(c.getValidaHasta().isBefore(LocalDate.now()))throw new ReglaNegocioException("La cotización está vencida.");}
    private static void esperada(Cotizacion c,Long version){if(version==null||c.getVersion()!=version)throw concurrencia(null);}
    private static ReglaNegocioException concurrencia(Exception ex){return new ReglaNegocioException("La cotización fue modificada por otro usuario. Actualiza la pantalla e inténtalo nuevamente.",ex);}
    private static LineaCotizacion copiarLinea(LineaCotizacion l,TenantPrincipal p,int n){return new LineaCotizacion(p.tenantId(),p.empresaId(),n,l.getProductoId(),l.getProductoCodigo(),l.getUnidadMedidaId(),l.getUnidadMedidaNombre(),l.getUnidadMedidaAbreviatura(),l.getDescripcion(),l.getCantidad(),l.getPrecioUnitario(),l.getTipoDescuento(),l.getValorDescuento(),l.getDescuento(),l.getImpuestoId(),l.getImpuestoNombre(),l.getTasaImpuesto(),l.getSubtotalBruto(),l.getBaseImponible(),l.getImpuesto(),l.getTotal());}
    private static CotizacionCatalogosDto.ClienteOpcion opcionCliente(Cliente c){return new CotizacionCatalogosDto.ClienteOpcion(c.getId(),c.getCodigo(),nombreCliente(c),c.getIdentificacion(),c.getMonedaId());}
    private static CotizacionCatalogosDto.ProductoOpcion opcionProducto(Producto p){UnidadMedida u=p.getUnidadMedida();Impuesto t=p.getImpuestoVenta();return new CotizacionCatalogosDto.ProductoOpcion(p.getId(),p.getCodigo(),p.getNombre(),p.getDescripcion(),u==null?null:u.getId(),u==null?null:u.getNombre(),u==null?null:u.getAbreviatura(),p.getPrecioVenta(),p.getMonedaId(),p.getImpuestoVentaId(),t==null?null:t.getNombre(),t==null?null:t.getPorcentaje());}
    private static String nombreCliente(Cliente c){if(c.getTipoPersona()==TipoPersonaCliente.FISICA)return limpiar((c.getNombres()==null?"":c.getNombres())+" "+(c.getApellidos()==null?"":c.getApellidos()));return primero(c.getNombreComercial(),c.getRazonSocial());}
    private static String nombreUsuario(Usuario u){String full=limpiar((u.getNombre()==null?"":u.getNombre())+" "+(u.getApellido()==null?"":u.getApellido()));return full.isBlank()?u.getUsuario():full;}
    private static int comparar(BigDecimal a,BigDecimal b){return (a==null?BigDecimal.ZERO:a).compareTo(b==null?BigDecimal.ZERO:b);}
    private static String unidad(LineaCotizacion l){String n=l.getUnidadMedidaNombre();String a=l.getUnidadMedidaAbreviatura();return n==null?"—":(a==null||a.isBlank()?n:n+" ("+a+")");}
    private static String primero(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static String normalizar(String value){return limpiar(value).replaceAll("[^\\p{Alnum}]","");}
    private static String limpiar(String value){return value==null?"":value.trim();}
    private static String opcional(String value,int max,String message){String v=limpiar(value);if(v.isEmpty())return null;if(v.length()>max)throw new ReglaNegocioException(message);return v;}
    private static String requerido(String value,int max,String empty,String tooLong){String v=limpiar(value);if(v.isEmpty())throw new ReglaNegocioException(empty);if(v.length()>max)throw new ReglaNegocioException(tooLong);return v;}
    private static String detalle(Cotizacion c){return "{\"numero\":\""+escape(c.getNumero())+"\",\"estado\":\""+c.getEstado()+"\",\"total\":"+c.getTotal()+"}";}
    private static String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    private record SnapshotCliente(String nombre,String identificacion,String direccion,String telefono,String correo) {}
    private record LineaValidada(Producto producto,Impuesto impuesto,CotizacionCalculoService.LineaCalculada calculada) {}
    private record Validada(Cliente cliente,Sucursal sucursal,Moneda moneda,List<LineaValidada> lineas) {}
}
