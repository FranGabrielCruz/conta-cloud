package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.SesionCajaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CashRegisterSessionServiceTest {
    private final CashRegisterSessionRepository sessions=mock(CashRegisterSessionRepository.class);
    private final CashRegisterRepository cajas=mock(CashRegisterRepository.class);
    private final FinancialMovementRepository movimientos=mock(FinancialMovementRepository.class);
    private final EmpresaRepository empresas=mock(EmpresaRepository.class);
    private final SucursalRepository sucursales=mock(SucursalRepository.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final CashRegisterSessionService service=new CashRegisterSessionService(sessions,cajas,movimientos,
        empresas,new SesionCajaMapper(),auditoria,sucursales);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
    private final UUID cajaId=UUID.randomUUID(),sucursalId=UUID.randomUUID(),monedaId=UUID.randomUUID();
    private Caja caja;private Sucursal sucursal;private Moneda moneda;

    @BeforeEach void preparar(){
        var principal=new TenantPrincipal(usuario,tenant,empresa,null,"DEMO","Administrador","admin","",true,true,
            Set.of(),Set.of("operaciones_caja.ver","operaciones_caja.abrir","operaciones_caja.cerrar",
            "operaciones_caja.ver_historial","operaciones_caja.revisar_cierre"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        sucursal=mock(Sucursal.class);when(sucursal.getId()).thenReturn(sucursalId);when(sucursal.getNombre()).thenReturn("Principal");
        moneda=mock(Moneda.class);when(moneda.getId()).thenReturn(monedaId);when(moneda.getCodigoIso()).thenReturn("DOP");
        caja=new Caja(tenant,empresa,sucursalId,monedaId,"CAJ-01","Caja principal",null,true,usuario);
        ReflectionTestUtils.setField(caja,"id",cajaId);caja.asignarSucursal(sucursal);caja.asignarMoneda(moneda);
        when(cajas.bloquearParaOperacion(cajaId,tenant,empresa)).thenReturn(Optional.of(caja));
        Empresa e=mock(Empresa.class);when(e.getZonaHoraria()).thenReturn("America/Santo_Domingo");
        when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(e));
        when(sessions.findByTenantIdAndEmpresaIdAndCajaIdAndStatus(tenant,empresa,cajaId,EstadoSesionCaja.OPEN)).thenReturn(Optional.empty());
        when(sessions.findByTenantIdAndEmpresaIdAndAbiertoPorAndStatus(tenant,empresa,usuario,EstadoSesionCaja.OPEN)).thenReturn(Optional.empty());
        when(sessions.saveAndFlush(any())).thenAnswer(inv->{SesionCaja s=inv.getArgument(0);if(s.getId()==null)ReflectionTestUtils.setField(s,"id",UUID.randomUUID());return s;});
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void abreTurnoConNumeroDiarioYFondoInicial(){
        when(sessions.ultimoTurno(eq(tenant),eq(empresa),eq(cajaId),any())).thenReturn(2);
        SesionCajaDto dto=service.abrir(cajaId,new AperturaCajaInput(new BigDecimal("500.00"),"Inicio"));
        assertThat(dto.numeroTurno()).isEqualTo(3);assertThat(dto.codigoVisible()).startsWith("TURNO-3-").endsWith("-CAJ-01");
        assertThat(dto.fondoInicial()).isEqualByComparingTo("500.00");assertThat(dto.estado()).isEqualTo(EstadoSesionCaja.OPEN);
        verify(auditoria).registrar(eq("CASH_REGISTER_OPENED"),eq("SesionCaja"),any(),anyString());
    }

    @Test void impideAbrirCuandoElUsuarioYaTieneOtroTurno(){
        SesionCaja existente=mock(SesionCaja.class);
        when(sessions.findByTenantIdAndEmpresaIdAndAbiertoPorAndStatus(tenant,empresa,usuario,EstadoSesionCaja.OPEN))
            .thenReturn(Optional.of(existente));
        assertThatThrownBy(()->service.abrir(cajaId,new AperturaCajaInput(BigDecimal.ZERO,null)))
            .hasMessage("Ya tienes un turno de caja abierto.");
        verify(sessions,never()).saveAndFlush(any());
    }

    @Test void cierraConArqueoCalculadoSoloConEfectivo(){
        SesionCaja sesion=new SesionCaja(tenant,empresa,caja,java.time.LocalDate.now(),1,"TURNO-1",new BigDecimal("100"),null,usuario);
        UUID sesionId=UUID.randomUUID();ReflectionTestUtils.setField(sesion,"id",sesionId);
        when(sessions.bloquear(sesionId,tenant,empresa)).thenReturn(Optional.of(sesion));
        MovimientoFinanciero ingreso=movimiento(TipoMovimientoFinanciero.INCOME,MedioPagoMovimiento.CASH,"50");
        MovimientoFinanciero tarjeta=movimiento(TipoMovimientoFinanciero.INCOME,MedioPagoMovimiento.CARD,"80");
        MovimientoFinanciero egreso=movimiento(TipoMovimientoFinanciero.EXPENSE,MedioPagoMovimiento.CASH,"20");
        when(movimientos.movimientosValidosSesion(tenant,empresa,sesionId)).thenReturn(List.of(ingreso,tarjeta,egreso));
        SesionCajaDto dto=service.cerrar(sesionId,new CierreCajaInput(new BigDecimal("125"),"Arqueo"));
        assertThat(dto.efectivoEsperado()).isEqualByComparingTo("130");
        assertThat(dto.diferencia()).isEqualByComparingTo("-5");assertThat(dto.estado()).isEqualTo(EstadoSesionCaja.CLOSED);
    }

    @Test void exigeTurnoAbiertoParaRegistrarMovimiento(){
        assertThatThrownBy(()->service.requerirSesionAbierta(cajaId))
            .hasMessage("La caja seleccionada está cerrada. Debes abrirla antes de registrar movimientos.");
    }

    @Test void apruebaCierrePendienteYRegistraAuditoriaEspecifica(){
        SesionCaja sesion=sesionCerrada(EstadoRevisionCaja.PENDING);
        when(sessions.bloquear(sesion.getId(),tenant,empresa)).thenReturn(Optional.of(sesion));

        SesionCajaDto resultado=service.revisar(sesion.getId(),new RevisionCierreCajaInput(EstadoRevisionCaja.APPROVED,null));

        assertThat(resultado.estadoRevision()).isEqualTo(EstadoRevisionCaja.APPROVED);
        verify(auditoria).registrar(eq("CASH_REGISTER_SESSION_APPROVED"),eq("SesionCaja"),eq(sesion.getId()),anyString());
    }

    @Test void permiteVolverARevisarCuandoRequiereRevision(){
        SesionCaja sesion=sesionCerrada(EstadoRevisionCaja.REQUIRES_REVIEW);
        when(sessions.bloquear(sesion.getId(),tenant,empresa)).thenReturn(Optional.of(sesion));

        service.revisar(sesion.getId(),new RevisionCierreCajaInput(EstadoRevisionCaja.APPROVED,"Corregido"));

        assertThat(sesion.getEstadoRevision()).isEqualTo(EstadoRevisionCaja.APPROVED);
    }

    @Test void rechazaSegundaRevisionDeUnCierreAprobadoSinModificarlo(){
        SesionCaja sesion=sesionCerrada(EstadoRevisionCaja.APPROVED);
        UUID revisor=UUID.randomUUID();ReflectionTestUtils.setField(sesion,"revisadoPor",revisor);
        ReflectionTestUtils.setField(sesion,"observacionRevision","Aprobado inicialmente");
        when(sessions.bloquear(sesion.getId(),tenant,empresa)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(()->service.revisar(sesion.getId(),new RevisionCierreCajaInput(EstadoRevisionCaja.REQUIRES_REVIEW,"Cambiar")))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Este cierre ya fue aprobado y no admite nuevas revisiones.");
        assertThat(sesion.getEstadoRevision()).isEqualTo(EstadoRevisionCaja.APPROVED);
        assertThat(sesion.getRevisadoPor()).isEqualTo(revisor);
        assertThat(sesion.getObservacionRevision()).isEqualTo("Aprobado inicialmente");
        verify(sessions,never()).saveAndFlush(sesion);
        verify(auditoria,never()).registrar(startsWith("CASH_REGISTER_SESSION_"),anyString(),any(),anyString());
    }

    private SesionCaja sesionCerrada(EstadoRevisionCaja estadoRevision){
        SesionCaja sesion=new SesionCaja(tenant,empresa,caja,java.time.LocalDate.now(),1,"TURNO-1",new BigDecimal("100"),null,usuario);
        ReflectionTestUtils.setField(sesion,"id",UUID.randomUUID());
        ReflectionTestUtils.setField(sesion,"status",EstadoSesionCaja.CLOSED);
        ReflectionTestUtils.setField(sesion,"estadoRevision",estadoRevision);
        return sesion;
    }

    private MovimientoFinanciero movimiento(TipoMovimientoFinanciero tipo,MedioPagoMovimiento medio,String monto){
        MovimientoFinanciero m=new MovimientoFinanciero(tenant,empresa,tipo,java.time.LocalDate.now(),
            TipoCuentaDinero.CASH_REGISTER,cajaId,monedaId,new BigDecimal(monto),"Prueba",null,null,medio,UUID.randomUUID(),usuario);
        return m;
    }
}
