package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.PagoFacturaProveedorInput;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SupplierPaymentService {
    private final SupplierPaymentRepository payments;
    private final FinancialMovementRepository movements;
    private final CuentaDineroResolver accountResolver;
    private final CashRegisterSessionService cashSessions;
    private final AuditoriaService audit;

    public SupplierPaymentService(SupplierPaymentRepository payments,FinancialMovementRepository movements,
            CuentaDineroResolver accountResolver,CashRegisterSessionService cashSessions,AuditoriaService audit) {
        this.payments=payments;this.movements=movements;this.accountResolver=accountResolver;
        this.cashSessions=cashSessions;this.audit=audit;
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and @empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('facturas_proveedores.pagar_contado')")
    public PagoProveedor registrar(FacturaProveedor invoice,PagoFacturaProveedorInput input) {
        if(input==null||input.tipoFuente()==null||input.fuenteId()==null)
            throw new ReglaNegocioException("Selecciona la caja o cuenta bancaria utilizada para pagar la factura.");
        UUID key=input.claveIdempotencia()==null?UUID.randomUUID():input.claveIdempotencia();
        var duplicated=payments.findByClaveIdempotenciaAndTenantIdAndEmpresaId(key,invoice.getTenantId(),invoice.getEmpresaId());
        if(duplicated.isPresent())return duplicated.get();
        if(payments.findByFacturaIdAndTenantIdAndEmpresaId(invoice.getId(),invoice.getTenantId(),invoice.getEmpresaId()).isPresent())
            throw new ReglaNegocioException("La factura ya tiene un pago al contado registrado.");
        var account=accountResolver.resolver(input.tipoFuente(),input.fuenteId());
        if(!invoice.getMonedaId().equals(account.monedaId()))
            throw new ReglaNegocioException("La moneda de la fuente de pago debe ser igual a la moneda de la factura.");
        MedioPagoMovimiento method=normalizeMethod(input.tipoFuente(),input.medioPago());
        UUID sessionId=input.tipoFuente()==TipoCuentaDinero.CASH_REGISTER
            ?cashSessions.requerirSesionAbierta(input.fuenteId()):null;
        var principal=TenantContext.principalActual();
        PagoProveedor payment=new PagoProveedor(invoice.getTenantId(),invoice.getEmpresaId(),invoice.getProveedorId(),
            invoice.getId(),invoice.getFecha(),invoice.getMonedaId(),invoice.getTotal(),input.tipoFuente(),
            input.fuenteId(),clean(input.referencia()),key,principal.usuarioId());
        payment=payments.saveAndFlush(payment);
        MovimientoFinanciero movement=MovimientoFinanciero.pagoProveedor(invoice.getTenantId(),invoice.getEmpresaId(),
            invoice.getFecha(),input.tipoFuente(),input.fuenteId(),invoice.getMonedaId(),invoice.getTotal(),
            "Pago factura proveedor "+invoice.getNumeroProveedor(),clean(input.referencia()),payment.getId(),method,
            sessionId,principal.usuarioId());
        movement.asignarRelacionesCuenta(account.caja(),account.cuentaBancaria(),account.moneda());
        movement=movements.saveAndFlush(movement);
        payment.vincularMovimiento(movement.getId());payments.saveAndFlush(payment);
        audit.registrar("SUPPLIER_PAYMENT_CREATED","PagoProveedor",payment.getId(),detail(payment));
        audit.registrar("SUPPLIER_PAYMENT_MOVEMENT_CREATED","MovimientoFinanciero",movement.getId(),
            "{\"facturaId\":\""+invoice.getId()+"\"}");
        return payment;
    }

    @Transactional
    public void anularPorFactura(FacturaProveedor invoice,String reason) {
        PagoProveedor payment=payments.bloquearPorFactura(invoice.getId(),invoice.getTenantId(),invoice.getEmpresaId()).orElse(null);
        if(payment==null||payment.getEstado()==EstadoPagoProveedor.VOIDED)return;
        MovimientoFinanciero movement=movements.bloquearPorOrigen(invoice.getTenantId(),invoice.getEmpresaId(),
            TipoOrigenMovimiento.SUPPLIER_PAYMENT,payment.getId())
            .orElseThrow(()->new ReglaNegocioException("No se encontró el movimiento financiero asociado al pago."));
        if(movement.getSesionCajaId()!=null)cashSessions.validarMovimientoModificable(movement.getSesionCajaId());
        UUID user=TenantContext.principalActual().usuarioId();movement.anular(reason,user);movements.saveAndFlush(movement);
        payment.anular(reason,user);payments.saveAndFlush(payment);
        audit.registrar("SUPPLIER_PAYMENT_VOIDED","PagoProveedor",payment.getId(),detail(payment));
    }

    private static MedioPagoMovimiento normalizeMethod(TipoCuentaDinero type,MedioPagoMovimiento method) {
        if(type==TipoCuentaDinero.CASH_REGISTER)return MedioPagoMovimiento.CASH;
        return method==null||method==MedioPagoMovimiento.CASH?MedioPagoMovimiento.BANK_TRANSFER:method;
    }
    private static String clean(String value){if(value==null||value.isBlank())return null;String clean=value.trim();
        if(clean.length()>100)throw new ReglaNegocioException("La referencia del pago excede 100 caracteres.");return clean;}
    private static String detail(PagoProveedor p){return "{\"facturaId\":\""+p.getFacturaId()+"\",\"monto\":"+
        p.getMonto()+",\"fuente\":\""+p.getTipoFuente()+"\",\"estado\":\""+p.getEstado()+"\"}";}
}
