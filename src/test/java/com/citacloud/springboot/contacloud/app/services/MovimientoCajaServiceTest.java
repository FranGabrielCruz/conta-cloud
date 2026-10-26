package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MovimientoCajaServiceTest {
    private final CashRegisterSessionRepository sesiones=mock(CashRegisterSessionRepository.class);
    private final FinancialMovementRepository movimientos=mock(FinancialMovementRepository.class);
    private final ConceptoMovimientoCajaRepository conceptos=mock(ConceptoMovimientoCajaRepository.class);
    private final IdempotentOperationService idempotencia=mock(IdempotentOperationService.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final MovimientoCajaService service=new MovimientoCajaService(sesiones,movimientos,conceptos,idempotencia,auditoria,jdbc);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
    private final UUID sesionId=UUID.randomUUID(),cajaId=UUID.randomUUID(),monedaId=UUID.randomUUID(),sucursalId=UUID.randomUUID();

    @BeforeEach void preparar(){
        var principal=new TenantPrincipal(usuario,tenant,empresa,null,"DEMO","Admin","admin","",true,true,Set.of(),
            Set.of("movimientos_caja.ver","movimientos_caja.crear","movimientos_caja.revertir"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        when(idempotencia.begin(any(),any(),anyString(),any(),anyString())).thenReturn(new IdempotentOperationService.Start(true,null,null));
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(1L);
        when(movimientos.saveAndFlush(any())).thenAnswer(i->{MovimientoFinanciero m=i.getArgument(0);
            ReflectionTestUtils.setField(m,"id",UUID.randomUUID());return m;});
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void registraSalidaManualEnLaFechaOperativaDelTurno(){
        SesionCaja sesion=sesionAbierta();ConceptoMovimientoCaja concepto=concepto(TipoMovimientoFinanciero.EXPENSE,true);
        when(sesiones.bloquear(sesionId,tenant,empresa)).thenReturn(Optional.of(sesion));
        when(conceptos.findByIdAndTenantIdAndEmpresaIdAndActiveTrue(any(),eq(tenant),eq(empresa))).thenReturn(Optional.of(concepto));
        MovimientoCajaProjection fila=proyeccion();
        when(movimientos.detalleMovimientoCaja(eq(tenant),eq(empresa),eq(sesionId),any())).thenReturn(Optional.of(fila));

        MovimientoCajaDto resultado=service.crear(new MovimientoCajaInput(sesionId,TipoMovimientoFinanciero.EXPENSE,concepto.getId(),
            new BigDecimal("500"),"AUT-25","Entrega autorizada",UUID.randomUUID()));

        assertThat(resultado.fechaHora()).isNotNull();
        verify(movimientos).saveAndFlush(argThat(m->m.getTipoMovimiento()==TipoMovimientoFinanciero.EXPENSE
            &&m.getTipoOrigen()==TipoOrigenMovimiento.MANUAL&&m.getFecha().equals(LocalDate.of(2026,10,26))
            &&m.getMonto().compareTo(new BigDecimal("500.0000"))==0&&m.getSesionCajaId().equals(sesionId)));
        verify(idempotencia).complete(eq(tenant),eq(empresa),eq("CASH_MOVEMENT_CREATE"),any(),eq("CASH_MOVEMENT"),any());
    }

    @Test void rechazaImportesNoPositivosAntesDePersistir(){
        assertThatThrownBy(()->service.crear(new MovimientoCajaInput(sesionId,TipoMovimientoFinanciero.INCOME,
            UUID.randomUUID(),BigDecimal.ZERO,null,null,UUID.randomUUID())))
            .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mayor que cero");
        verifyNoInteractions(sesiones,movimientos,conceptos);
    }

    @Test void unMovimientoAutomaticoSoloPuedeRevertirseDesdeSuOrigen(){
        SesionCaja sesion=sesionAbierta();when(sesiones.bloquear(sesionId,tenant,empresa)).thenReturn(Optional.of(sesion));
        MovimientoFinanciero automatico=MovimientoFinanciero.ventaContado(tenant,empresa,LocalDate.of(2026,10,26),
            cajaId,monedaId,new BigDecimal("100"),"FAC-000001",UUID.randomUUID(),sesionId,usuario);
        UUID movimientoId=UUID.randomUUID();ReflectionTestUtils.setField(automatico,"id",movimientoId);
        when(movimientos.bloquearMovimientoCaja(movimientoId,tenant,empresa,sesionId)).thenReturn(Optional.of(automatico));

        assertThatThrownBy(()->service.revertir(new ReversionMovimientoCajaInput(sesionId,movimientoId,
            "Corrección",UUID.randomUUID()))).isInstanceOf(ReglaNegocioException.class)
            .hasMessageContaining("documento de origen");
        verify(movimientos,never()).saveAndFlush(any());
    }

    private SesionCaja sesionAbierta(){SesionCaja s=mock(SesionCaja.class);when(s.getId()).thenReturn(sesionId);
        when(s.getStatus()).thenReturn(EstadoSesionCaja.OPEN);when(s.getAbiertoPor()).thenReturn(usuario);
        when(s.getSucursalId()).thenReturn(sucursalId);when(s.getCajaId()).thenReturn(cajaId);when(s.getMonedaId()).thenReturn(monedaId);
        when(s.getFechaOperativa()).thenReturn(LocalDate.of(2026,10,26));return s;}
    private ConceptoMovimientoCaja concepto(TipoMovimientoFinanciero tipo,boolean observacion){ConceptoMovimientoCaja c=mock(ConceptoMovimientoCaja.class);
        when(c.getId()).thenReturn(UUID.randomUUID());when(c.getTipo()).thenReturn(tipo);when(c.getNombre()).thenReturn("Entrega a administración");
        when(c.isRequiereObservacion()).thenReturn(observacion);return c;}
    private MovimientoCajaProjection proyeccion(){MovimientoCajaProjection p=mock(MovimientoCajaProjection.class);when(p.getId()).thenReturn(UUID.randomUUID());
        when(p.getNumero()).thenReturn("MOV-000001");when(p.getFechaHora()).thenReturn(Instant.now());when(p.getCaja()).thenReturn("Caja 1");
        when(p.getSucursal()).thenReturn("Principal");when(p.getUsuario()).thenReturn("admin");when(p.getTipo()).thenReturn("EXPENSE");
        when(p.getOrigen()).thenReturn("MANUAL");when(p.getConcepto()).thenReturn("Entrega");when(p.getReferencia()).thenReturn("AUT-25");
        when(p.getImporte()).thenReturn(new BigDecimal("500"));when(p.getSaldo()).thenReturn(new BigDecimal("1000"));
        when(p.getMonedaCodigo()).thenReturn("DOP");when(p.getEstado()).thenReturn("REGISTERED");when(p.isReversible()).thenReturn(true);return p;}
}
