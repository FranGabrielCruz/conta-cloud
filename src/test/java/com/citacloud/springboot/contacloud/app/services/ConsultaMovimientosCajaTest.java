package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultaMovimientosCajaTest {
    final CashRegisterSessionRepository sesiones=mock(CashRegisterSessionRepository.class);
    final FinancialMovementRepository movimientos=mock(FinancialMovementRepository.class);
    final ConceptoMovimientoCajaRepository conceptos=mock(ConceptoMovimientoCajaRepository.class);
    final IdempotentOperationService idempotencia=mock(IdempotentOperationService.class);
    final AuditoriaService auditoria=mock(AuditoriaService.class);
    final MovimientoCajaNumeroService numeros=mock(MovimientoCajaNumeroService.class);
    final CashRegisterRepository cajas=mock(CashRegisterRepository.class);
    final MovimientoCajaService service=new MovimientoCajaService(sesiones,movimientos,conceptos,idempotencia,auditoria,numeros,cajas);
    final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID(),sesion=UUID.randomUUID(),sucursal=UUID.randomUUID();
    final SesionCaja s=mock(SesionCaja.class,RETURNS_DEEP_STUBS);
    @BeforeEach void setup(){
        auth(tenant,empresa,usuario,true,Set.of(),Set.of("movimientos_caja.ver"));
        when(sesiones.findByIdAndTenantIdAndEmpresaId(sesion,tenant,empresa)).thenReturn(Optional.of(s));
        when(s.getId()).thenReturn(sesion);when(s.getSucursalId()).thenReturn(sucursal);when(s.getAbiertoPor()).thenReturn(usuario);
        when(s.getFondoInicial()).thenReturn(new BigDecimal("5000"));when(s.getStatus()).thenReturn(EstadoSesionCaja.OPEN);
        when(s.getCaja().getNombre()).thenReturn("Caja 01");when(s.getSucursal().getNombre()).thenReturn("Centro");
        when(s.getMoneda().getCodigoIso()).thenReturn("DOP");when(s.getUsuarioApertura().getNombre()).thenReturn("Cajero");
        when(movimientos.buscarMovimientosCaja(any(),any(),any(),anyString(),anyString(),anyString(),any())).thenReturn(Page.empty());
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    void auth(UUID t,UUID e,UUID u,boolean todas,Set<UUID> branches,Set<String> permisos){
        var p=new TenantPrincipal(u,t,e,null,"DEMO","Cajero","cajero","",true,todas,branches,permisos);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    }
    ResumenMovimientoCajaProjection efectivo(String origen,String in,String out){var f=mock(ResumenMovimientoCajaProjection.class);when(f.getOrigen()).thenReturn(origen);when(f.getEntradas()).thenReturn(new BigDecimal(in));when(f.getSalidas()).thenReturn(new BigDecimal(out));return f;}
    FormaPagoSesionProjection medio(String id,String nombre,String importe){var f=mock(FormaPagoSesionProjection.class);when(f.getCodigo()).thenReturn(id);when(f.getNombre()).thenReturn(nombre);when(f.getImporte()).thenReturn(new BigDecimal(importe));return f;}
    @Test void calculaExpectativaConReglasDeCajaYNoConLaSumaDeLosMedios(){
        var cash=List.of(efectivo("SALES_PAYMENT","19300","0"),efectivo("CUSTOMER_RECEIPT","1200","0"),efectivo("MANUAL","1500","750"));
        var payments=List.of(medio("cash","Efectivo","20500"),medio("custom","Tarjeta corporativa","10250"),medio("bank","Transferencia","4500"));
        when(movimientos.resumenEfectivoSesion(tenant,empresa,sesion)).thenReturn(cash);
        when(movimientos.formasPagoSesion(tenant,empresa,sesion)).thenReturn(payments);
        var r=service.consultar(sesion,"solo una factura",null,null,0,10).resumen();
        assertThat(r.entradas()).isEqualByComparingTo("1500");assertThat(r.salidas()).isEqualByComparingTo("750");
        assertThat(r.efectivoEsperado()).isEqualByComparingTo("26250");assertThat(r.totalOperaciones()).isEqualByComparingTo("35250");
        assertThat(r.formasPago()).extracting(x->x.nombre()).containsExactly("Efectivo","Tarjeta corporativa","Transferencia");
        verify(movimientos).buscarMovimientosCaja(tenant,empresa,sesion,"solo una factura","ALL","ALL",PageRequest.of(0,10));
        verifyNoInteractions(conceptos,idempotencia,auditoria,numeros);verify(movimientos,never()).save(any());verify(sesiones,never()).bloquear(any(),any(),any());
    }
    @Test void fondoSinOperacionesYFaltaDeSesionNoInventanDatos(){
        assertThat(service.consultar(sesion,"",null,null,0,10).resumen().efectivoEsperado()).isEqualByComparingTo("5000");
        clearInvocations(sesiones,movimientos);
        var vacio=service.consultar(null,"",null,null,0,10);assertThat(vacio.resumen()).isNull();assertThat(vacio.movimientos()).isEmpty();verifyNoInteractions(sesiones,movimientos);
    }
    @Test void refrescoDetectaCierreSinModificarLaSesion(){
        when(s.getStatus()).thenReturn(EstadoSesionCaja.OPEN,EstadoSesionCaja.CLOSED);
        assertThat(service.consultar(sesion,"",null,null,0,10).resumen().estado()).isEqualTo(EstadoSesionCaja.OPEN);
        assertThat(service.consultar(sesion,"",null,null,0,10).resumen().estado()).isEqualTo(EstadoSesionCaja.CLOSED);
        verify(s,never()).cerrar(any(),any(),any(),any());
    }
    @Test void usuarioDeOtraEmpresaNoPuedeConsultarElIdManipulado(){
        auth(UUID.randomUUID(),UUID.randomUUID(),usuario,true,Set.of(),Set.of("movimientos_caja.ver"));
        assertThatThrownBy(()->service.consultar(sesion,"",null,null,0,10)).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(movimientos);
    }
    @Test void sucursalNoAutorizadaYOtroCajeroNoPuedenLeerTotales(){
        auth(tenant,empresa,usuario,false,Set.of(UUID.randomUUID()),Set.of("movimientos_caja.ver"));
        assertThatThrownBy(()->service.consultar(sesion,"",null,null,0,10)).isInstanceOf(RecursoNoEncontradoException.class);
        auth(tenant,empresa,UUID.randomUUID(),true,Set.of(),Set.of("movimientos_caja.ver"));
        assertThatThrownBy(()->service.consultar(sesion,"",null,null,0,10)).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(movimientos);
    }
    @Test void permisoYModuloSeValidanEnElProxyBackend(){
        try(var context=new AnnotationConfigApplicationContext()){
            var modulos=mock(EmpresaModuloService.class);when(modulos.habilitado("CAJA_BANCOS")).thenReturn(true);
            context.register(SecurityConfig.class);context.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            context.registerBean(MovimientoCajaService.class,()->service);context.refresh();var secured=context.getBean(MovimientoCajaService.class);
            auth(tenant,empresa,usuario,true,Set.of(),Set.of());
            assertThatThrownBy(()->secured.consultar(sesion,"",null,null,0,10)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            auth(tenant,empresa,usuario,true,Set.of(),Set.of("movimientos_caja.ver"));when(modulos.habilitado("CAJA_BANCOS")).thenReturn(false);
            assertThatThrownBy(()->secured.consultar(sesion,"",null,null,0,10)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(movimientos);
            when(modulos.habilitado("CAJA_BANCOS")).thenReturn(true);
            assertThatThrownBy(()->secured.consultarCaja(null,"",null,null,0,10)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            auth(tenant,empresa,usuario,true,Set.of(),Set.of("operaciones_caja.ver"));
            assertThatThrownBy(()->secured.consultarCaja(null,"",null,null,0,10)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            auth(tenant,empresa,usuario,true,Set.of(),Set.of("operaciones_caja.ver","movimientos_caja.ver"));
            assertThat(secured.consultarCaja(null,"",null,null,0,10).resumen()).isNull();
        }
    }
    @Test void transaccionEsSoloLecturaConSnapshotRepetible()throws Exception{
        var tx=MovimientoCajaService.class.getMethod("consultar",UUID.class,String.class,TipoMovimientoFinanciero.class,TipoOrigenMovimiento.class,int.class,int.class).getAnnotation(Transactional.class);
        assertThat(tx.readOnly()).isTrue();assertThat(tx.isolation()).isEqualTo(Isolation.REPEATABLE_READ);assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
    @Test void administracionResuelveLaSesionActualDeLaCajaAunqueElCajeroSeaOtro(){
        UUID cajaId=UUID.randomUUID();Caja caja=mock(Caja.class);when(caja.getId()).thenReturn(cajaId);when(caja.getSucursalId()).thenReturn(sucursal);
        when(cajas.findByIdAndTenantIdAndEmpresaId(cajaId,tenant,empresa)).thenReturn(Optional.of(caja));
        when(sesiones.findByTenantIdAndEmpresaIdAndCajaIdAndStatus(tenant,empresa,cajaId,EstadoSesionCaja.OPEN)).thenReturn(Optional.of(s));
        when(s.getAbiertoPor()).thenReturn(UUID.randomUUID());
        assertThat(service.consultarCaja(cajaId,"",null,null,0,10).resumen().sesionId()).isEqualTo(sesion);
        when(sesiones.findByTenantIdAndEmpresaIdAndCajaIdAndStatus(tenant,empresa,cajaId,EstadoSesionCaja.OPEN)).thenReturn(Optional.empty());
        clearInvocations(movimientos);
        assertThat(service.consultarCaja(cajaId,"",null,null,0,10).resumen()).isNull();verifyNoInteractions(movimientos);
    }
    @Test void administracionNoAdmiteCajaDeOtraEmpresaOSucursal(){
        UUID cajaId=UUID.randomUUID();
        assertThatThrownBy(()->service.consultarCaja(cajaId,"",null,null,0,10)).isInstanceOf(RecursoNoEncontradoException.class);
        Caja caja=mock(Caja.class);when(caja.getSucursalId()).thenReturn(UUID.randomUUID());
        when(cajas.findByIdAndTenantIdAndEmpresaId(cajaId,tenant,empresa)).thenReturn(Optional.of(caja));
        auth(tenant,empresa,usuario,false,Set.of(sucursal),Set.of("movimientos_caja.ver","operaciones_caja.ver"));
        assertThatThrownBy(()->service.consultarCaja(cajaId,"",null,null,0,10)).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(movimientos);
    }
    @Configuration @EnableMethodSecurity static class SecurityConfig {}
}
