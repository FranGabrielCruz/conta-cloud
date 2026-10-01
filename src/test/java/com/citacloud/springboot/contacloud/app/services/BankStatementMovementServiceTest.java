package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.MovimientoEstadoBancarioInput;
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

class BankStatementMovementServiceTest {
    private final BankReconciliationRepository reconciliaciones=mock(BankReconciliationRepository.class);
    private final BankStatementMovementRepository movimientos=mock(BankStatementMovementRepository.class);
    private final BankReconciliationMatchRepository asociaciones=mock(BankReconciliationMatchRepository.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final BankStatementMovementService service=new BankStatementMovementService(reconciliaciones,movimientos,
        asociaciones,new ConciliacionBancariaMapper(),auditoria);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
    private final UUID conciliacionId=UUID.randomUUID(),movimientoId=UUID.randomUUID(),cuentaId=UUID.randomUUID(),monedaId=UUID.randomUUID();
    private ConciliacionBancaria conciliacion;private MovimientoEstadoBancario movimiento;

    @BeforeEach void preparar(){
        autenticar(tenant,empresa);Moneda moneda=new Moneda(empresa,"DOP","Peso dominicano","RD$",(short)2);
        ReflectionTestUtils.setField(moneda,"id",monedaId);CuentaBancaria cuenta=mock(CuentaBancaria.class);
        conciliacion=new ConciliacionBancaria(tenant,empresa,cuentaId,monedaId,LocalDate.of(2026,9,1),
            LocalDate.of(2026,9,30),BigDecimal.ZERO,BigDecimal.ZERO,usuario);
        ReflectionTestUtils.setField(conciliacion,"id",conciliacionId);conciliacion.asignarRelaciones(cuenta,moneda);
        movimiento=new MovimientoEstadoBancario(tenant,empresa,conciliacionId,cuentaId,LocalDate.of(2026,9,15),
            DireccionMovimientoBancario.INFLOW,"Depósito","DEP-1",new BigDecimal("1200.00"),usuario);
        ReflectionTestUtils.setField(movimiento,"id",movimientoId);
        when(reconciliaciones.bloquear(conciliacionId,tenant,empresa)).thenReturn(Optional.of(conciliacion));
        when(movimientos.findByIdAndTenantIdAndEmpresaIdAndConciliacionId(movimientoId,tenant,empresa,conciliacionId))
            .thenReturn(Optional.of(movimiento));
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void actualizaSoloCamposCorregiblesYAudita(){
        var resultado=service.actualizar(conciliacionId,movimientoId,new MovimientoEstadoBancarioInput(
            LocalDate.of(2026,9,20),DireccionMovimientoBancario.OUTFLOW,"  Servicio  "," REF-2 ",new BigDecimal("1500")));
        assertThat(resultado.fecha()).isEqualTo(LocalDate.of(2026,9,20));assertThat(resultado.direccion()).isEqualTo(DireccionMovimientoBancario.OUTFLOW);
        assertThat(resultado.descripcion()).isEqualTo("Servicio");assertThat(resultado.referencia()).isEqualTo("REF-2");
        assertThat(resultado.monto()).isEqualByComparingTo("1500.00");assertThat(movimiento.getCuentaBancariaId()).isEqualTo(cuentaId);
        verify(movimientos).saveAndFlush(movimiento);verify(auditoria).registrar(eq("BANK_RECONCILIATION_BANK_MOVEMENT_UPDATED"),
            eq("MovimientoEstadoBancario"),eq(movimientoId),contains("\"anterior\""));
    }

    @Test void rechazaFechaFueraDelPeriodo(){
        assertThatThrownBy(()->service.actualizar(conciliacionId,movimientoId,new MovimientoEstadoBancarioInput(
            LocalDate.of(2026,10,1),DireccionMovimientoBancario.INFLOW,"Depósito",null,BigDecimal.ONE)))
            .isInstanceOf(ReglaNegocioException.class).hasMessage("La fecha del movimiento debe pertenecer al período de conciliación.");
        verify(movimientos,never()).saveAndFlush(any());
    }

    @Test void rechazaEditarMovimientoConciliado(){
        when(asociaciones.existsByMovimientoBancarioId(movimientoId)).thenReturn(true);
        assertThatThrownBy(()->service.actualizar(conciliacionId,movimientoId,inputValido()))
            .isInstanceOf(ReglaNegocioException.class).hasMessage("El movimiento bancario ya fue conciliado y no puede modificarse.");
    }

    @Test void eliminaMovimientoManualPendienteSinTocarMovimientosFinancieros(){
        service.eliminar(conciliacionId,movimientoId);verify(auditoria).registrar(eq("BANK_RECONCILIATION_BANK_MOVEMENT_DELETED"),
            eq("MovimientoEstadoBancario"),eq(movimientoId),contains(conciliacionId.toString()));
        verify(movimientos).delete(movimiento);verify(movimientos).flush();
    }

    @Test void rechazaEliminarMovimientoConciliado(){
        when(asociaciones.existsByMovimientoBancarioId(movimientoId)).thenReturn(true);
        assertThatThrownBy(()->service.eliminar(conciliacionId,movimientoId)).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("El movimiento bancario ya fue conciliado y no puede eliminarse.");verify(movimientos,never()).delete(any());
    }

    @Test void conciliacionFinalizadaEsSoloLectura(){
        conciliacion.finalizar(usuario);
        assertThatThrownBy(()->service.actualizar(conciliacionId,movimientoId,inputValido())).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La conciliación está finalizada y no puede modificarse.");
        assertThatThrownBy(()->service.eliminar(conciliacionId,movimientoId)).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La conciliación está finalizada y no puede modificarse.");
    }

    @Test void consultaSiempreConTenantYEmpresaDelContexto(){
        UUID otroTenant=UUID.randomUUID(),otraEmpresa=UUID.randomUUID();autenticar(otroTenant,otraEmpresa);
        assertThatThrownBy(()->service.eliminar(conciliacionId,movimientoId)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(reconciliaciones).bloquear(conciliacionId,otroTenant,otraEmpresa);verify(movimientos,never()).delete(any());
    }

    private MovimientoEstadoBancarioInput inputValido(){return new MovimientoEstadoBancarioInput(LocalDate.of(2026,9,20),
        DireccionMovimientoBancario.INFLOW,"Depósito corregido",null,new BigDecimal("1500"));}
    private void autenticar(UUID tenantId,UUID empresaId){var principal=new TenantPrincipal(usuario,tenantId,empresaId,null,
        "DEMO","Administrador","admin","",true,true,Set.of(),Set.of("conciliacion_bancaria.movimiento_banco_editar",
        "conciliacion_bancaria.movimiento_banco_eliminar"));SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
}
