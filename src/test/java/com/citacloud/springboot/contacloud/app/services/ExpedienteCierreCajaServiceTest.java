package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpedienteCierreCajaServiceTest {
    @Mock CierreCajaRepository cierres; @Mock CashRegisterSessionRepository sesiones;
    @Mock FacturaVentaRepository facturas; @Mock CobroClienteRepository cobros;
    @Mock FinancialMovementRepository movimientos; @Mock RecuentoCajaRepository recuentos;
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
    private final UUID cierreId=UUID.randomUUID(),sesionId=UUID.randomUUID(),cajaId=UUID.randomUUID(),sucursalId=UUID.randomUUID();
    private ExpedienteCierreCajaService service;

    @BeforeEach void preparar(){
        TenantPrincipal principal=new TenantPrincipal(usuario,tenant,empresa,null,"DEMO","Admin","admin","",true,true,
            Set.of(),Set.of("cierres_caja.ver","facturas.ver","recibos.ver","movimientos_caja.ver","recuentos_caja.ver"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        service=new ExpedienteCierreCajaService(cierres,sesiones,facturas,cobros,movimientos,recuentos);
        CierreCaja cierre=mock(CierreCaja.class);lenient().when(cierre.getSesionId()).thenReturn(sesionId);lenient().when(cierre.getCajaId()).thenReturn(cajaId);lenient().when(cierre.getSucursalId()).thenReturn(sucursalId);
        SesionCaja sesion=mock(SesionCaja.class);lenient().when(sesion.getCajaId()).thenReturn(cajaId);lenient().when(sesion.getSucursalId()).thenReturn(sucursalId);
        when(cierres.findByIdAndTenantIdAndEmpresaId(cierreId,tenant,empresa)).thenReturn(Optional.of(cierre));
        when(sesiones.findByIdAndTenantIdAndEmpresaId(sesionId,tenant,empresa)).thenReturn(Optional.of(sesion));
    }
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void ventasUsanSesionEstructuralDelCierreYPaginacionBackend(){
        when(facturas.buscarPorSesion(eq(tenant),eq(empresa),eq(sesionId),eq("cliente"),eq(""),isNull(),any(Pageable.class)))
            .thenReturn(Page.empty(PageRequest.of(0,10)));
        service.ventas(cierreId,"Cliente",null,0,10);
        ArgumentCaptor<Pageable> page=ArgumentCaptor.forClass(Pageable.class);
        verify(facturas).buscarPorSesion(eq(tenant),eq(empresa),eq(sesionId),eq("cliente"),eq(""),isNull(),page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(10);assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test void cobrosUsanSesionDelCierreSinReconstruirPorFechas(){
        when(cobros.buscarPorSesion(tenant,empresa,sesionId,"rec","",0,10)).thenReturn(Page.empty(PageRequest.of(0,10)));
        service.cobros(cierreId,"REC",0,10);
        verify(cobros).buscarPorSesion(tenant,empresa,sesionId,"rec","",0,10);
    }

    @Test void recuentosUsanExclusivamenteSesionDelCierre(){
        when(recuentos.historial(eq(tenant),eq(empresa),eq(sesionId),any(Pageable.class))).thenReturn(Page.empty());
        service.recuentos(cierreId,0,10);
        verify(recuentos).historial(eq(tenant),eq(empresa),eq(sesionId),any(Pageable.class));
    }

    @Test void rechazaRelacionManipuladaEntreCierreYSesion(){
        SesionCaja distinta=mock(SesionCaja.class);when(distinta.getCajaId()).thenReturn(UUID.randomUUID());
        when(sesiones.findByIdAndTenantIdAndEmpresaId(sesionId,tenant,empresa)).thenReturn(Optional.of(distinta));
        assertThatThrownBy(()->service.totalVentas(cierreId)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(facturas);
    }

    @Test void totalVendidoSoloLoCalculaBackendParaLaSesionResuelta(){
        when(facturas.totalConfirmadoPorSesion(tenant,empresa,sesionId)).thenReturn(new BigDecimal("1500.00"));
        assertThat(service.totalVentas(cierreId)).isEqualByComparingTo("1500.00");
    }
}
