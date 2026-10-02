package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.MovimientoFinancieroInput;
import com.citacloud.springboot.contacloud.app.mappers.MovimientoFinancieroMapper;
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
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialMovementServiceTest {
    private final FinancialMovementRepository repository=mock(FinancialMovementRepository.class);
    private final CashRegisterRepository cajas=mock(CashRegisterRepository.class);
    private final BankAccountRepository cuentas=mock(BankAccountRepository.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final CashRegisterSessionService sesionesCaja=mock(CashRegisterSessionService.class);
    private final CuentaDineroResolver cuentaResolver=new CuentaDineroResolver(cajas,cuentas);
    private final FinancialMovementService service=new FinancialMovementService(repository,cuentaResolver,
        new MovimientoFinancieroMapper(),auditoria,sesionesCaja);
    private final UUID tenantId=UUID.randomUUID(),empresaId=UUID.randomUUID(),usuarioId=UUID.randomUUID();
    private final UUID sucursalId=UUID.randomUUID(),cajaId=UUID.randomUUID(),monedaId=UUID.randomUUID();
    private final UUID cuentaBancariaId=UUID.randomUUID();
    private Caja caja; private Moneda moneda; private final AtomicReference<MovimientoFinanciero> guardado=new AtomicReference<>();

    @BeforeEach void preparar(){
        var principal=new TenantPrincipal(usuarioId,tenantId,empresaId,null,"DEMO","Administrador","admin","",true,
            true,Set.of(),Set.of("ingresos.ver","ingresos.crear","ingresos.editar","ingresos.anular","egresos.ver","egresos.crear","egresos.editar","egresos.anular"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        moneda=mock(Moneda.class);when(moneda.getId()).thenReturn(monedaId);when(moneda.getCodigoIso()).thenReturn("DOP");
        caja=new Caja(tenantId,empresaId,sucursalId,monedaId,"CAJ-1","Caja principal",null,true,usuarioId);
        ReflectionTestUtils.setField(caja,"id",cajaId);caja.asignarMoneda(moneda);
        Sucursal sucursal=mock(Sucursal.class);when(sucursal.getId()).thenReturn(sucursalId);when(sucursal.getNombre()).thenReturn("Principal");
        caja.asignarSucursal(sucursal);when(cajas.findByIdAndTenantIdAndEmpresaId(cajaId,tenantId,empresaId)).thenReturn(Optional.of(caja));
        when(sesionesCaja.requerirSesionAbierta(cajaId)).thenReturn(UUID.randomUUID());
        when(repository.saveAndFlush(any())).thenAnswer(inv->{MovimientoFinanciero m=inv.getArgument(0);
            if(m.getId()==null)ReflectionTestUtils.setField(m,"id",UUID.randomUUID());
            guardado.set(m);return m;});
        when(repository.findByIdAndTenantIdAndEmpresaIdAndTipoMovimiento(any(),eq(tenantId),eq(empresaId),any()))
            .thenAnswer(inv->{MovimientoFinanciero m=guardado.get();return m!=null&&m.getTipoMovimiento()==inv.getArgument(3)?Optional.of(m):Optional.empty();});
        when(repository.buscarParaActualizar(any(),eq(tenantId),eq(empresaId),any()))
            .thenAnswer(inv->{MovimientoFinanciero m=guardado.get();return m!=null&&m.getTipoMovimiento()==inv.getArgument(3)?Optional.of(m):Optional.empty();});
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void creaIngresoManualConMonedaDerivadaDeLaCaja(){
        var dto=service.crearIngreso(entrada(new BigDecimal("1250.50")));
        assertThat(dto.monedaId()).isEqualTo(monedaId);assertThat(dto.monedaCodigo()).isEqualTo("DOP");
        assertThat(dto.estado()).isEqualTo(EstadoMovimientoFinanciero.REGISTERED);
        assertThat(dto.tipoOrigen()).isEqualTo(TipoOrigenMovimiento.MANUAL);
        verify(auditoria).registrar(eq("FINANCIAL_INCOME_CREATED"),eq("MovimientoFinanciero"),any(),anyString());
    }

    @Test void saldoAperturaPositivoGeneraEntradaConMontoPositivo(){
        CuentaBancaria bancaria=cuentaBancaria();
        service.registrarSaldoApertura(bancaria,LocalDate.of(2026,10,2),new BigDecimal("250000.00"));
        assertThat(guardado.get().getTipoOrigen()).isEqualTo(TipoOrigenMovimiento.OPENING_BALANCE);
        assertThat(guardado.get().getTipoMovimiento()).isEqualTo(TipoMovimientoFinanciero.INCOME);
        assertThat(guardado.get().getMonto()).isEqualByComparingTo("250000.00");
    }

    @Test void saldoAperturaNegativoGeneraSalidaConMontoAbsoluto(){
        CuentaBancaria bancaria=cuentaBancaria();
        service.registrarSaldoApertura(bancaria,LocalDate.of(2026,10,2),new BigDecimal("-25000.00"));
        assertThat(guardado.get().getTipoMovimiento()).isEqualTo(TipoMovimientoFinanciero.EXPENSE);
        assertThat(guardado.get().getMonto()).isEqualByComparingTo("25000.00");
    }

    @Test void saldoAperturaCeroNoGeneraMovimiento(){
        service.registrarSaldoApertura(cuentaBancaria(),LocalDate.of(2026,10,2),BigDecimal.ZERO);
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void creaEgresoBancarioAunqueElRepositorioDevuelvaLaMismaEntidadGestionada(){
        CuentaBancaria bancaria=mock(CuentaBancaria.class);
        when(bancaria.getId()).thenReturn(cuentaBancariaId);when(bancaria.getMonedaId()).thenReturn(monedaId);
        when(bancaria.getMoneda()).thenReturn(moneda);when(bancaria.getBancoNombre()).thenReturn("Banco Demo");
        when(bancaria.getNombreCuenta()).thenReturn("Operativa");when(bancaria.isActivo()).thenReturn(true);
        when(cuentas.findByIdAndTenantIdAndEmpresaId(cuentaBancariaId,tenantId,empresaId)).thenReturn(Optional.of(bancaria));

        var dto=service.crearEgreso(new MovimientoFinancieroInput(LocalDate.now(),TipoCuentaDinero.BANK_ACCOUNT,
            cuentaBancariaId,"Pago de servicio",new BigDecimal("1500.00"),null,null));

        assertThat(dto.cuentaNombre()).isEqualTo("Banco Demo · Operativa");
        assertThat(dto.monedaCodigo()).isEqualTo("DOP");
        assertThat(dto.tipoCuenta()).isEqualTo(TipoCuentaDinero.BANK_ACCOUNT);
    }

    private CuentaBancaria cuentaBancaria(){
        CuentaBancaria bancaria=mock(CuentaBancaria.class);when(bancaria.getId()).thenReturn(cuentaBancariaId);
        when(bancaria.getMonedaId()).thenReturn(monedaId);when(bancaria.getMoneda()).thenReturn(moneda);return bancaria;
    }

    @Test void rechazaMontoNoPositivo(){
        assertThatThrownBy(()->service.crearEgreso(entrada(BigDecimal.ZERO))).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("El monto debe ser mayor que cero.");
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void rechazaCajaDeOtroTenantEmpresaOInactiva(){
        when(cajas.findByIdAndTenantIdAndEmpresaId(cajaId,tenantId,empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.crearIngreso(entrada(BigDecimal.TEN))).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La caja seleccionada no es válida o está inactiva.");
    }

    @Test void anulaSinEliminarYConservaMotivo(){
        var creado=service.crearEgreso(entrada(new BigDecimal("50.00")));
        var anulado=service.anularEgreso(creado.id(),"Comprobante duplicado");
        assertThat(anulado.estado()).isEqualTo(EstadoMovimientoFinanciero.VOIDED);
        assertThat(anulado.motivoAnulacion()).isEqualTo("Comprobante duplicado");
        assertThat(anulado.anuladoEn()).isNotNull();verify(repository,never()).delete(any());
        verify(auditoria).registrar(eq("FINANCIAL_EXPENSE_VOIDED"),eq("MovimientoFinanciero"),eq(creado.id()),anyString());
    }

    @Test void anulaMovimientoCargadoConRelacionesOpcionalesSinRequerirJoinEnElBloqueo(){
        var creado=service.crearEgreso(entrada(new BigDecimal("50.00")));

        var anulado=service.anularEgreso(creado.id(),"Registro incorrecto");

        assertThat(anulado.estado()).isEqualTo(EstadoMovimientoFinanciero.VOIDED);
        assertThat(anulado.cuentaNombre()).isEqualTo("Caja principal");
        assertThat(anulado.monedaCodigo()).isEqualTo("DOP");
        verify(repository).buscarParaActualizar(creado.id(),tenantId,empresaId,TipoMovimientoFinanciero.EXPENSE);
    }

    @Test void movimientoAnuladoNoPuedeEditarse(){
        var creado=service.crearIngreso(entrada(BigDecimal.ONE));service.anularIngreso(creado.id(),"Error de captura");
        assertThatThrownBy(()->service.actualizarIngreso(creado.id(),entrada(BigDecimal.TEN)))
            .isInstanceOf(ReglaNegocioException.class).hasMessage("No se puede editar un movimiento anulado.");
    }

    @Test void noPermiteModificarElMontoDeUnIngresoRegistrado(){
        var creado=service.crearIngreso(entrada(new BigDecimal("100.00")));

        assertThatThrownBy(()->service.actualizarIngreso(creado.id(),entrada(new BigDecimal("125.00"))))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("El monto de un movimiento registrado no puede modificarse.");
    }

    @Test void noPermiteModificarElMontoDeUnEgresoRegistrado(){
        var creado=service.crearEgreso(entrada(new BigDecimal("100.00")));

        assertThatThrownBy(()->service.actualizarEgreso(creado.id(),entrada(new BigDecimal("125.00"))))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("El monto de un movimiento registrado no puede modificarse.");
    }

    @Test void noExponeMovimientoDeOtroTenantOEmpresa(){
        UUID id=UUID.randomUUID();guardado.set(null);
        assertThatThrownBy(()->service.obtenerIngreso(id)).isInstanceOf(RecursoNoEncontradoException.class)
            .hasMessage("Movimiento no encontrado.");
        verify(repository).findByIdAndTenantIdAndEmpresaIdAndTipoMovimiento(id,tenantId,empresaId,TipoMovimientoFinanciero.INCOME);
    }

    private MovimientoFinancieroInput entrada(BigDecimal monto){return new MovimientoFinancieroInput(LocalDate.now(),
        TipoCuentaDinero.CASH_REGISTER,cajaId,"Venta de contado",monto,"REF-1","Ingreso manual");}
}
