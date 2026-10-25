package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class CobroClienteService {
    private static final int PAGE_SIZE=5;
    private final CobroClienteRepository receipts;
    private final ClienteRepository customers;
    private final MonedaRepository currencies;
    private final SucursalRepository branches;
    private final MetodoPagoVentaRepository methods;
    private final CuentaDineroResolver accountResolver;
    private final CashRegisterSessionService cashSessions;
    private final FinancialMovementRepository movements;
    private final BankReconciliationMatchRepository reconciliation;
    private final AuditoriaService audit;

    public CobroClienteService(CobroClienteRepository receipts,ClienteRepository customers,MonedaRepository currencies,SucursalRepository branches,
            MetodoPagoVentaRepository methods,CuentaDineroResolver accountResolver,CashRegisterSessionService cashSessions,
            FinancialMovementRepository movements,BankReconciliationMatchRepository reconciliation,AuditoriaService audit){
        this.receipts=receipts;this.customers=customers;this.currencies=currencies;this.branches=branches;this.methods=methods;
        this.accountResolver=accountResolver;this.cashSessions=cashSessions;this.movements=movements;this.reconciliation=reconciliation;this.audit=audit;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.ver')")
    public Page<CobroClienteDto> buscar(String texto,LocalDate desde,LocalDate hasta,EstadoReciboCliente estado,int pagina){
        if(pagina<0)throw new ReglaNegocioException("Paginación inválida.");
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new ReglaNegocioException("La fecha inicial no puede ser posterior a la fecha final.");
        var p=TenantContext.principalActual();String t=limpiar(texto);
        boolean todas=p.accesoTodasSucursales()||p.permisos().contains("recibos.ver_otras_sucursales");
        return receipts.buscar(p.tenantId(),EmpresaContext.requerirEmpresaId(),todas,p.sucursalIds(),t,soloDigitos(t),desde,hasta,estado,pagina,PAGE_SIZE);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.crear')")
    public List<ClienteCobroDto> buscarClientes(String texto,int pagina){
        if(pagina<0)throw new ReglaNegocioException("Paginación inválida.");var p=TenantContext.principalActual();String t=limpiar(texto);
        return customers.buscarParaPos(p.tenantId(),EmpresaContext.requerirEmpresaId(),t,soloDigitos(t),soloDigitos(t),PageRequest.of(pagina,PAGE_SIZE))
            .stream().map(c->new ClienteCobroDto(c.getId(),c.getCodigo(),nombre(c),c.getIdentificacion(),c.getMonedaId())).toList();
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.crear')")
    public Page<FacturaPendienteCobroDto> facturasPendientes(UUID cliente,UUID moneda,int pagina){
        if(cliente==null||moneda==null||pagina<0)throw new ReglaNegocioException("Selecciona cliente y moneda.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();cliente(cliente,p.tenantId(),empresa);
        receipts.prepararCuentas(p.tenantId(),empresa,cliente);
        return receipts.facturas(p.tenantId(),empresa,cliente,moneda,pagina,PAGE_SIZE);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.crear')")
    public UUID monedaBase(){return currencies.findByEmpresaIdAndMonedaBaseTrue(EmpresaContext.requerirEmpresaId())
        .filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("Configura una moneda base activa.")).getId();}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.crear')")
    public CobroClienteResultadoDto confirmar(CobroClienteInput input){
        if(input==null||input.clienteId()==null||input.monedaId()==null||input.sucursalId()==null||input.fecha()==null)
            throw new ReglaNegocioException("Cliente, sucursal, fecha y moneda son obligatorios.");
        BigDecimal received=money(input.recibido());if(received.signum()<=0)throw new ReglaNegocioException("El total recibido debe ser mayor que cero.");
        if(input.idempotencia()==null)throw new ReglaNegocioException("La clave de idempotencia es obligatoria.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();String fingerprint=CommandFingerprint.sha256(new Object[]{input});
        CobroClienteDto duplicate=receipts.buscarIdempotencia(p.tenantId(),empresa,input.idempotencia()).orElse(null);
        if(duplicate!=null){if(!Objects.equals(receipts.fingerprint(p.tenantId(),empresa,input.idempotencia()),fingerprint))throw new ReglaNegocioException("La operación ya fue utilizada con datos diferentes.");return resultado(duplicate);}
        Cliente customer=cliente(input.clienteId(),p.tenantId(),empresa);Moneda currency=currencies.findByIdAndEmpresaId(input.monedaId(),empresa)
            .filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("La moneda seleccionada no está disponible."));
        Sucursal branch=branches.findByIdAndEmpresaId(input.sucursalId(),empresa).filter(Sucursal::isActivo)
            .orElseThrow(()->new ReglaNegocioException("La sucursal no está disponible."));
        if(!EmpresaContext.permiteSucursal(branch.getId()))throw new RecursoNoEncontradoException("Sucursal no encontrada.");
        List<CobroClienteInput.AplicacionFactura> allocations=aplicaciones(input.aplicaciones());
        BigDecimal applied=allocations.stream().map(CobroClienteInput.AplicacionFactura::monto).map(CobroClienteService::money).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(applied.compareTo(received)>0)throw new ReglaNegocioException("El total aplicado no puede superar el total recibido.");
        List<MedioValidado> paymentMethods=medios(input,received,currency,p.tenantId(),empresa);
        UUID receiptId=UUID.randomUUID();String number="REC-"+String.format(Locale.ROOT,"%06d",receipts.siguienteNumero(p.tenantId(),empresa));
        receipts.insertarRecibo(receiptId,p.tenantId(),empresa,number,customer.getId(),nombre(customer),customer.getIdentificacion(),branch.getId(),branch.getNombre(),
            currency.getId(),currency.getCodigoIso(),input.fecha(),received,applied,texto(input.referencia(),100),texto(input.observacion(),1000),input.idempotencia(),fingerprint,p.usuarioId());
        for(var item:allocations){var account=receipts.bloquearCuenta(item.cuentaCobrarId(),p.tenantId(),empresa);
            if(account==null||!account.facturaId().equals(item.facturaId())||!account.clienteId().equals(customer.getId())||!account.monedaId().equals(currency.getId()))throw new ReglaNegocioException("Una factura seleccionada ya no está disponible.");
            BigDecimal amount=money(item.monto());
            if(amount.signum()<=0)throw new ReglaNegocioException("El importe aplicado debe ser mayor que cero.");
            if(amount.compareTo(account.saldo())>0)throw new ReglaNegocioException("El importe aplicado supera el saldo pendiente actual de la factura.");
            receipts.aplicarCuenta(account,amount);receipts.insertarAplicacion(UUID.randomUUID(),p.tenantId(),empresa,receiptId,account,amount,p.usuarioId());
            audit.registrar("CUSTOMER_RECEIPT_APPLICATION_CREATED","CobroCliente",receiptId,"{\"facturaId\":\""+account.facturaId()+"\",\"monto\":"+amount+"}");}
        int order=1;for(MedioValidado method:paymentMethods){UUID paymentId=UUID.randomUUID();receipts.insertarMedio(paymentId,p.tenantId(),empresa,receiptId,method.method.getId(),method.method.getCode(),method.method.getName(),method.method.getType().name(),method.amount,
            method.type==TipoCuentaDinero.CASH_REGISTER?method.account.id():null,method.sessionId,method.type==TipoCuentaDinero.BANK_ACCOUNT?method.account.id():null,method.account.nombre(),method.reference,order++);
            MovimientoFinanciero movement=MovimientoFinanciero.cobroCliente(p.tenantId(),empresa,input.fecha(),method.type,method.account.id(),currency.getId(),method.amount,number,paymentId,medio(method.method),method.sessionId,method.reference,p.usuarioId());
            movement.asignarRelacionesCuenta(method.account.caja(),method.account.cuentaBancaria(),method.account.moneda());movement=movements.saveAndFlush(movement);receipts.vincularMovimiento(paymentId,movement.getId());}
        audit.registrar("CUSTOMER_RECEIPT_CONFIRMED","CobroCliente",receiptId,"{\"numero\":\""+number+"\",\"recibido\":"+received+",\"aplicado\":"+applied+"}");
        return new CobroClienteResultadoDto(receiptId,number,received,applied,received.subtract(applied));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.aplicar')")
    public CobroClienteDto aplicarDisponible(UUID id,AplicarSaldoClienteInput input){
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();CobroClienteDto receipt=receipts.bloquearRecibo(id,p.tenantId(),empresa);
        if(receipt==null)throw new RecursoNoEncontradoException("Recibo no encontrado.");autorizarSucursal(receipt);if(receipt.estado()==EstadoReciboCliente.VOIDED||receipt.disponible().signum()<=0)throw new ReglaNegocioException("El recibo no tiene saldo disponible.");
        List<CobroClienteInput.AplicacionFactura> items=aplicaciones(input==null?null:input.aplicaciones());BigDecimal total=items.stream().map(CobroClienteInput.AplicacionFactura::monto).map(CobroClienteService::money).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.signum()<=0||total.compareTo(receipt.disponible())>0)throw new ReglaNegocioException("El importe supera el saldo disponible del recibo.");
        for(var item:items){var account=receipts.bloquearCuenta(item.cuentaCobrarId(),p.tenantId(),empresa);BigDecimal amount=money(item.monto());
            if(account==null||!account.facturaId().equals(item.facturaId())||!account.clienteId().equals(receipt.clienteId())||!account.monedaId().equals(receipt.monedaId())||amount.signum()<=0||amount.compareTo(account.saldo())>0)throw new ReglaNegocioException("El saldo de una o más facturas cambió. Actualice la información antes de continuar.");
            receipts.aplicarCuenta(account,amount);receipts.insertarAplicacion(UUID.randomUUID(),p.tenantId(),empresa,id,account,amount,p.usuarioId());}
        receipts.consumirDisponible(id,total);audit.registrar("CUSTOMER_RECEIPT_AVAILABLE_APPLIED","CobroCliente",id,"{\"monto\":"+total+"}");
        return detalle(id);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.ver')")
    public CobroClienteDto detalle(UUID id){var p=TenantContext.principalActual();CobroClienteDto dto=receipts.detalle(id,p.tenantId(),EmpresaContext.requerirEmpresaId());if(dto==null)throw new RecursoNoEncontradoException("Recibo no encontrado.");autorizarSucursal(dto);return dto;}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.anular')")
    public CobroClienteDto anular(UUID id,String reason){String clean=textoObligatorio(reason,500,"El motivo de anulación es obligatorio.");var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();CobroClienteDto receipt=receipts.bloquearRecibo(id,p.tenantId(),empresa);
        if(receipt==null)throw new RecursoNoEncontradoException("Recibo no encontrado.");autorizarSucursal(receipt);if(receipt.estado()==EstadoReciboCliente.VOIDED)return detalle(id);
        for(var app:receipts.aplicacionesActivas(id,p.tenantId(),empresa)){
            if(receipts.bloquearCuenta(app.cuentaId(),p.tenantId(),empresa)==null)
                throw new ReglaNegocioException("No fue posible bloquear una cuenta por cobrar del recibo.");
            receipts.revertirAplicacion(app,clean,p.usuarioId());
        }
        for(UUID movementId:receipts.movimientos(id,p.tenantId(),empresa)){MovimientoFinanciero movement=movements.buscarParaActualizar(movementId,p.tenantId(),empresa,TipoMovimientoFinanciero.INCOME).orElseThrow(()->new ReglaNegocioException("No fue posible revertir un movimiento financiero del recibo."));if(reconciliation.existsByMovimientoFinancieroId(movementId))throw new ReglaNegocioException("El recibo posee un movimiento conciliado y no puede anularse.");cashSessions.validarMovimientoModificable(movement.getSesionCajaId());movement.anular(clean,p.usuarioId());movements.save(movement);}
        receipts.anular(id,p.tenantId(),empresa,clean,p.usuarioId());audit.registrar("CUSTOMER_RECEIPT_VOIDED","CobroCliente",id,"{\"motivo\":\""+clean.replace("\"","'")+"\"}");return detalle(id);}

    private List<MedioValidado> medios(CobroClienteInput input,BigDecimal received,Moneda currency,UUID tenant,UUID empresa){
        if(input.medios()==null||input.medios().isEmpty())throw new ReglaNegocioException("Agrega al menos un medio de pago.");List<MedioValidado> out=new ArrayList<>();BigDecimal sum=BigDecimal.ZERO;int cash=0;
        for(AplicacionCobroInput item:input.medios()){MetodoPagoVenta method=methods.bloquearActivo(item.metodoPagoId(),tenant,empresa).orElseThrow(()->new ReglaNegocioException("El medio de pago ya no está disponible."));BigDecimal amount=money(item.monto());if(amount.signum()<=0)throw new ReglaNegocioException("Los importes de pago deben ser mayores que cero.");
            TipoCuentaDinero type;UUID accountId;UUID session=null;if(method.isCash()){if(++cash>1)throw new ReglaNegocioException("El efectivo debe registrarse una sola vez.");type=TipoCuentaDinero.CASH_REGISTER;accountId=input.cajaId();session=cashSessions.requerirSesionAbierta(accountId);if(input.sesionCajaId()!=null&&!input.sesionCajaId().equals(session))throw new ReglaNegocioException("El turno de caja cambió. Actualice la información.");}
            else{type=TipoCuentaDinero.BANK_ACCOUNT;accountId=item.cuentaBancariaId()!=null?item.cuentaBancariaId():method.getFinancialBankAccountId();if(accountId==null)throw new ReglaNegocioException("Selecciona la cuenta destino de "+method.getName()+".");}
            var account=accountResolver.resolver(type,accountId);if(!currency.getId().equals(account.monedaId()))throw new ReglaNegocioException("La cuenta destino utiliza una moneda distinta al cobro.");String reference=texto(item.referencia(),100);if(method.isRequiresReference()&&reference==null)throw new ReglaNegocioException("Indica la referencia de "+method.getName()+".");out.add(new MedioValidado(method,amount,type,account,session,reference));sum=sum.add(amount);}
        if(sum.compareTo(received)!=0)throw new ReglaNegocioException("La suma de los medios de pago debe coincidir con el total recibido.");return out;
    }
    private Cliente cliente(UUID id,UUID tenant,UUID empresa){return customers.findByIdAndTenantIdAndEmpresaId(id,tenant,empresa).filter(Cliente::isActivo).orElseThrow(()->new ReglaNegocioException("El cliente seleccionado no está disponible."));}
    private static void autorizarSucursal(CobroClienteDto receipt){var p=TenantContext.principalActual();if(!p.accesoTodasSucursales()&&!p.permisos().contains("recibos.ver_otras_sucursales")&&!p.sucursalIds().contains(receipt.sucursalId()))throw new RecursoNoEncontradoException("Recibo no encontrado.");}
    private static List<CobroClienteInput.AplicacionFactura> aplicaciones(List<CobroClienteInput.AplicacionFactura> source){List<CobroClienteInput.AplicacionFactura> out=source==null?new ArrayList<>():new ArrayList<>(source);out.sort(Comparator.comparing(CobroClienteInput.AplicacionFactura::cuentaCobrarId,Comparator.nullsLast(Comparator.naturalOrder())));Set<UUID> seen=new HashSet<>();for(var x:out)if(x==null||x.cuentaCobrarId()==null||x.facturaId()==null||x.monto()==null||!seen.add(x.cuentaCobrarId()))throw new ReglaNegocioException("La distribución del cobro contiene datos inválidos o repetidos.");return List.copyOf(out);}
    private static CobroClienteResultadoDto resultado(CobroClienteDto x){return new CobroClienteResultadoDto(x.id(),x.numero(),x.recibido(),x.aplicado(),x.disponible());}
    private static MedioPagoMovimiento medio(MetodoPagoVenta m){return switch(m.getType()){case CASH->MedioPagoMovimiento.CASH;case CREDIT_CARD,DEBIT_CARD->MedioPagoMovimiento.CARD;case BANK_TRANSFER->MedioPagoMovimiento.BANK_TRANSFER;case CHECK->MedioPagoMovimiento.CHECK;case OTHER->MedioPagoMovimiento.OTHER;};}
    private static BigDecimal money(BigDecimal value){return (value==null?BigDecimal.ZERO:value).setScale(4,RoundingMode.HALF_UP);}
    private static String nombre(Cliente c){if(c.getTipoPersona()==TipoPersonaCliente.JURIDICA)return primero(c.getNombreComercial(),c.getRazonSocial(),c.getCodigo());return (limpiar(c.getNombres())+" "+limpiar(c.getApellidos())).trim();}
    private static String primero(String...v){for(String x:v)if(x!=null&&!x.isBlank())return x.trim();return "Cliente";}
    private static String limpiar(String v){return v==null?"":v.trim();}private static String soloDigitos(String v){return v==null?"":v.replaceAll("\\D","");}
    private static String texto(String v,int max){String x=limpiar(v);if(x.isEmpty())return null;if(x.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return x;}
    private static String textoObligatorio(String v,int max,String message){String x=texto(v,max);if(x==null)throw new ReglaNegocioException(message);return x;}
    private record MedioValidado(MetodoPagoVenta method,BigDecimal amount,TipoCuentaDinero type,CuentaDineroResolver.CuentaResuelta account,UUID sessionId,String reference){}
}
