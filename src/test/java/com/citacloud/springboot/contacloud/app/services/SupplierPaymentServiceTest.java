package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.PagoFacturaProveedorInput;
import com.citacloud.springboot.contacloud.app.dto.PagoProveedorInput;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierPaymentServiceTest {
    private final SupplierPaymentRepository payments=mock(SupplierPaymentRepository.class);
    private final SupplierPaymentApplicationRepository applications=mock(SupplierPaymentApplicationRepository.class);
    private final SupplierPaymentDraftAllocationRepository drafts=mock(SupplierPaymentDraftAllocationRepository.class);
    private final FinancialMovementRepository movements=mock(FinancialMovementRepository.class);
    private final CashRegisterRepository cash=mock(CashRegisterRepository.class);
    private final BankAccountRepository banks=mock(BankAccountRepository.class);
    private final CashRegisterSessionService sessions=mock(CashRegisterSessionService.class);
    private final AuditoriaService audit=mock(AuditoriaService.class);
    private final SupplierRepository suppliers=mock(SupplierRepository.class);
    private final MonedaRepository currencies=mock(MonedaRepository.class);
    private final PurchaseInvoiceRepository invoices=mock(PurchaseInvoiceRepository.class);
    private final AccountsPayableRepository payables=mock(AccountsPayableRepository.class);
    private final SupplierCreditNoteRepository creditNotes=mock(SupplierCreditNoteRepository.class);
    private final SupplierPaymentNumberService numbers=mock(SupplierPaymentNumberService.class);
    private final BankReconciliationMatchRepository reconciliation=mock(BankReconciliationMatchRepository.class);
    private final SupplierPaymentService service=new SupplierPaymentService(payments,applications,drafts,movements,
        new CuentaDineroResolver(cash,banks),sessions,audit,suppliers,currencies,invoices,payables,creditNotes,numbers,reconciliation);
    private final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),user=UUID.randomUUID();
    private final UUID supplier=UUID.randomUUID(),branch=UUID.randomUUID(),currency=UUID.randomUUID();

    @BeforeEach void setup(){
        var principal=new TenantPrincipal(user,tenant,company,null,"DEMO","Admin","admin","",true,true,Set.of(),
            Set.of("facturas_proveedores.pagar_contado"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        when(payments.findByClaveIdempotenciaAndTenantIdAndEmpresaId(any(),eq(tenant),eq(company))).thenReturn(Optional.empty());
        when(payments.findByFacturaIdAndTenantIdAndEmpresaId(any(),eq(tenant),eq(company))).thenReturn(Optional.empty());
        when(numbers.next(tenant,company)).thenReturn("PG-000001");
        when(payments.saveAndFlush(any())).thenAnswer(i->{PagoProveedor p=i.getArgument(0);if(p.getId()==null)
            ReflectionTestUtils.setField(p,"id",UUID.randomUUID());return p;});
        when(movements.saveAndFlush(any())).thenAnswer(i->{MovimientoFinanciero m=i.getArgument(0);if(m.getId()==null)
            ReflectionTestUtils.setField(m,"id",UUID.randomUUID());return m;});
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void pagoBancarioCreaUnEgresoExactoSinCuentaPorPagar(){
        UUID accountId=UUID.randomUUID();Moneda coin=mock(Moneda.class);when(coin.getCodigoIso()).thenReturn("DOP");
        CuentaBancaria account=mock(CuentaBancaria.class);when(account.isActivo()).thenReturn(true);
        when(account.getMonedaId()).thenReturn(currency);when(account.getMoneda()).thenReturn(coin);
        when(account.getBancoNombre()).thenReturn("BHD");when(account.getNombreCuenta()).thenReturn("Operativa");
        when(banks.findByIdAndTenantIdAndEmpresaId(accountId,tenant,company)).thenReturn(Optional.of(account));
        FacturaProveedor invoice=invoice(currency);UUID key=UUID.randomUUID();

        PagoProveedor result=service.registrar(invoice,new PagoFacturaProveedorInput(TipoCuentaDinero.BANK_ACCOUNT,
            accountId,MedioPagoMovimiento.BANK_TRANSFER,"TRX-1",key));

        assertThat(result.getMonto()).isEqualByComparingTo("14160.00");assertThat(result.getTipoFuente()).isEqualTo(TipoCuentaDinero.BANK_ACCOUNT);
        verify(movements).saveAndFlush(argThat(m->m.getTipoMovimiento()==TipoMovimientoFinanciero.EXPENSE
            &&m.getTipoOrigen()==TipoOrigenMovimiento.SUPPLIER_PAYMENT&&m.getMonto().compareTo(new BigDecimal("14160.00"))==0));
    }

    @Test void rechazaFuenteEnMonedaDistinta(){
        UUID accountId=UUID.randomUUID();CuentaBancaria account=mock(CuentaBancaria.class);when(account.isActivo()).thenReturn(true);
        when(account.getMonedaId()).thenReturn(UUID.randomUUID());when(banks.findByIdAndTenantIdAndEmpresaId(accountId,tenant,company))
            .thenReturn(Optional.of(account));
        assertThatThrownBy(()->service.registrar(invoice(currency),new PagoFacturaProveedorInput(TipoCuentaDinero.BANK_ACCOUNT,
            accountId,null,null,UUID.randomUUID()))).isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("moneda de la fuente");
        verify(movements,never()).saveAndFlush(any());
    }

    @Test void creaPagoEnBorradorConClaveIdempotenteEstable(){
        UUID accountId=UUID.randomUUID(),key=UUID.randomUUID();
        prepararCatalogosPago(accountId);

        var result=service.create(input(accountId,key));

        assertThat(result.estado()).isEqualTo(EstadoPagoProveedor.DRAFT);
        assertThat(result.numero()).isEqualTo("PG-000001");
        verify(payments).findByClaveIdempotenciaAndTenantIdAndEmpresaId(key,tenant,company);
        verify(payments).saveAndFlush(any(PagoProveedor.class));
    }

    @Test void creaYConfirmaPagoEnUnaSolaTransaccion(){
        UUID accountId=UUID.randomUUID(),key=UUID.randomUUID();
        prepararCatalogosPago(accountId);
        PagoProveedor[] persisted={null};
        when(payments.saveAndFlush(any())).thenAnswer(invocation->{
            PagoProveedor payment=invocation.getArgument(0);
            if(payment.getId()==null)ReflectionTestUtils.setField(payment,"id",UUID.randomUUID());
            persisted[0]=payment;
            return payment;
        });
        when(payments.bloquear(any(),eq(tenant),eq(company)))
            .thenAnswer(invocation->Optional.ofNullable(persisted[0]));
        when(drafts.findAllByPagoIdAndTenantIdAndEmpresaId(any(),eq(tenant),eq(company)))
            .thenReturn(List.of());

        var result=service.createAndConfirm(input(accountId,key));

        assertThat(result.estado()).isEqualTo(EstadoPagoProveedor.AVAILABLE);
        assertThat(result.disponible()).isEqualByComparingTo("1500.00");
        verify(movements).saveAndFlush(argThat(movement->
            movement.getTipoMovimiento()==TipoMovimientoFinanciero.EXPENSE
                && movement.getTipoOrigen()==TipoOrigenMovimiento.SUPPLIER_PAYMENT));
    }

    private void prepararCatalogosPago(UUID accountId){
        Proveedor provider=mock(Proveedor.class);
        when(provider.getId()).thenReturn(supplier);
        when(provider.isActivo()).thenReturn(true);
        when(suppliers.findByIdAndTenantIdAndEmpresaId(supplier,tenant,company)).thenReturn(Optional.of(provider));
        Moneda coin=mock(Moneda.class);
        when(coin.getId()).thenReturn(currency);
        when(coin.isActivo()).thenReturn(true);
        when(currencies.findByIdAndEmpresaId(currency,company)).thenReturn(Optional.of(coin));
        CuentaBancaria account=mock(CuentaBancaria.class);
        when(account.isActivo()).thenReturn(true);
        when(account.getMonedaId()).thenReturn(currency);
        when(account.getMoneda()).thenReturn(coin);
        when(account.getBancoNombre()).thenReturn("BHD");
        when(account.getNombreCuenta()).thenReturn("Operativa");
        when(banks.findByIdAndTenantIdAndEmpresaId(accountId,tenant,company)).thenReturn(Optional.of(account));
    }

    private PagoProveedorInput input(UUID accountId,UUID key){
        return new PagoProveedorInput(supplier,LocalDate.of(2026,10,10),currency,new BigDecimal("1500.00"),
            MedioPagoMovimiento.BANK_TRANSFER,TipoCuentaDinero.BANK_ACCOUNT,accountId,null,"TRX-100",null,
            List.of(),null,key);
    }

    private FacturaProveedor invoice(UUID currencyId){FacturaProveedor f=new FacturaProveedor(tenant,company,supplier,branch,
        "F-100","F100",null,LocalDate.of(2026,10,4),LocalDate.of(2026,10,4),UUID.randomUUID(),currencyId,null,null,null,user);
        ReflectionTestUtils.setField(f,"id",UUID.randomUUID());f.totales(new BigDecimal("12000"),BigDecimal.ZERO,
            new BigDecimal("2160"),new BigDecimal("14160"));return f;}
}
