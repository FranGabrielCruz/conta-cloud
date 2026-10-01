package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.TransferenciaFinancieraInput;
import com.citacloud.springboot.contacloud.app.mappers.TransferenciaFinancieraMapper;
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

class FinancialTransferServiceTest {
    private final FinancialTransferRepository repository=mock(FinancialTransferRepository.class);
    private final TasaCambioRepository tasas=mock(TasaCambioRepository.class);
    private final CuentaDineroResolver cuentas=mock(CuentaDineroResolver.class);
    private final FinancialMovementService movimientos=mock(FinancialMovementService.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final FinancialTransferService service=new FinancialTransferService(repository,tasas,cuentas,movimientos,
        new TransferenciaFinancieraMapper(),auditoria);
    private final UUID tenantId=UUID.randomUUID(),empresaId=UUID.randomUUID(),usuarioId=UUID.randomUUID();
    private final UUID origenId=UUID.randomUUID(),destinoId=UUID.randomUUID(),dopId=UUID.randomUUID(),usdId=UUID.randomUUID();
    private Moneda dop,usd;private CuentaDineroResolver.CuentaResuelta origenDop,destinoDop,destinoUsd;

    @BeforeEach void preparar(){
        var principal=new TenantPrincipal(usuarioId,tenantId,empresaId,null,"DEMO","Administrador","admin","",true,true,
            Set.of(),Set.of("transferencias.ver","transferencias.crear","transferencias.anular"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        dop=moneda(dopId,"DOP");usd=moneda(usdId,"USD");
        Caja caja=mock(Caja.class);when(caja.getId()).thenReturn(origenId);when(caja.getNombre()).thenReturn("Caja Principal");
        CuentaBancaria bancoDop=mock(CuentaBancaria.class);when(bancoDop.getId()).thenReturn(destinoId);
        when(bancoDop.getBancoNombre()).thenReturn("Banco Demo");when(bancoDop.getNombreCuenta()).thenReturn("Cuenta DOP");
        CuentaBancaria bancoUsd=mock(CuentaBancaria.class);when(bancoUsd.getId()).thenReturn(destinoId);
        when(bancoUsd.getBancoNombre()).thenReturn("Banco Demo");when(bancoUsd.getNombreCuenta()).thenReturn("Cuenta USD");
        origenDop=new CuentaDineroResolver.CuentaResuelta(TipoCuentaDinero.CASH_REGISTER,origenId,dopId,dop,caja,null,"Caja Principal");
        destinoDop=new CuentaDineroResolver.CuentaResuelta(TipoCuentaDinero.BANK_ACCOUNT,destinoId,dopId,dop,null,bancoDop,"Banco Demo · Cuenta DOP");
        destinoUsd=new CuentaDineroResolver.CuentaResuelta(TipoCuentaDinero.BANK_ACCOUNT,destinoId,usdId,usd,null,bancoUsd,"Banco Demo · Cuenta USD");
        when(cuentas.resolver(TipoCuentaDinero.CASH_REGISTER,origenId)).thenReturn(origenDop);
        when(repository.saveAndFlush(any())).thenAnswer(inv->{TransferenciaFinanciera t=inv.getArgument(0);
            if(t.getId()==null)ReflectionTestUtils.setField(t,"id",UUID.randomUUID());return t;});
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void creaTransferenciaMismaMonedaConDosMovimientosVinculados(){
        when(cuentas.resolver(TipoCuentaDinero.BANK_ACCOUNT,destinoId)).thenReturn(destinoDop);
        var dto=service.crear(input(new BigDecimal("20000.00"),null));
        assertThat(dto.montoDestino()).isEqualByComparingTo("20000.00");assertThat(dto.tasaCambio()).isNull();
        verify(movimientos).registrarTransferencia(dto.id(),TipoMovimientoFinanciero.EXPENSE,LocalDate.of(2026,10,1),
            origenDop,new BigDecimal("20000.00"),"TRF-1","Depósito");
        verify(movimientos).registrarTransferencia(dto.id(),TipoMovimientoFinanciero.INCOME,LocalDate.of(2026,10,1),
            destinoDop,new BigDecimal("20000.00"),"TRF-1","Depósito");
        verify(auditoria).registrar(eq("FINANCIAL_TRANSFER_CREATED"),eq("TransferenciaFinanciera"),eq(dto.id()),anyString());
    }

    @Test void convierteDopAUsdConTasaCotizadaComoUnoUsdEnDop(){
        when(cuentas.resolver(TipoCuentaDinero.BANK_ACCOUNT,destinoId)).thenReturn(destinoUsd);
        var dto=service.crear(input(new BigDecimal("60000.00"),new BigDecimal("60.0000")));
        assertThat(dto.montoDestino()).isEqualByComparingTo("1000.00");
        assertThat(dto.tasaCambio()).isEqualByComparingTo("60.00000000");
        assertThat(dto.descripcionTasa()).isEqualTo("1 USD = 60 DOP");
        verify(movimientos).registrarTransferencia(dto.id(),TipoMovimientoFinanciero.INCOME,LocalDate.of(2026,10,1),
            destinoUsd,new BigDecimal("1000.00"),"TRF-1","Depósito");
    }

    @Test void precargaTasaConfiguradaSinUsarTasasFuturas(){
        TasaCambio tasa=mock(TasaCambio.class);when(tasa.getTasa()).thenReturn(new BigDecimal("60.00000000"));
        when(tasa.getMonedaOrigen()).thenReturn(usd);when(tasa.getMonedaDestino()).thenReturn(dop);
        when(tasas.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
            tenantId,empresaId,usdId,dopId,LocalDate.of(2026,10,1))).thenReturn(Optional.of(tasa));
        var result=service.resolverTasa(dopId,usdId,LocalDate.of(2026,10,1));
        assertThat(result).get().extracting(x->x.tasa()).isEqualTo(new BigDecimal("60.00000000"));
        assertThat(result.get().equivalencia()).isEqualTo("1 USD = 60 DOP");
        verify(tasas,never()).findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
            tenantId,empresaId,usdId,dopId,LocalDate.of(2026,10,2));
    }

    @Test void rechazaOrigenIgualAlDestinoAntesDePersistir(){
        var i=new TransferenciaFinancieraInput(LocalDate.now(),TipoCuentaDinero.CASH_REGISTER,origenId,
            TipoCuentaDinero.CASH_REGISTER,origenId,BigDecimal.TEN,null,null,null);
        assertThatThrownBy(()->service.crear(i)).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("El origen y el destino deben ser diferentes.");
        verifyNoInteractions(repository);
    }

    @Test void propagaFalloDelMovimientoDestinoParaQueLaTransaccionHagaRollback(){
        when(cuentas.resolver(TipoCuentaDinero.BANK_ACCOUNT,destinoId)).thenReturn(destinoDop);
        when(movimientos.registrarTransferencia(any(),eq(TipoMovimientoFinanciero.INCOME),any(),any(),any(),any(),any()))
            .thenThrow(new IllegalStateException("fallo destino"));
        assertThatThrownBy(()->service.crear(input(new BigDecimal("100.00"),null)))
            .isInstanceOf(IllegalStateException.class).hasMessage("fallo destino");
        verify(auditoria,never()).registrar(eq("FINANCIAL_TRANSFER_CREATED"),any(),any(),any());
    }

    @Test void anulaTransferenciaYMovimientosComoUnaOperacion(){
        when(cuentas.resolver(TipoCuentaDinero.BANK_ACCOUNT,destinoId)).thenReturn(destinoDop);
        var creada=service.crear(input(new BigDecimal("100.00"),null));
        TransferenciaFinanciera entity=entityDesde(creada);
        when(repository.buscarParaAnular(creada.id(),tenantId,empresaId)).thenReturn(Optional.of(entity));
        var anulada=service.anular(creada.id(),"Cuenta destino incorrecta");
        assertThat(anulada.estado()).isEqualTo(EstadoMovimientoFinanciero.VOIDED);
        assertThat(anulada.motivoAnulacion()).isEqualTo("Cuenta destino incorrecta");
        verify(movimientos).anularTransferencia(creada.id(),"Cuenta destino incorrecta");
        verify(auditoria).registrar(eq("FINANCIAL_TRANSFER_VOIDED"),eq("TransferenciaFinanciera"),eq(creada.id()),anyString());
    }

    @Test void noExponeOperacionGenericaDeEdicion(){
        assertThat(Arrays.stream(FinancialTransferService.class.getMethods()).map(java.lang.reflect.Method::getName))
            .doesNotContain("actualizar","editar");
    }

    private TransferenciaFinancieraInput input(BigDecimal monto,BigDecimal tasa){return new TransferenciaFinancieraInput(
        LocalDate.of(2026,10,1),TipoCuentaDinero.CASH_REGISTER,origenId,TipoCuentaDinero.BANK_ACCOUNT,destinoId,
        monto,tasa,"TRF-1","Depósito");}
    private TransferenciaFinanciera entityDesde(com.citacloud.springboot.contacloud.app.dto.TransferenciaFinancieraDto d){
        TransferenciaFinanciera t=new TransferenciaFinanciera(tenantId,empresaId,d.fecha(),d.tipoOrigen(),d.origenId(),
            d.tipoDestino(),d.destinoId(),d.monedaOrigenId(),d.monedaDestinoId(),d.montoOrigen(),d.montoDestino(),
            d.tasaCambio(),d.descripcionTasa(),d.referencia(),d.descripcion(),usuarioId);
        ReflectionTestUtils.setField(t,"id",d.id());t.asignarRelaciones(origenDop.caja(),null,null,destinoDop.cuentaBancaria(),dop,dop);return t;
    }
    private Moneda moneda(UUID id,String codigo){Moneda m=new Moneda(empresaId,codigo,codigo,"$",(short)2);
        ReflectionTestUtils.setField(m,"id",id);return m;}
}
