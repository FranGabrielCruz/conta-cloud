package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaldosCuentasServiceTest {
    private final FinancialMovementRepository movimientos=mock(FinancialMovementRepository.class);
    private final CashRegisterSessionRepository sesiones=mock(CashRegisterSessionRepository.class);
    private final BankAccountRepository bancos=mock(BankAccountRepository.class);
    private final CashRegisterRepository cajas=mock(CashRegisterRepository.class);
    private final MonedaRepository monedas=mock(MonedaRepository.class);
    private final SucursalRepository sucursales=mock(SucursalRepository.class);
    private final TasaCambioRepository tasas=mock(TasaCambioRepository.class);
    private final SaldosCuentasService service=new SaldosCuentasService(movimientos,sesiones,bancos,cajas,monedas,sucursales,tasas);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
    private final UUID dopId=UUID.randomUUID(),usdId=UUID.randomUUID();

    @BeforeEach void autenticar(){var principal=new TenantPrincipal(usuario,tenant,empresa,null,"DEMO","Admin","admin","",true,true,Set.of(),
        Set.of("saldos_cuentas.ver","saldos_cuentas.ver_movimientos","saldos_cuentas.ver_bancaria"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void consultaSaldosConTenantEmpresaYFechaDeCorte(){
        SaldoCuentaProjection row=saldo(usdId,"USD",new BigDecimal("100"));
        when(movimientos.saldosBancarios(eq(tenant),eq(empresa),eq(LocalDate.of(2026,10,10)),eq("Banco"),isNull(),eq(true),any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(row)));
        var result=service.bancos(LocalDate.of(2026,10,10)," Banco ",null,true,0,10);
        assertThat(result.getContent()).singleElement().satisfies(x->{assertThat(x.moneda()).isEqualTo("USD");assertThat(x.saldoActual()).isEqualByComparingTo("100");});
    }

    @Test void convierteResumenUsandoTasaDeLaFechaDeCorte(){
        Moneda dop=moneda(dopId,"DOP",true);when(monedas.findByIdAndEmpresaId(dopId,empresa)).thenReturn(Optional.of(dop));
        TotalSaldoMonedaProjection totalUsd=total(usdId,"USD",new BigDecimal("10"));
        when(movimientos.totalesBancarios(eq(tenant),eq(empresa),any(),anyString(),isNull(),isNull())).thenReturn(List.of(totalUsd));
        when(movimientos.totalesCajas(eq(tenant),eq(empresa),any(),anyString(),isNull(),isNull(),isNull(),eq(true),anySet()))
            .thenReturn(List.of());
        TasaCambio rate=mock(TasaCambio.class);when(rate.getTasa()).thenReturn(new BigDecimal("60"));
        when(tasas.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(tenant,empresa,usdId,dopId,LocalDate.of(2026,10,10)))
            .thenReturn(Optional.of(rate));
        var result=service.resumen(LocalDate.of(2026,10,10),"",null,null,null,dopId,true,true);
        assertThat(result.general()).isEqualByComparingTo("600");assertThat(result.equivalente()).isTrue();assertThat(result.advertencias()).isEmpty();
    }

    @Test void noMezclaMonedasCuandoFaltaLaTasa(){
        Moneda dop=moneda(dopId,"DOP",true);when(monedas.findByIdAndEmpresaId(dopId,empresa)).thenReturn(Optional.of(dop));
        TotalSaldoMonedaProjection totalUsd=total(usdId,"USD",new BigDecimal("10"));
        when(movimientos.totalesBancarios(any(),any(),any(),anyString(),isNull(),isNull())).thenReturn(List.of(totalUsd));
        var result=service.resumen(LocalDate.of(2026,10,10),"",null,null,null,dopId,true,false);
        assertThat(result.equivalente()).isFalse();assertThat(result.general()).isNull();
        assertThat(result.totalesPorMoneda()).containsEntry("USD",new BigDecimal("10"));
        assertThat(result.advertencias()).singleElement().asString().contains("USD → DOP");
    }

    @Test void calculaElTotalGeneralConCajasNegativas(){
        Moneda dop=moneda(dopId,"DOP",true);when(monedas.findByIdAndEmpresaId(dopId,empresa)).thenReturn(Optional.of(dop));
        TotalSaldoMonedaProjection totalBancos=total(dopId,"DOP",new BigDecimal("18316.40"));
        TotalSaldoMonedaProjection totalCajas=total(dopId,"DOP",new BigDecimal("-18112.00"));
        when(movimientos.totalesBancarios(any(),any(),any(),anyString(),isNull(),isNull())).thenReturn(List.of(totalBancos));
        when(movimientos.totalesCajas(any(),any(),any(),anyString(),isNull(),isNull(),isNull(),eq(true),anySet()))
            .thenReturn(List.of(totalCajas));
        var result=service.resumen(LocalDate.of(2026,10,10),"",null,null,null,dopId,true,true);
        assertThat(result.bancos()).isEqualByComparingTo("18316.40");
        assertThat(result.cajas()).isEqualByComparingTo("-18112.00");
        assertThat(result.general()).isEqualByComparingTo("204.40");
        assertThat(result.conversionAplicada()).isFalse();
    }

    private SaldoCuentaProjection saldo(UUID moneda,String codigo,BigDecimal actual){SaldoCuentaProjection p=mock(SaldoCuentaProjection.class);
        when(p.getCuentaId()).thenReturn(UUID.randomUUID());when(p.getCuenta()).thenReturn("Cuenta");when(p.getMonedaId()).thenReturn(moneda);
        when(p.getMoneda()).thenReturn(codigo);when(p.getSimbolo()).thenReturn("$");when(p.getActivo()).thenReturn(true);
        when(p.getSaldoInicial()).thenReturn(BigDecimal.ZERO);when(p.getEntradas()).thenReturn(actual);when(p.getSalidas()).thenReturn(BigDecimal.ZERO);when(p.getSaldoActual()).thenReturn(actual);return p;}
    private TotalSaldoMonedaProjection total(UUID moneda,String codigo,BigDecimal amount){TotalSaldoMonedaProjection p=mock(TotalSaldoMonedaProjection.class);
        when(p.getMonedaId()).thenReturn(moneda);when(p.getMoneda()).thenReturn(codigo);when(p.getTotal()).thenReturn(amount);return p;}
    private Moneda moneda(UUID id,String codigo,boolean activa){Moneda m=mock(Moneda.class);when(m.getId()).thenReturn(id);when(m.getCodigoIso()).thenReturn(codigo);when(m.getSimbolo()).thenReturn("DOP".equals(codigo)?"RD$":"US$");when(m.isActivo()).thenReturn(activa);return m;}
}
