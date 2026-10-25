package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class FacturacionPosService {
    private static final int SCALE=4;
    private static final int LIMITE_CLIENTES_POS=5;
    private final TerminalPosRepository terminales; private final CashRegisterRepository cajas;
    private final CashRegisterSessionRepository sesiones; private final SucursalRepository sucursales;
    private final PosWarehouseResolver almacenesPos; private final ProductRepository productos; private final ClienteRepository clientes;
    private final InventoryMovementRepository existencias; private final FacturaVentaRepository facturas;
    private final LineaFacturaVentaRepository lineas; private final PagoVentaRepository pagos;
    private final MetodoPagoVentaRepository metodosPago; private final AplicacionPagoVentaRepository aplicacionesPago;
    private final BankAccountRepository cuentasBancarias;
    private final FinancialMovementRepository movimientos; private final InventoryMovementService inventario;
    private final AuditoriaService auditoria; private final VentaGuardadaService ventasGuardadas;private final DescuentoVentaService descuentos;private final RecargoVentaService recargos; private final JdbcTemplate jdbc;private final IdempotentOperationService idempotency;private final CobroClienteRepository cuentasCobrar;
    @Autowired private CotizacionRepository cotizaciones;

    public FacturacionPosService(TerminalPosRepository terminales,CashRegisterRepository cajas,
            CashRegisterSessionRepository sesiones,SucursalRepository sucursales,PosWarehouseResolver almacenesPos,
            ProductRepository productos,ClienteRepository clientes,InventoryMovementRepository existencias,
            FacturaVentaRepository facturas,LineaFacturaVentaRepository lineas,PagoVentaRepository pagos,
            MetodoPagoVentaRepository metodosPago,AplicacionPagoVentaRepository aplicacionesPago,BankAccountRepository cuentasBancarias,
            FinancialMovementRepository movimientos,InventoryMovementService inventario,AuditoriaService auditoria,
            VentaGuardadaService ventasGuardadas,DescuentoVentaService descuentos,RecargoVentaService recargos,JdbcTemplate jdbc,IdempotentOperationService idempotency,CobroClienteRepository cuentasCobrar){
        this.terminales=terminales;this.cajas=cajas;this.sesiones=sesiones;this.sucursales=sucursales;this.almacenesPos=almacenesPos;
        this.productos=productos;this.clientes=clientes;this.existencias=existencias;this.facturas=facturas;this.lineas=lineas;
        this.pagos=pagos;this.metodosPago=metodosPago;this.aplicacionesPago=aplicacionesPago;this.cuentasBancarias=cuentasBancarias;
        this.movimientos=movimientos;this.inventario=inventario;this.auditoria=auditoria;this.ventasGuardadas=ventasGuardadas;this.descuentos=descuentos;this.recargos=recargos;this.jdbc=jdbc;this.idempotency=idempotency;this.cuentasCobrar=cuentasCobrar;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.configurar_terminal')")
    public List<CajaPosOpcionDto> cajasDisponibles(){
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        return cajas.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),empresa).stream()
            .filter(c->EmpresaContext.permiteSucursal(c.getSucursalId())&&c.getSucursal().isActivo())
            .map(c->new CajaPosOpcionDto(c.getId(),c.getNombre(),c.getSucursalId(),c.getSucursal().getNombre(),c.getMoneda().getCodigoIso())).toList();
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.configurar_terminal')")
    public TerminalPosConfiguradaDto configurarTerminal(ConfigurarTerminalPosInput input){
        if(input==null||input.cajaId()==null)throw new ReglaNegocioException("Selecciona una caja.");
        String nombre=input.nombre()==null?"":input.nombre().trim();
        if(nombre.isEmpty()||nombre.length()>120)throw new ReglaNegocioException("Indica un nombre válido para la terminal.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        Caja caja=cajas.findByIdAndTenantIdAndEmpresaId(input.cajaId(),p.tenantId(),empresa)
            .orElseThrow(()->new RecursoNoEncontradoException("Caja no encontrada."));
        if(!caja.isActivo()||!EmpresaContext.permiteSucursal(caja.getSucursalId()))throw new RecursoNoEncontradoException("Caja no encontrada.");
        String token=nuevoToken();TerminalPos terminal=terminales.saveAndFlush(new TerminalPos(p.tenantId(),empresa,
            caja.getSucursalId(),caja.getId(),hash(token),nombre,p.usuarioId()));
        auditoria.registrar("POS_TERMINAL_CONFIGURED","TerminalPos",terminal.getId(),"{\"cajaId\":\""+caja.getId()+"\"}");
        Sucursal sucursal=sucursales.findByIdAndEmpresaId(caja.getSucursalId(),empresa)
            .orElseThrow(()->new ReglaNegocioException("La sucursal configurada para esta caja no está disponible."));
        return new TerminalPosConfiguradaDto(token,toDto(terminal,caja,almacenesPos.resolver(p.tenantId(),empresa,sucursal)));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.ver')")
    public Optional<TerminalPosDto> terminal(String token){
        if(token==null||token.isBlank())return Optional.empty();
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        return terminales.findByTokenHashAndTenantIdAndEmpresaIdAndActivoTrue(hash(token),p.tenantId(),empresa)
            .filter(t->EmpresaContext.permiteSucursal(t.getSucursalId())).map(t->{
                Caja caja=cajas.findByIdAndTenantIdAndEmpresaId(t.getCajaId(),p.tenantId(),empresa)
                    .orElseThrow(()->new RecursoNoEncontradoException("La caja configurada ya no existe."));
                Sucursal sucursal=sucursales.findByIdAndEmpresaId(caja.getSucursalId(),empresa)
                    .orElseThrow(()->new ReglaNegocioException("La sucursal configurada para esta caja no está disponible."));
                Almacen almacen=almacenesPos.resolver(p.tenantId(),empresa,sucursal);
                return toDto(t,caja,almacen);
            });
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.crear')")
    public BusquedaClientesPosDto buscarClientes(String texto){
        var p=TenantContext.principalActual();String q=limpiar(texto);
        var resultado=clientes.buscarParaPos(p.tenantId(),EmpresaContext.requerirEmpresaId(),q,soloDigitos(q),soloDigitos(q),
            PageRequest.of(0,LIMITE_CLIENTES_POS));
        List<ClientePosDto> encontrados=resultado.getContent().stream().map(c->new ClientePosDto(c.getId(),c.getCodigo(),nombreCliente(c),
            c.getTipoIdentificacion(),c.getIdentificacion(),c.getTelefono())).toList();
        return new BusquedaClientesPosDto(encontrados,resultado.hasNext());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.crear')")
    public List<ProductoPosDto> buscarProductos(String token,UUID almacenId,String texto,int offset,int limite){
        ContextoPos ctx=contexto(token,false,almacenId);String q=limpiar(texto);
        int size=Math.max(1,Math.min(limite,100));int page=Math.max(0,offset)/size;
        return productos.buscarParaVenta(ctx.principal.tenantId(),ctx.empresa,ctx.caja.getMonedaId(),q,PageRequest.of(page,size)).stream()
            .map(p->new ProductoPosDto(p.getId(),p.getCodigo(),p.getNombre(),p.getCodigoBarras(),p.getUnidadMedida().getAbreviatura(),
                p.getPrecioVenta(),p.getImpuestoVenta()!=null&&p.getImpuestoVenta().isActivo()?p.getImpuestoVenta().getPorcentaje():BigDecimal.ZERO,
                p.isControlaExistencia()?existencias.saldo(ctx.principal.tenantId(),ctx.empresa,ctx.almacen.getId(),p.getId()):BigDecimal.ZERO,
                p.isControlaExistencia(),p.isPermiteExistenciaNegativa(),p.isPermiteDescuentos(),p.isPermiteRecargos())).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.crear')")
    public Page<ProductoPosDto> buscarProductosPagina(String token,UUID almacenId,String texto,int pagina){
        if(pagina<0)throw new ReglaNegocioException("La página solicitada no es válida.");
        ContextoPos ctx=contexto(token,false,almacenId);String q=limpiar(texto);
        return productos.buscarParaVenta(ctx.principal.tenantId(),ctx.empresa,ctx.caja.getMonedaId(),q,
                PageRequest.of(pagina,PosPaginationPolicy.DIALOG_DEFAULT_PAGE_SIZE,
                        Sort.by("nombre").and(Sort.by("codigo")).and(Sort.by("id"))))
            .map(p->new ProductoPosDto(p.getId(),p.getCodigo(),p.getNombre(),p.getCodigoBarras(),p.getUnidadMedida().getAbreviatura(),
                p.getPrecioVenta(),p.getImpuestoVenta()!=null&&p.getImpuestoVenta().isActivo()?p.getImpuestoVenta().getPorcentaje():BigDecimal.ZERO,
                p.isControlaExistencia()?existencias.saldo(ctx.principal.tenantId(),ctx.empresa,ctx.almacen.getId(),p.getId()):BigDecimal.ZERO,
                p.isControlaExistencia(),p.isPermiteExistenciaNegativa(),p.isPermiteDescuentos(),p.isPermiteRecargos()));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.crear')")
    public void validarCantidad(String token,UUID almacenId,UUID productoId,BigDecimal cantidad){
        if(productoId==null||cantidad==null||cantidad.signum()<=0)
            throw new ReglaNegocioException("La cantidad debe ser mayor que cero.");
        ContextoPos ctx=contexto(token,false,almacenId);
        Producto producto=productos.findByIdAndTenantIdAndEmpresaId(productoId,ctx.principal.tenantId(),ctx.empresa)
            .filter(Producto::isActivo).orElseThrow(()->new RecursoNoEncontradoException("Producto no encontrado."));
        if(!ctx.caja.getMonedaId().equals(producto.getMonedaId()))
            throw new ReglaNegocioException("El producto utiliza una moneda distinta a la caja.");
        if(producto.isControlaExistencia()&&!producto.isPermiteExistenciaNegativa()){
            BigDecimal disponible=existencias.saldo(ctx.principal.tenantId(),ctx.empresa,ctx.almacen.getId(),producto.getId());
            if(disponible.compareTo(escala(cantidad))<0)
                throw new ReglaNegocioException("Existencia insuficiente para "+producto.getNombre()+".");
        }
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.cobrar')")
    public List<MetodoPagoPosDto> metodosPago(){
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        return metodosPago.findAllByTenantIdAndEmpresaIdAndActiveTrueOrderByDisplayOrderAscNameAsc(p.tenantId(),empresa).stream()
            .filter(m->m.getType()!=TipoMetodoPagoVenta.BANK_TRANSFER||p.permisos().contains("facturas.cobrar_transferencia"))
            .filter(m->m.getType()!=TipoMetodoPagoVenta.CHECK||p.permisos().contains("facturas.cobrar_cheque"))
            .map(m->new MetodoPagoPosDto(m.getId(),m.getCode(),m.getName(),m.getType(),m.isRequiresReference(),
                m.isRequiresBankAccount(),m.isAllowsChange(),m.isCash(),m.isElectronic(),m.getIcon(),m.isDefaultMethod())).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.cobrar_transferencia')")
    public List<CuentaBancariaPosDto> cuentasBancarias(){
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        return cuentasBancarias.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByBancoNombreAscNombreCuentaAsc(p.tenantId(),empresa).stream()
            .map(c->new CuentaBancariaPosDto(c.getId(),c.getBancoNombre()+" · "+c.getNombreCuenta()+" · •••• "+c.getNumeroCuentaUltimos4(),
                c.getMoneda()==null?"":c.getMoneda().getCodigoIso())).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.cobrar')")
    public CreditoVentaPosDto creditoDisponible(String token,UUID almacenId,UUID clienteId,BigDecimal total){
        if(clienteId==null)return null;
        var p=TenantContext.principalActual();
        if(!p.permisos().contains("facturas.facturar_credito"))return null;
        ContextoPos ctx=contexto(token,false,almacenId);
        Cliente cliente=clientes.findByIdAndTenantIdAndEmpresaId(clienteId,p.tenantId(),ctx.empresa).filter(Cliente::isActivo).orElse(null);
        if(cliente==null||cliente.getCondicionPago()==null||!cliente.getCondicionPago().isActivo()
                ||cliente.getCondicionPago().getTipo()!=TipoCondicionPago.CREDIT||cliente.getDiasCredito()<=0
                ||!ctx.caja.getMonedaId().equals(cliente.getMonedaId()))return null;
        BigDecimal factura=escala(total);BigDecimal saldo=escala(cuentasCobrar.saldoPendiente(p.tenantId(),ctx.empresa,cliente.getId(),ctx.caja.getMonedaId()));
        BigDecimal proyectado=saldo.add(factura);BigDecimal limite=escala(cliente.getLimiteCredito());
        boolean sinLimite=cliente.getControlLimite()==ControlLimiteCredito.NO_CONTROLAR&&limite.signum()==0;
        boolean excede=cliente.getControlLimite()!=ControlLimiteCredito.NO_CONTROLAR
                &&proyectado.compareTo(limite)>0;
        LocalDate fecha=ctx.sesion==null?LocalDate.now():ctx.sesion.getFechaOperativa();
        return new CreditoVentaPosDto(cliente.getId(),nombreCliente(cliente),cliente.getIdentificacion(),cliente.getDiasCredito(),
                fecha,fecha.plusDays(cliente.getDiasCredito()),sinLimite?null:limite,saldo,factura,proyectado,
                cliente.getControlLimite(),excede,p.permisos().contains("facturas.exceder_limite_credito"));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.cobrar')")
    public FacturaVentaResultadoDto confirmar(ConfirmarVentaInput input){
        if(input==null||input.idempotencia()==null)throw new ReglaNegocioException("No fue posible identificar la operación de cobro.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();String operation="SALES_INVOICE_CONFIRM";
        var start=idempotency.begin(p.tenantId(),empresa,operation,input.idempotencia(),fingerprint(input));
        if(start.replay())return resultado(facturas.findByIdAndTenantIdAndEmpresaId(start.resultId(),p.tenantId(),empresa).orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada.")));
        Optional<FacturaVenta> existente=facturas.findByTenantIdAndEmpresaIdAndClaveIdempotencia(p.tenantId(),empresa,input.idempotencia());
        if(existente.isPresent()){idempotency.complete(p.tenantId(),empresa,operation,input.idempotencia(),"FacturaVenta",existente.get().getId());return resultado(existente.get());}
        if(input.almacenId()==null)throw new ReglaNegocioException("No fue posible identificar el almacén de la venta.");
        ContextoPos ctx=contexto(input.token(),true,input.almacenId());
        existente=facturas.findByTenantIdAndEmpresaIdAndClaveIdempotencia(p.tenantId(),empresa,input.idempotencia());
        if(existente.isPresent()){idempotency.complete(p.tenantId(),empresa,operation,input.idempotencia(),"FacturaVenta",existente.get().getId());return resultado(existente.get());}
        if(input.lineas()==null||input.lineas().isEmpty())throw new ReglaNegocioException("Agrega al menos un producto o servicio.");
        if(input.lineas().size()>200)throw new ReglaNegocioException("La factura excede el máximo de 200 líneas.");
        Cotizacion cotizacionOrigen=null;Map<UUID,LineaCotizacion> lineasCotizacion=new HashMap<>();
        if(input.cotizacionOrigenId()!=null){
            cotizacionOrigen=cotizaciones.bloquear(input.cotizacionOrigenId(),p.tenantId(),empresa)
                .orElseThrow(()->new RecursoNoEncontradoException("Cotización no encontrada."));
            if(cotizacionOrigen.getEstado()!=EstadoCotizacion.ENVIADA&&cotizacionOrigen.getEstado()!=EstadoCotizacion.ACEPTADA)
                throw new ReglaNegocioException("La cotización ya no está disponible para facturar.");
            if(!cotizacionOrigen.getMonedaId().equals(ctx.caja.getMonedaId()))
                throw new ReglaNegocioException("La moneda de la cotización no coincide con la moneda de la caja.");
            cotizacionOrigen.getLineas().forEach(l->lineasCotizacion.put(l.getId(),l));
        }
        Set<UUID> ids=new HashSet<>();List<CalculoLinea> calculosBase=new ArrayList<>();List<LineaVistaPreviaDescuentoInput> lineasDescuento=new ArrayList<>();
        Map<UUID,Producto> productosBloqueados=new HashMap<>();
        input.lineas().stream().filter(Objects::nonNull).map(LineaVentaInput::productoId).filter(Objects::nonNull).distinct()
            .sorted(Comparator.comparing(UUID::toString)).forEach(id->productosBloqueados.put(id,productos.bloquear(id,p.tenantId(),empresa)
                .orElseThrow(()->new RecursoNoEncontradoException("Producto no encontrado."))));
        for(LineaVentaInput entrada:input.lineas()){
            if(entrada==null||entrada.productoId()==null||entrada.cantidad()==null||entrada.cantidad().signum()<=0)
                throw new ReglaNegocioException("Todas las cantidades deben ser mayores que cero.");
            if(!ids.add(entrada.productoId()))throw new ReglaNegocioException("El producto ya existe en la factura.");
            BigDecimal cantidad=entrada.cantidad().setScale(SCALE,RoundingMode.HALF_UP);
            Producto producto=productosBloqueados.get(entrada.productoId());
            if(!producto.isActivo()||producto.getPrecioVenta()==null)throw new ReglaNegocioException("El producto "+producto.getNombre()+" no está disponible para venta.");
            if(!ctx.caja.getMonedaId().equals(producto.getMonedaId()))throw new ReglaNegocioException("El producto "+producto.getNombre()+" utiliza una moneda distinta a la caja.");
            LineaCotizacion lineaCotizada=input.cotizacionOrigenId()==null?null:lineasCotizacion.get(entrada.cotizacionLineaOrigenId());
            if(entrada.cotizacionLineaOrigenId()!=null&&lineaCotizada==null)
                throw new ReglaNegocioException("Una línea ya no pertenece a la cotización seleccionada.");
            if(lineaCotizada!=null&&!lineaCotizada.getProductoId().equals(producto.getId()))
                throw new ReglaNegocioException("El producto no coincide con la línea cotizada.");
            if(lineaCotizada!=null){BigDecimal convertida=jdbc.queryForObject("select coalesce(sum(converted_quantity),0) from sales_quote_line_conversion where tenant_id=? and empresa_id=? and sales_quote_line_id=?",BigDecimal.class,p.tenantId(),empresa,lineaCotizada.getId());BigDecimal pendiente=lineaCotizada.getCantidad().subtract(convertida==null?BigDecimal.ZERO:convertida);if(cantidad.compareTo(pendiente)>0)throw new ReglaNegocioException("La cantidad de "+producto.getNombre()+" supera lo pendiente de la cotización.");}
            BigDecimal precio=(lineaCotizada==null?producto.getPrecioVenta():lineaCotizada.getPrecioUnitario()).setScale(SCALE,RoundingMode.HALF_UP);
            if(entrada.precioEsperado()==null||precio.compareTo(entrada.precioEsperado().setScale(SCALE,RoundingMode.HALF_UP))!=0)
                throw new ReglaNegocioException("El precio de "+producto.getNombre()+" cambió. Actualiza la venta antes de cobrar.");
            Impuesto tax=producto.getImpuestoVenta()!=null&&producto.getImpuestoVenta().isActivo()?producto.getImpuestoVenta():null;
            BigDecimal tasa=tax==null?BigDecimal.ZERO:tax.getPorcentaje();
            BigDecimal bruto=precio.multiply(cantidad).setScale(SCALE,RoundingMode.HALF_UP);
            if(producto.isControlaExistencia()&&!producto.isPermiteExistenciaNegativa()){
                BigDecimal disponible=existencias.saldo(p.tenantId(),empresa,ctx.almacen.getId(),producto.getId());
                if(disponible.compareTo(cantidad)<0)throw new ReglaNegocioException("Existencia insuficiente para "+producto.getNombre()+".");
            }
            UUID lineaId=entrada.lineaId()==null?producto.getId():entrada.lineaId();
            calculosBase.add(new CalculoLinea(lineaId,producto,cantidad,precio,bruto,BigDecimal.ZERO,BigDecimal.ZERO,bruto,tax,BigDecimal.ZERO,BigDecimal.ZERO,entrada.cotizacionLineaOrigenId()));
            lineasDescuento.add(new LineaVistaPreviaDescuentoInput(lineaId,producto.getId(),producto.getNombre(),cantidad,precio,tasa,producto.isPermiteDescuentos()));
        }
        DescuentoVentaService.ValidacionConfirmacion descuentoValidado=descuentos.validarConfirmacion(input.idempotencia(),input.descuento(),lineasDescuento);
        Map<UUID,VistaPreviaDescuentoDto.Asignacion> asignaciones=new HashMap<>();
        descuentoValidado.vista().asignaciones().forEach(x->asignaciones.put(x.lineaId(),x));
        if(cotizacionOrigen!=null)for(LineaVentaInput entrada:input.lineas()){
            CalculoLinea base=calculosBase.stream().filter(c->c.lineaId.equals(entrada.lineaId())).findFirst().orElseThrow();
            BigDecimal d=escala(entrada.descuentoCotizado());if(d.compareTo(base.subtotalBruto)>0)throw new ReglaNegocioException("El descuento cotizado supera el subtotal de "+base.producto.getNombre()+".");
            BigDecimal bi=base.subtotalBruto.subtract(d);BigDecimal tax=bi.multiply(base.impuesto==null?BigDecimal.ZERO:base.impuesto.getPorcentaje()).divide(new BigDecimal("100"),SCALE,RoundingMode.HALF_UP);
            asignaciones.put(base.lineaId,new VistaPreviaDescuentoDto.Asignacion(base.lineaId,base.producto.getId(),base.subtotalBruto,d,bi,tax,bi.add(tax)));
        }
        List<LineaVistaPreviaRecargoInput> lineasRecargo=new ArrayList<>();
        for(CalculoLinea base:calculosBase){VistaPreviaDescuentoDto.Asignacion a=asignaciones.get(base.lineaId);lineasRecargo.add(new LineaVistaPreviaRecargoInput(base.lineaId,base.producto.getId(),base.producto.getNombre(),base.cantidad,base.precio,base.impuesto==null?BigDecimal.ZERO:base.impuesto.getPorcentaje(),a==null?BigDecimal.ZERO:a.descuento(),base.producto.isPermiteRecargos()));}
        RecargoVentaService.ValidacionConfirmacion recargoValidado=recargos.validarConfirmacion(input.idempotencia(),input.recargo(),lineasRecargo);
        Map<UUID,VistaPreviaRecargoDto.Asignacion> asignacionesRecargo=new HashMap<>();recargoValidado.vista().asignaciones().forEach(x->asignacionesRecargo.put(x.lineaId(),x));
        List<CalculoLinea> calculos=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO,descuentoTotal=BigDecimal.ZERO,recargoTotal=BigDecimal.ZERO,impuestos=BigDecimal.ZERO,total=BigDecimal.ZERO;
        for(CalculoLinea base:calculosBase){VistaPreviaDescuentoDto.Asignacion a=asignaciones.get(base.lineaId);VistaPreviaRecargoDto.Asignacion r=asignacionesRecargo.get(base.lineaId);if(a==null||r==null)throw new ReglaNegocioException("No fue posible reconciliar los ajustes de la venta.");CalculoLinea c=new CalculoLinea(base.lineaId,base.producto,base.cantidad,base.precio,a.subtotalBruto(),a.descuento(),r.recargo(),r.baseImponible(),base.impuesto,r.impuesto(),r.total(),base.cotizacionLineaOrigenId);calculos.add(c);subtotal=subtotal.add(c.subtotalBruto);descuentoTotal=descuentoTotal.add(c.descuento);recargoTotal=recargoTotal.add(c.recargo);impuestos=impuestos.add(c.impuestoMonto);total=total.add(c.total);}
        Cliente cliente;LocalDate vencimiento=null;boolean excesoCreditoAutorizado=false;
        List<AplicacionValidada> cobros;BigDecimal aplicado,cambio,recibido;
        if(input.ventaCredito()){
            if(!p.permisos().contains("facturas.facturar_credito"))throw new ReglaNegocioException("No tienes permiso para facturar a crédito.");
            if(input.clienteId()==null)throw new ReglaNegocioException("Selecciona un cliente habilitado para crédito.");
            cliente=clientes.bloquear(input.clienteId(),p.tenantId(),empresa).filter(Cliente::isActivo)
                .orElseThrow(()->new ReglaNegocioException("El cliente seleccionado no está disponible."));
            excesoCreditoAutorizado=validarClienteCredito(cliente,ctx,total,p.permisos());
            if(input.aplicaciones()!=null&&!input.aplicaciones().isEmpty())throw new ReglaNegocioException("Una factura a crédito no puede incluir pagos inmediatos.");
            vencimiento=ctx.sesion.getFechaOperativa().plusDays(cliente.getDiasCredito());
            cobros=List.of();aplicado=BigDecimal.ZERO.setScale(SCALE);cambio=BigDecimal.ZERO.setScale(SCALE);recibido=BigDecimal.ZERO.setScale(SCALE);
        }else{
            cliente=input.clienteId()==null?null:clientes.findByIdAndTenantIdAndEmpresaId(input.clienteId(),p.tenantId(),empresa)
                .filter(Cliente::isActivo).orElseThrow(()->new RecursoNoEncontradoException("Cliente no encontrado."));
            cobros=validarAplicaciones(input.aplicaciones(),total,ctx,p.permisos());
            aplicado=CobroVentaCalculo.totalAplicado(cobros.stream().map(AplicacionValidada::monto).toList());
            cambio=CobroVentaCalculo.totalAplicado(cobros.stream().map(AplicacionValidada::cambio).toList());
            recibido=total.add(cambio);
        }
        String numero=siguienteNumero(p.tenantId(),empresa);
        FacturaVenta factura=new FacturaVenta(p.tenantId(),empresa,numero,ctx.sesion.getFechaOperativa(),p.usuarioId(),
            ctx.terminal.getId(),ctx.caja.getId(),ctx.sesion.getId(),ctx.caja.getSucursalId(),ctx.almacen.getId(),
            cliente==null?null:cliente.getId(),cliente==null?"Consumidor Final":nombreCliente(cliente),cliente==null?null:cliente.getIdentificacion(),
            p.nombre(),ctx.caja.getMonedaId(),ctx.caja.getMoneda().getCodigoIso(),ctx.caja.getMoneda().getSimbolo(),nota(input.nota()),
            subtotal,descuentoTotal,impuestos,total,input.ventaCredito()?total:recibido,input.idempotencia());
        factura.aplicarRecargo(recargoTotal);
        if(input.ventaCredito())factura.marcarCredito(cliente.getCondicionPagoId(),cliente.getDiasCredito(),vencimiento);
        factura=facturas.saveAndFlush(factura);
        if(input.ventaCredito())cuentasCobrar.crearCuenta(p.tenantId(),empresa,factura.getId(),cliente.getId(),ctx.caja.getMonedaId(),total,vencimiento);
        int orden=1;
        for(CalculoLinea c:calculos){
            DescuentoVentaInput descriptor=descuentoValidado.descuento();
            LineaFacturaVenta linea=lineas.saveAndFlush(new LineaFacturaVenta(factura,c.producto,ctx.almacen.getId(),orden++,c.cantidad,
                c.precio,c.subtotalBruto,c.descuento,c.baseImponible,c.impuesto,c.impuestoMonto,c.total,
                c.descuento.signum()>0&&descriptor!=null?descriptor.tipo():null,c.descuento.signum()>0&&descriptor!=null?descriptor.valor():null,
                c.descuento.signum()>0&&descuentoValidado.motivo()!=null?descuentoValidado.motivo().getId():null,
                c.descuento.signum()>0&&descuentoValidado.motivo()!=null?descuentoValidado.motivo().getNombre():null,
                c.descuento.signum()>0&&descriptor!=null?descriptor.detalle():null,c.descuento.signum()>0?descuentoValidado.autorizacionId():null,
                c.descuento.signum()>0?descuentoValidado.autorizadaPor():null,c.descuento.signum()>0?descuentoValidado.autorizadaEn():null));
            RecargoVentaInput descriptorRecargo=recargoValidado.recargo();if(c.recargo.signum()>0&&descriptorRecargo!=null)linea.aplicarRecargo(c.recargo,c.baseImponible,c.impuestoMonto,c.total,descriptorRecargo.tipo(),descriptorRecargo.valor(),recargoValidado.concepto(),recargoValidado.motivo(),descriptorRecargo.detalle());
            lineas.saveAndFlush(linea);
            inventario.vender(factura,linea,c.producto,p.usuarioId());
        }
        if(!input.ventaCredito()){
            PagoVenta pago=pagos.saveAndFlush(new PagoVenta(factura,aplicado,cambio,p.usuarioId()));
            int ordenPago=1;
            for(AplicacionValidada cobro:cobros){
                AplicacionPagoVenta aplicacion=aplicacionesPago.saveAndFlush(new AplicacionPagoVenta(pago,cobro.metodo,cobro.monto,
                    cobro.recibido,cobro.cambio,cobro.cuenta==null?null:cobro.cuenta.getId(),cobro.referencia,cobro.banco,ordenPago++));
                registrarMovimientoCobro(ctx,factura,aplicacion,cobro,p.usuarioId());
            }
        }
        auditoria.registrar("SALES_INVOICE_CONFIRMED","FacturaVenta",factura.getId(),"{\"numero\":\""+numero+"\",\"total\":"+total+
            ",\"descuento\":"+descuentoTotal+",\"recargo\":"+recargoTotal+",\"ventaCredito\":"+input.ventaCredito()+",\"aplicaciones\":"+cobros.size()+",\"mixto\":"+CobroVentaCalculo.mixto(cobros.size())+"}");
        if(excesoCreditoAutorizado)auditoria.registrar("SALES_CREDIT_LIMIT_OVERRIDE","FacturaVenta",factura.getId(),
                "{\"clienteId\":\""+cliente.getId()+"\",\"total\":"+total+"}");
        descuentos.consumirAutorizacion(descuentoValidado.autorizacionId(),factura.getId());
        recargos.consumirAutorizacion(recargoValidado.autorizacionId(),factura.getId());
        if(cotizacionOrigen!=null&&calculos.stream().anyMatch(c->c.cotizacionLineaOrigenId!=null))
            registrarConversionCotizacion(cotizacionOrigen,factura,calculos,p.usuarioId());
        ventasGuardadas.completarDesdeFactura(input.ventaGuardadaId(),factura.getId(),p.usuarioId(),ctx.terminal.getId(),p.tenantId(),empresa);
        idempotency.complete(p.tenantId(),empresa,operation,input.idempotencia(),"FacturaVenta",factura.getId());
        return resultado(factura);
    }

    private static String fingerprint(ConfirmarVentaInput i){return CommandFingerprint.sha256(new Object[]{i.token(),i.almacenId(),i.clienteId(),i.nota(),i.ventaGuardadaId(),i.descuento(),i.recargo(),i.cotizacionOrigenId(),i.ventaCredito(),i.lineas(),i.aplicaciones()});}

    private boolean validarClienteCredito(Cliente cliente,ContextoPos ctx,BigDecimal total,Set<String> permisos){
        if(cliente.getCondicionPago()==null||!cliente.getCondicionPago().isActivo()
                ||cliente.getCondicionPago().getTipo()!=TipoCondicionPago.CREDIT||cliente.getDiasCredito()<=0)
            throw new ReglaNegocioException("Las condiciones de crédito del cliente cambiaron. Actualice la información antes de continuar.");
        if(!ctx.caja.getMonedaId().equals(cliente.getMonedaId()))
            throw new ReglaNegocioException("La moneda configurada para el crédito del cliente no coincide con la venta.");
        BigDecimal limite=escala(cliente.getLimiteCredito());
        if(cliente.getControlLimite()==ControlLimiteCredito.NO_CONTROLAR)return false;
        BigDecimal saldo=escala(cuentasCobrar.saldoPendiente(ctx.principal.tenantId(),ctx.empresa,cliente.getId(),ctx.caja.getMonedaId()));
        if(saldo.add(escala(total)).compareTo(limite)<=0)return false;
        if(cliente.getControlLimite()==ControlLimiteCredito.BLOQUEAR)
            throw new ReglaNegocioException("El cliente excede el límite de crédito permitido.");
        if(cliente.getControlLimite()==ControlLimiteCredito.ADVERTIR&&!permisos.contains("facturas.exceder_limite_credito"))
            throw new ReglaNegocioException("El cliente excede su límite de crédito y no tienes permiso para autorizarlo.");
        return true;
    }

    private List<AplicacionValidada> validarAplicaciones(List<AplicacionCobroInput> entradas,BigDecimal total,ContextoPos ctx,Set<String> permisos){
        if(entradas==null||entradas.isEmpty())throw new ReglaNegocioException("Agrega al menos un método de pago.");
        if(entradas.size()>20)throw new ReglaNegocioException("El cobro excede el máximo de 20 aplicaciones.");
        List<AplicacionValidada> resultado=new ArrayList<>();int efectivo=0;
        for(AplicacionCobroInput entrada:entradas){
            if(entrada==null||entrada.metodoPagoId()==null)throw new ReglaNegocioException("Selecciona un método de pago válido.");
            MetodoPagoVenta metodo=metodosPago.bloquearActivo(entrada.metodoPagoId(),ctx.principal.tenantId(),ctx.empresa)
                .orElseThrow(()->new ReglaNegocioException("El método de pago ya no está disponible."));
            if(metodo.getType()==TipoMetodoPagoVenta.BANK_TRANSFER&&!permisos.contains("facturas.cobrar_transferencia"))
                throw new ReglaNegocioException("No tienes permiso para cobrar mediante transferencia.");
            if(metodo.getType()==TipoMetodoPagoVenta.CHECK&&!permisos.contains("facturas.cobrar_cheque"))
                throw new ReglaNegocioException("No tienes permiso para cobrar mediante cheque.");
            BigDecimal monto=escala(entrada.monto());
            if(monto.signum()<=0)throw new ReglaNegocioException("Todos los montos aplicados deben ser mayores que cero.");
            String referencia=texto(entrada.referencia(),100);String banco=texto(entrada.banco(),120);
            if(metodo.isRequiresReference()&&referencia==null)throw new ReglaNegocioException("Indica la referencia de "+metodo.getName()+".");
            CuentaBancaria cuenta=null;
            if(entrada.cuentaBancariaId()!=null)cuenta=cuentasBancarias.findByIdAndTenantIdAndEmpresaId(entrada.cuentaBancariaId(),ctx.principal.tenantId(),ctx.empresa)
                .filter(CuentaBancaria::isActivo).orElseThrow(()->new ReglaNegocioException("La cuenta bancaria ya no está disponible."));
            if(cuenta==null&&metodo.getFinancialBankAccountId()!=null)cuenta=cuentasBancarias.findByIdAndTenantIdAndEmpresaId(
                metodo.getFinancialBankAccountId(),ctx.principal.tenantId(),ctx.empresa).filter(CuentaBancaria::isActivo)
                .orElseThrow(()->new ReglaNegocioException("La cuenta financiera configurada para "+metodo.getName()+" ya no está disponible."));
            if(metodo.isRequiresBankAccount()&&cuenta==null)throw new ReglaNegocioException("Selecciona la cuenta destino.");
            if(cuenta!=null&&!ctx.caja.getMonedaId().equals(cuenta.getMonedaId()))throw new ReglaNegocioException("La cuenta destino utiliza una moneda distinta a la venta.");
            BigDecimal recibido=null,cambio=BigDecimal.ZERO.setScale(SCALE);
            if(metodo.isCash()){
                if(++efectivo>1)throw new ReglaNegocioException("El efectivo debe registrarse en una sola aplicación.");
                recibido=escala(entrada.recibido()==null?monto:entrada.recibido());
                if(recibido.compareTo(monto)<0)throw new ReglaNegocioException("El efectivo recibido no puede ser menor que el monto aplicado.");
                cambio=CobroVentaCalculo.cambio(monto,recibido);
            }else if(entrada.recibido()!=null&&escala(entrada.recibido()).compareTo(monto)!=0){
                throw new ReglaNegocioException("Solo el efectivo puede generar cambio.");
            }
            resultado.add(new AplicacionValidada(metodo,monto,recibido,cambio,cuenta,referencia,banco));
        }
        BigDecimal aplicado=CobroVentaCalculo.totalAplicado(resultado.stream().map(AplicacionValidada::monto).toList());
        int comparacion=aplicado.compareTo(total.setScale(SCALE,RoundingMode.HALF_UP));
        if(comparacion<0)throw new ReglaNegocioException("El cobro está incompleto. Queda un saldo pendiente de "+total.subtract(aplicado).setScale(2,RoundingMode.HALF_UP)+".");
        if(comparacion>0)throw new ReglaNegocioException("El total aplicado no puede superar el total de la venta.");
        return resultado;
    }

    private void registrarConversionCotizacion(Cotizacion cotizacion,FacturaVenta factura,List<CalculoLinea> calculos,UUID usuario){
        UUID enlace=UUID.randomUUID();
        jdbc.update("insert into sales_quote_document_link(id,tenant_id,empresa_id,sales_quote_id,document_type,document_id,document_number,document_status,created_by) values(?,?,?,?,?,?,?,?,?)",
            enlace,factura.getTenantId(),factura.getEmpresaId(),cotizacion.getId(),"SALES_INVOICE",factura.getId(),factura.getNumero(),"CONFIRMED",usuario);
        for(CalculoLinea linea:calculos)if(linea.cotizacionLineaOrigenId!=null)jdbc.update(
            "insert into sales_quote_line_conversion(id,tenant_id,empresa_id,sales_quote_line_id,document_link_id,converted_quantity) values(?,?,?,?,?,?)",
            UUID.randomUUID(),factura.getTenantId(),factura.getEmpresaId(),linea.cotizacionLineaOrigenId,enlace,linea.cantidad);
        boolean completa=cotizacion.getLineas().stream().allMatch(l->{BigDecimal cantidad=jdbc.queryForObject(
            "select coalesce(sum(converted_quantity),0) from sales_quote_line_conversion where tenant_id=? and empresa_id=? and sales_quote_line_id=?",
            BigDecimal.class,factura.getTenantId(),factura.getEmpresaId(),l.getId());return cantidad!=null&&cantidad.compareTo(l.getCantidad())>=0;});
        if(completa){cotizacion.marcarConvertida(usuario);cotizacion.registrarEvento("CONVERTIDA","Cotización convertida completamente en factura "+factura.getNumero()+".",usuario);cotizaciones.saveAndFlush(cotizacion);}
        auditoria.registrar(completa?"SALES_QUOTE_CONVERTED":"SALES_QUOTE_PARTIALLY_CONVERTED","Cotizacion",cotizacion.getId(),"{\"facturaId\":\""+factura.getId()+"\"}");
    }

    private void registrarMovimientoCobro(ContextoPos ctx,FacturaVenta factura,AplicacionPagoVenta aplicacion,AplicacionValidada cobro,UUID usuario){
        TipoCuentaDinero tipoCuenta;UUID cuentaId;MedioPagoMovimiento medio;
        if(cobro.metodo.isCash()){tipoCuenta=TipoCuentaDinero.CASH_REGISTER;cuentaId=ctx.caja.getId();medio=MedioPagoMovimiento.CASH;}
        else if(cobro.cuenta!=null){tipoCuenta=TipoCuentaDinero.BANK_ACCOUNT;cuentaId=cobro.cuenta.getId();medio=MedioPagoMovimiento.BANK_TRANSFER;}
        else return;
        movimientos.save(MovimientoFinanciero.cobroVenta(ctx.principal.tenantId(),ctx.empresa,factura.getFecha(),tipoCuenta,cuentaId,
            ctx.caja.getMonedaId(),cobro.monto,factura.getNumero(),aplicacion.getId(),medio,ctx.sesion.getId(),cobro.referencia,usuario));
    }
    private static BigDecimal escala(BigDecimal valor){return (valor==null?BigDecimal.ZERO:valor).setScale(SCALE,RoundingMode.HALF_UP);}
    private static String texto(String valor,int max){String s=limpiar(valor);if(s.length()>max)throw new ReglaNegocioException("El dato ingresado excede el máximo permitido.");return s.isEmpty()?null:s;}

    private ContextoPos contexto(String token,boolean bloquear,UUID almacenEsperado){
        if(token==null||token.isBlank())throw new ReglaNegocioException("Esta terminal no está configurada.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        TerminalPos terminal=terminales.findByTokenHashAndTenantIdAndEmpresaIdAndActivoTrue(hash(token),p.tenantId(),empresa)
            .orElseThrow(()->new ReglaNegocioException("Esta terminal no está configurada o fue desactivada."));
        if(!EmpresaContext.permiteSucursal(terminal.getSucursalId()))throw new RecursoNoEncontradoException("Terminal no encontrada.");
        if(bloquear)terminal=terminales.bloquear(terminal.getId(),p.tenantId(),empresa).orElseThrow();
        Caja caja=(bloquear?cajas.bloquearParaOperacion(terminal.getCajaId(),p.tenantId(),empresa):cajas.findByIdAndTenantIdAndEmpresaId(terminal.getCajaId(),p.tenantId(),empresa))
            .orElseThrow(()->new ReglaNegocioException("La caja configurada ya no está disponible."));
        if(!caja.isActivo())throw new ReglaNegocioException("La caja configurada está inactiva.");
        SesionCaja sesion=sesiones.findByTenantIdAndEmpresaIdAndCajaIdAndStatus(p.tenantId(),empresa,caja.getId(),EstadoSesionCaja.OPEN).orElse(null);
        if(bloquear){
            if(sesion==null)throw new ReglaNegocioException("La caja está cerrada. Debes abrirla antes de cobrar.");
            sesion=sesiones.bloquear(sesion.getId(),p.tenantId(),empresa).orElseThrow();
            if(!p.usuarioId().equals(sesion.getAbiertoPor()))throw new ReglaNegocioException("La caja está abierta por otro usuario.");
        }
        Sucursal sucursal=sucursales.findByIdAndEmpresaId(caja.getSucursalId(),empresa)
            .orElseThrow(()->new ReglaNegocioException("La sucursal configurada para esta caja no está disponible."));
        Almacen almacen=almacenesPos.resolver(p.tenantId(),empresa,sucursal);
        if(almacenEsperado!=null&&!almacenEsperado.equals(almacen.getId()))
            throw new ReglaNegocioException("La configuración del almacén cambió. Reinicia la venta antes de continuar.");
        return new ContextoPos(p,empresa,terminal,caja,sesion,almacen);
    }

    private TerminalPosDto toDto(TerminalPos t,Caja c,Almacen almacen){
        SesionCaja s=sesiones.findByTenantIdAndEmpresaIdAndCajaIdAndStatus(t.getTenantId(),t.getEmpresaId(),c.getId(),EstadoSesionCaja.OPEN).orElse(null);
        return new TerminalPosDto(t.getId(),t.getNombre(),c.getSucursalId(),c.getSucursal().getNombre(),c.getId(),c.getNombre(),
            almacen.getId(),almacen.getNombre(),c.getMoneda().getCodigoIso(),s!=null,s==null?null:s.getId(),s==null?null:s.getFechaOperativa());
    }
    private String siguienteNumero(UUID tenant,UUID empresa){
        Long n=jdbc.queryForObject("INSERT INTO sales_invoice_sequence(tenant_id,empresa_id,last_number) VALUES(?,?,1) "+
            "ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET last_number=sales_invoice_sequence.last_number+1 RETURNING last_number",
            Long.class,tenant,empresa);return "FAC-"+String.format(Locale.ROOT,"%06d",n);
    }
    private static FacturaVentaResultadoDto resultado(FacturaVenta f){return new FacturaVentaResultadoDto(f.getId(),f.getNumero(),f.getTotal(),f.getRecibido(),f.getCambio());}
    private static String nombreCliente(Cliente c){return c.getTipoPersona()==TipoPersonaCliente.JURIDICA?c.getRazonSocial():
        (limpiar(c.getNombres())+" "+limpiar(c.getApellidos())).trim();}
    private static String nota(String v){String s=limpiar(v);if(s.length()>1000)throw new ReglaNegocioException("La nota excede 1,000 caracteres.");return s.isEmpty()?null:s;}
    private static String limpiar(String v){return v==null?"":v.trim();}
    private static String soloDigitos(String v){return v==null?"":v.replaceAll("\\D","");}
    private static String nuevoToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(b);}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private record ContextoPos(com.citacloud.springboot.contacloud.app.security.TenantPrincipal principal,UUID empresa,TerminalPos terminal,Caja caja,SesionCaja sesion,Almacen almacen){}
    private record CalculoLinea(UUID lineaId,Producto producto,BigDecimal cantidad,BigDecimal precio,BigDecimal subtotalBruto,BigDecimal descuento,BigDecimal recargo,BigDecimal baseImponible,Impuesto impuesto,BigDecimal impuestoMonto,BigDecimal total,UUID cotizacionLineaOrigenId){}
    private record AplicacionValidada(MetodoPagoVenta metodo,BigDecimal monto,BigDecimal recibido,BigDecimal cambio,
                                      CuentaBancaria cuenta,String referencia,String banco){}
}
