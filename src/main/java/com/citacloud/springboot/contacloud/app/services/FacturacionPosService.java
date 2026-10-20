package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
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
    private final AuditoriaService auditoria; private final VentaGuardadaService ventasGuardadas; private final JdbcTemplate jdbc;

    public FacturacionPosService(TerminalPosRepository terminales,CashRegisterRepository cajas,
            CashRegisterSessionRepository sesiones,SucursalRepository sucursales,PosWarehouseResolver almacenesPos,
            ProductRepository productos,ClienteRepository clientes,InventoryMovementRepository existencias,
            FacturaVentaRepository facturas,LineaFacturaVentaRepository lineas,PagoVentaRepository pagos,
            MetodoPagoVentaRepository metodosPago,AplicacionPagoVentaRepository aplicacionesPago,BankAccountRepository cuentasBancarias,
            FinancialMovementRepository movimientos,InventoryMovementService inventario,AuditoriaService auditoria,
            VentaGuardadaService ventasGuardadas,JdbcTemplate jdbc){
        this.terminales=terminales;this.cajas=cajas;this.sesiones=sesiones;this.sucursales=sucursales;this.almacenesPos=almacenesPos;
        this.productos=productos;this.clientes=clientes;this.existencias=existencias;this.facturas=facturas;this.lineas=lineas;
        this.pagos=pagos;this.metodosPago=metodosPago;this.aplicacionesPago=aplicacionesPago;this.cuentasBancarias=cuentasBancarias;
        this.movimientos=movimientos;this.inventario=inventario;this.auditoria=auditoria;this.ventasGuardadas=ventasGuardadas;this.jdbc=jdbc;
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
                p.isControlaExistencia(),p.isPermiteExistenciaNegativa())).toList();
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

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.cobrar')")
    public FacturaVentaResultadoDto confirmar(ConfirmarVentaInput input){
        if(input==null||input.idempotencia()==null)throw new ReglaNegocioException("No fue posible identificar la operación de cobro.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        Optional<FacturaVenta> existente=facturas.findByTenantIdAndEmpresaIdAndClaveIdempotencia(p.tenantId(),empresa,input.idempotencia());
        if(existente.isPresent())return resultado(existente.get());
        if(input.almacenId()==null)throw new ReglaNegocioException("No fue posible identificar el almacén de la venta.");
        ContextoPos ctx=contexto(input.token(),true,input.almacenId());
        existente=facturas.findByTenantIdAndEmpresaIdAndClaveIdempotencia(p.tenantId(),empresa,input.idempotencia());
        if(existente.isPresent())return resultado(existente.get());
        if(input.lineas()==null||input.lineas().isEmpty())throw new ReglaNegocioException("Agrega al menos un producto o servicio.");
        if(input.lineas().size()>200)throw new ReglaNegocioException("La factura excede el máximo de 200 líneas.");
        Set<UUID> ids=new HashSet<>();List<CalculoLinea> calculos=new ArrayList<>();
        BigDecimal subtotal=BigDecimal.ZERO,impuestos=BigDecimal.ZERO,total=BigDecimal.ZERO;
        for(LineaVentaInput entrada:input.lineas()){
            if(entrada==null||entrada.productoId()==null||entrada.cantidad()==null||entrada.cantidad().signum()<=0)
                throw new ReglaNegocioException("Todas las cantidades deben ser mayores que cero.");
            if(!ids.add(entrada.productoId()))throw new ReglaNegocioException("El producto ya existe en la factura.");
            BigDecimal cantidad=entrada.cantidad().setScale(SCALE,RoundingMode.HALF_UP);
            Producto producto=productos.bloquear(entrada.productoId(),p.tenantId(),empresa)
                .orElseThrow(()->new RecursoNoEncontradoException("Producto no encontrado."));
            if(!producto.isActivo()||producto.getPrecioVenta()==null)throw new ReglaNegocioException("El producto "+producto.getNombre()+" no está disponible para venta.");
            if(!ctx.caja.getMonedaId().equals(producto.getMonedaId()))throw new ReglaNegocioException("El producto "+producto.getNombre()+" utiliza una moneda distinta a la caja.");
            BigDecimal precio=producto.getPrecioVenta().setScale(SCALE,RoundingMode.HALF_UP);
            if(entrada.precioEsperado()==null||precio.compareTo(entrada.precioEsperado().setScale(SCALE,RoundingMode.HALF_UP))!=0)
                throw new ReglaNegocioException("El precio de "+producto.getNombre()+" cambió. Actualiza la venta antes de cobrar.");
            Impuesto tax=producto.getImpuestoVenta()!=null&&producto.getImpuestoVenta().isActivo()?producto.getImpuestoVenta():null;
            VentaCalculo.Resultado calculo=VentaCalculo.linea(precio,cantidad,tax==null?BigDecimal.ZERO:tax.getPorcentaje());
            BigDecimal base=calculo.subtotal(),impuesto=calculo.impuesto(),lineaTotal=calculo.total();
            if(producto.isControlaExistencia()&&!producto.isPermiteExistenciaNegativa()){
                BigDecimal disponible=existencias.saldo(p.tenantId(),empresa,ctx.almacen.getId(),producto.getId());
                if(disponible.compareTo(cantidad)<0)throw new ReglaNegocioException("Existencia insuficiente para "+producto.getNombre()+".");
            }
            calculos.add(new CalculoLinea(producto,cantidad,precio,base,tax,impuesto,lineaTotal));
            subtotal=subtotal.add(base);impuestos=impuestos.add(impuesto);total=total.add(lineaTotal);
        }
        List<AplicacionValidada> cobros=validarAplicaciones(input.aplicaciones(),total,ctx,p.permisos());
        BigDecimal aplicado=CobroVentaCalculo.totalAplicado(cobros.stream().map(AplicacionValidada::monto).toList());
        BigDecimal cambio=CobroVentaCalculo.totalAplicado(cobros.stream().map(AplicacionValidada::cambio).toList());
        BigDecimal recibido=total.add(cambio);
        Cliente cliente=input.clienteId()==null?null:clientes.findByIdAndTenantIdAndEmpresaId(input.clienteId(),p.tenantId(),empresa)
            .filter(Cliente::isActivo).orElseThrow(()->new RecursoNoEncontradoException("Cliente no encontrado."));
        String numero=siguienteNumero(p.tenantId(),empresa);
        FacturaVenta factura=facturas.saveAndFlush(new FacturaVenta(p.tenantId(),empresa,numero,ctx.sesion.getFechaOperativa(),p.usuarioId(),
            ctx.terminal.getId(),ctx.caja.getId(),ctx.sesion.getId(),ctx.caja.getSucursalId(),ctx.almacen.getId(),
            cliente==null?null:cliente.getId(),cliente==null?"Consumidor Final":nombreCliente(cliente),cliente==null?null:cliente.getIdentificacion(),
            p.nombre(),ctx.caja.getMonedaId(),ctx.caja.getMoneda().getCodigoIso(),ctx.caja.getMoneda().getSimbolo(),nota(input.nota()),
            subtotal,impuestos,total,recibido,input.idempotencia()));
        int orden=1;
        for(CalculoLinea c:calculos){
            LineaFacturaVenta linea=lineas.saveAndFlush(new LineaFacturaVenta(factura,c.producto,ctx.almacen.getId(),orden++,c.cantidad,
                c.precio,c.subtotal,c.impuesto,c.impuestoMonto,c.total));
            inventario.vender(factura,linea,c.producto,p.usuarioId());
        }
        PagoVenta pago=pagos.saveAndFlush(new PagoVenta(factura,aplicado,cambio,p.usuarioId()));
        int ordenPago=1;
        for(AplicacionValidada cobro:cobros){
            AplicacionPagoVenta aplicacion=aplicacionesPago.saveAndFlush(new AplicacionPagoVenta(pago,cobro.metodo,cobro.monto,
                cobro.recibido,cobro.cambio,cobro.cuenta==null?null:cobro.cuenta.getId(),cobro.referencia,cobro.banco,ordenPago++));
            registrarMovimientoCobro(ctx,factura,aplicacion,cobro,p.usuarioId());
        }
        auditoria.registrar("SALES_INVOICE_CONFIRMED","FacturaVenta",factura.getId(),"{\"numero\":\""+numero+"\",\"total\":"+total+
            ",\"aplicaciones\":"+cobros.size()+",\"mixto\":"+CobroVentaCalculo.mixto(cobros.size())+"}");
        ventasGuardadas.completarDesdeFactura(input.ventaGuardadaId(),factura.getId(),p.usuarioId(),ctx.terminal.getId(),p.tenantId(),empresa);
        return resultado(factura);
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
    private record CalculoLinea(Producto producto,BigDecimal cantidad,BigDecimal precio,BigDecimal subtotal,Impuesto impuesto,BigDecimal impuestoMonto,BigDecimal total){}
    private record AplicacionValidada(MetodoPagoVenta metodo,BigDecimal monto,BigDecimal recibido,BigDecimal cambio,
                                      CuentaBancaria cuenta,String referencia,String banco){}
}
