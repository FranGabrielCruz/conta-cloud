package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
class PaymentReceiptConfirmationRetryTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @ParameterizedTest @ValueSource(booleans={true,false})
    void concurrentRetryRechecksKeyBeforeLockingPaymentResourcesOrWritingEffects(boolean sameData){
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),client=UUID.randomUUID(),currency=UUID.randomUUID(),branch=UUID.randomUUID(),key=UUID.randomUUID(),receiptId=UUID.randomUUID();
        var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"DEMO","Admin","admin","",true,true,Set.of(),Set.of("recibos.crear"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
        var receipts=mock(CobroClienteRepository.class);var customers=mock(ClienteRepository.class);var currencies=mock(MonedaRepository.class);var branches=mock(SucursalRepository.class);var methods=mock(MetodoPagoVentaRepository.class);var accounts=mock(CuentaDineroResolver.class);var sessions=mock(CashRegisterSessionService.class);var movements=mock(FinancialMovementRepository.class);var reconciliation=mock(BankReconciliationMatchRepository.class);var audit=mock(AuditoriaService.class);
        var service=new CobroClienteService(receipts,customers,currencies,branches,methods,accounts,sessions,movements,reconciliation,audit);
        var customer=mock(Cliente.class);when(customer.isActivo()).thenReturn(true);when(customer.getId()).thenReturn(client);when(customers.findByIdAndTenantIdAndEmpresaId(client,tenant,company)).thenReturn(Optional.of(customer));
        var c=mock(Moneda.class);when(c.isActivo()).thenReturn(true);when(currencies.findByIdAndEmpresaId(currency,company)).thenReturn(Optional.of(c));
        var b=mock(Sucursal.class);when(b.isActivo()).thenReturn(true);when(b.getId()).thenReturn(branch);when(branches.findByIdAndEmpresaId(branch,company)).thenReturn(Optional.of(b));
        var input=new CobroClienteInput(client,currency,branch,LocalDate.of(2026,11,1),BigDecimal.TEN,null,null,null,null,key,List.of(),List.of());
        var confirmed=new CobroClienteDto(receiptId,"REC-001",client,"Juan",null,currency,"DOP",branch,"Principal",input.fecha(),OffsetDateTime.parse("2026-11-01T21:25:00Z"),BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN,EstadoReciboCliente.AVAILABLE,null,null,"Admin",null,List.of(),List.of());
        when(receipts.buscarIdempotencia(tenant,company,key)).thenReturn(Optional.empty(),Optional.of(confirmed));
        when(receipts.fingerprint(tenant,company,key)).thenReturn(sameData?CommandFingerprint.sha256(new Object[]{input}):"different");
        if(sameData)assertThat(service.confirmar(input).id()).isEqualTo(receiptId);else assertThatThrownBy(()->service.confirmar(input)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("datos diferentes");
        verify(receipts).bloquearClienteImpresion(tenant,company,client);verify(receipts,never()).siguienteNumero(any(),any());verify(receipts,never()).conservarImpresion(any(),any(),any(),any(),any(),any());verifyNoInteractions(methods,accounts,sessions,movements,reconciliation,audit);
    }
}
