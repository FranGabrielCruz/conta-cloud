package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ConciliacionBancariaMapper;
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

class BankReconciliationServiceTest {
    private final BankReconciliationRepository reconciliaciones=mock(BankReconciliationRepository.class);
    private final BankStatementMovementRepository bancos=mock(BankStatementMovementRepository.class);
    private final BankReconciliationMatchRepository asociaciones=mock(BankReconciliationMatchRepository.class);
    private final FinancialMovementRepository financieros=mock(FinancialMovementRepository.class);
    private final BankAccountRepository cuentas=mock(BankAccountRepository.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final BankReconciliationService service=new BankReconciliationService(reconciliaciones,bancos,asociaciones,
        financieros,cuentas,new ConciliacionBancariaMapper(),auditoria);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
    private final UUID cuentaId=UUID.randomUUID(),monedaId=UUID.randomUUID(),reconciliacionId=UUID.randomUUID();
    private CuentaBancaria cuenta;private Moneda moneda;

    @BeforeEach void preparar(){var principal=new TenantPrincipal(usuario,tenant,empresa,null,"DEMO","Admin","admin","",true,true,
        Set.of(),Set.of("conciliacion_bancaria.ver","conciliacion_bancaria.crear","conciliacion_bancaria.conciliar","conciliacion_bancaria.finalizar","conciliacion_bancaria.anular"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        moneda=new Moneda(empresa,"DOP","Peso dominicano","RD$",(short)2);ReflectionTestUtils.setField(moneda,"id",monedaId);
        cuenta=mock(CuentaBancaria.class);when(cuenta.getId()).thenReturn(cuentaId);when(cuenta.getTenantId()).thenReturn(tenant);
        when(cuenta.getEmpresaId()).thenReturn(empresa);when(cuenta.getMonedaId()).thenReturn(monedaId);when(cuenta.getMoneda()).thenReturn(moneda);
        when(cuenta.getBancoNombre()).thenReturn("Banco Demo");when(cuenta.getNombreCuenta()).thenReturn("Principal");when(cuenta.isActivo()).thenReturn(true);
        when(cuentas.bloquearParaConciliacion(cuentaId,tenant,empresa)).thenReturn(Optional.of(cuenta));
        when(reconciliaciones.saveAndFlush(any())).thenAnswer(i->{ConciliacionBancaria r=i.getArgument(0);if(r.getId()==null)ReflectionTestUtils.setField(r,"id",reconciliacionId);return r;});
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void creaEnProcesoConMonedaDerivadaDeCuenta(){var dto=service.crear(input());
        assertThat(dto.estado()).isEqualTo(EstadoConciliacionBancaria.IN_PROGRESS);assertThat(dto.monedaId()).isEqualTo(monedaId);
        verify(auditoria).registrar(eq("BANK_RECONCILIATION_CREATED"),eq("ConciliacionBancaria"),eq(reconciliacionId),anyString());}

    @Test void rechazaPeriodoSolapado(){when(reconciliaciones.existeSolapamiento(tenant,empresa,cuentaId,
        LocalDate.of(2026,9,1),LocalDate.of(2026,9,30))).thenReturn(true);
        assertThatThrownBy(()->service.crear(input())).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Ya existe una conciliación que se superpone con este período.");verify(reconciliaciones,never()).saveAndFlush(any());}

    @Test void rechazaFechasInvertidasAntesDeConsultarCuenta(){var i=new ConciliacionBancariaInput(cuentaId,
        LocalDate.of(2026,10,1),LocalDate.of(2026,9,30),BigDecimal.ZERO,BigDecimal.ZERO);
        assertThatThrownBy(()->service.crear(i)).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La fecha inicial no puede ser posterior a la fecha final.");verifyNoInteractions(cuentas);}

    @Test void agregarLineaBancariaNoCreaMovimientoFinanciero(){ConciliacionBancaria r=reconciliacion();
        when(reconciliaciones.bloquear(reconciliacionId,tenant,empresa)).thenReturn(Optional.of(r));
        when(bancos.saveAndFlush(any())).thenAnswer(i->{MovimientoEstadoBancario b=i.getArgument(0);ReflectionTestUtils.setField(b,"id",UUID.randomUUID());return b;});
        service.agregarMovimientoBanco(reconciliacionId,new MovimientoEstadoBancarioInput(LocalDate.of(2026,9,25),
            DireccionMovimientoBancario.OUTFLOW,"Comisión","COM-9",new BigDecimal("500")));
        verify(bancos).saveAndFlush(any());verify(financieros,never()).save(any());}

    @Test void rechazaAsociarDireccionesDiferentes(){ConciliacionBancaria r=reconciliacion();UUID fid=UUID.randomUUID(),bid=UUID.randomUUID();
        when(reconciliaciones.bloquear(reconciliacionId,tenant,empresa)).thenReturn(Optional.of(r));
        MovimientoFinanciero f=new MovimientoFinanciero(tenant,empresa,TipoMovimientoFinanciero.INCOME,LocalDate.of(2026,9,3),
            TipoCuentaDinero.BANK_ACCOUNT,cuentaId,monedaId,new BigDecimal("100"),"Aporte",null,null,usuario);ReflectionTestUtils.setField(f,"id",fid);
        MovimientoEstadoBancario b=new MovimientoEstadoBancario(tenant,empresa,reconciliacionId,cuentaId,LocalDate.of(2026,9,3),
            DireccionMovimientoBancario.OUTFLOW,"Débito",null,new BigDecimal("100"),usuario);ReflectionTestUtils.setField(b,"id",bid);
        when(financieros.findByIdAndTenantIdAndEmpresaIdAndTipoCuentaAndCuentaBancariaId(fid,tenant,empresa,TipoCuentaDinero.BANK_ACCOUNT,cuentaId)).thenReturn(Optional.of(f));
        when(bancos.findByIdAndTenantIdAndEmpresaIdAndConciliacionId(bid,tenant,empresa,reconciliacionId)).thenReturn(Optional.of(b));
        assertThatThrownBy(()->service.conciliar(reconciliacionId,fid,bid)).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Las direcciones de los movimientos no son compatibles.");verify(asociaciones,never()).saveAndFlush(any());}

    @Test void rechazaFinalizarConDiferencia(){ConciliacionBancaria r=reconciliacion();
        when(reconciliaciones.bloquear(reconciliacionId,tenant,empresa)).thenReturn(Optional.of(r));
        when(cuentas.findByIdAndTenantIdAndEmpresaId(cuentaId,tenant,empresa)).thenReturn(Optional.of(cuenta));
        when(bancos.findAllByTenantIdAndEmpresaIdAndConciliacionId(tenant,empresa,reconciliacionId)).thenReturn(List.of());
        when(asociaciones.findAllByTenantIdAndEmpresaIdAndConciliacionId(tenant,empresa,reconciliacionId)).thenReturn(List.of());
        when(financieros.candidatosConciliacion(eq(tenant),eq(empresa),eq(cuentaId),eq(monedaId),any(),any()))
            .thenReturn(org.springframework.data.domain.Page.empty());
        assertThatThrownBy(()->service.finalizar(reconciliacionId)).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La conciliación presenta diferencias y no puede finalizarse.");}

    private ConciliacionBancariaInput input(){return new ConciliacionBancariaInput(cuentaId,LocalDate.of(2026,9,1),
        LocalDate.of(2026,9,30),new BigDecimal("100000"),new BigDecimal("142500"));}
    private ConciliacionBancaria reconciliacion(){ConciliacionBancaria r=new ConciliacionBancaria(tenant,empresa,cuentaId,monedaId,
        LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),new BigDecimal("100000"),new BigDecimal("142500"),usuario);
        ReflectionTestUtils.setField(r,"id",reconciliacionId);r.asignarRelaciones(cuenta,moneda);return r;}
}
