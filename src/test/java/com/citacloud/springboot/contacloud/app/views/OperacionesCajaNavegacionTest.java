package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.router.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the real Caja view/navigation and its real shared content, without a browser. */
class OperacionesCajaNavegacionTest {
    final UI ui=new UI();final CashRegisterSessionService sesiones=mock(CashRegisterSessionService.class);
    final MovimientoCajaService movimientos=mock(MovimientoCajaService.class);final EmpresaModuloService modulos=mock(EmpresaModuloService.class);
    final RecuentoCajaConsultaService recuentos=mock(RecuentoCajaConsultaService.class);final ValeCajaService vales=mock(ValeCajaService.class);
    final UUID caja=UUID.randomUUID();
    @BeforeEach void setup(){UI.setCurrent(ui);auth(Set.of("operaciones_caja.ver","operaciones_caja.ver_historial","movimientos_caja.ver","recuentos_caja.ver","vales_caja.ver"));when(modulos.habilitado("CAJA_BANCOS")).thenReturn(true);
        when(sesiones.sucursalesOperables()).thenReturn(List.of());
        var opcion=new OperacionCajaDto(caja,"Caja 01",UUID.randomUUID(),"Centro","DOP",true,EstadoSesionCaja.OPEN,UUID.randomUUID(),"Turno 1");
        when(sesiones.buscarOperaciones(anyString(),any(),any(),anyInt(),anyInt(),anyString(),anyBoolean())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(opcion)));
        when(sesiones.buscarHistorial(anyString(),any(),any(),any(),any(),anyInt(),anyInt())).thenReturn(Page.empty());
        when(movimientos.consultarCaja(any(),anyString(),any(),any(),anyInt(),anyInt())).thenReturn(datos());
    }
    @AfterEach void cleanup(){UI.setCurrent(null);SecurityContextHolder.clearContext();}
    void auth(Set<String> permisos){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,permisos);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    OperacionesCajaView view(){return new OperacionesCajaView(sesiones,modulos,recuentos,mock(RecuentoCajaPdfService.class),vales,mock(ValeCajaPdfService.class),mock(CierreCajaService.class),mock(CierreCajaPdfService.class),mock(ExpedienteCierreCajaService.class),mock(UltimasFacturasService.class),mock(CobroClienteService.class),movimientos);}
    ConsultaMovimientosCajaDto datos(){return new ConsultaMovimientosCajaDto(new ConsultaMovimientosCajaDto.Resumen(UUID.randomUUID(),UUID.randomUUID(),caja,"Caja 01","Centro","Ana",OffsetDateTime.now(),"DOP",EstadoSesionCaja.OPEN,new BigDecimal("1500"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("1500"),List.of(new ConsultaMovimientosCajaDto.FormaPago("card","Tarjeta",new BigDecimal("8250"))),new BigDecimal("8250")),Page.empty());}
    @Test void usuarioAutorizadoVeExactamenteLasCincoPestanasEnElOrdenEsperado(){
        var v=view();Tabs tabs=(Tabs)ReflectionTestUtils.getField(v,"tabs");
        assertThat(tabs.getChildren().filter(Component::isVisible).map(c->((Tab)c).getLabel())).containsExactly("Estado actual","Movimientos","Historial de cierres","Recuentos","Vales de caja");
    }
    @Test void rutaDirectaMovementsSeleccionaYRenderizaLaConsultaCompleta(){
        var v=view();var event=mock(BeforeEnterEvent.class);when(event.getLocation()).thenReturn(new Location("operaciones-caja?tab=movements"));v.beforeEnter(event);
        Tabs tabs=(Tabs)ReflectionTestUtils.getField(v,"tabs");assertThat(tabs.getSelectedTab().getLabel()).isEqualTo("Movimientos");
        assertThat(((Component)ReflectionTestUtils.getField(v,"panelOperaciones")).isVisible()).isFalse();
        assertThat(((Component)ReflectionTestUtils.getField(v,"panelMovimientos")).isVisible()).isTrue();
        var body=(com.vaadin.flow.component.html.Div)ReflectionTestUtils.getField(v,"contenidoMovimientos");
        assertThat(body.getChildren()).hasSize(1);assertThat(body.getChildren().findFirst().orElseThrow()).isInstanceOf(MovimientosCajaPanel.class);
        verify(movimientos).consultarCaja(caja,"",null,null,0,10);verify(event,never()).rerouteTo(DashboardView.class);
        var panel=body.getChildren().findFirst().orElseThrow();assertThat(panel.getElement().getTextRecursively()).contains("SESIÓN ACTUAL","RESUMEN DE CAJA","FORMAS DE PAGO","Tarjeta","TOTAL OPERACIONES","DETALLE DE MOVIMIENTOS");
    }
    @Test void rutaSinPermisoNoCargaDatosNiEludeElBackend(){
        auth(Set.of("operaciones_caja.ver"));var v=view();var event=mock(BeforeEnterEvent.class);when(event.getLocation()).thenReturn(new Location("operaciones-caja?tab=movements"));v.beforeEnter(event);
        verify(event).rerouteTo(DashboardView.class);verifyNoInteractions(movimientos);
        assertThatThrownBy(()->v.mostrarPestana("movements")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void cambiaDeCajaSinConservarResumenAnterior(){
        var v=view();v.mostrarPestana("movements");var combo=(com.vaadin.flow.component.combobox.ComboBox<OperacionCajaDto>)ReflectionTestUtils.getField(v,"cajaMovimientos");
        UUID otra=UUID.randomUUID();combo.setValue(new OperacionCajaDto(otra,"Caja 02",UUID.randomUUID(),"Norte","DOP",true,EstadoSesionCaja.CLOSED,null,null));
        verify(movimientos).consultarCaja(otra,"",null,null,0,10);
    }
    @Test void laPestanaDeConsultaNoContieneComandosFinancieros(){
        var v=view();v.mostrarPestana("movements");var body=(Component)ReflectionTestUtils.getField(v,"contenidoMovimientos");String html=body.getElement().toString();
        assertThat(html).doesNotContain("Nuevo movimiento","Revertir movimiento","Cerrar caja","Guardar movimiento");
        verify(movimientos,never()).crear(any());verify(movimientos,never()).revertir(any());
    }
    @Test void lasOtrasCuatroPestanasSiguenConectadasASusConsultas(){
        var catalogos=new RecuentoCajaCatalogosDto(List.of(),List.of(),List.of());
        when(recuentos.catalogos()).thenReturn(catalogos);when(vales.catalogos()).thenReturn(catalogos);
        when(recuentos.buscar(anyString(),any(),any(),any(),any(),any(),any(),anyInt(),anyInt())).thenReturn(Page.empty());
        when(vales.buscarAdmin(anyString(),any(),any(),any(),any(),any(),any(),anyInt(),anyInt())).thenReturn(Page.empty());
        var v=view();v.mostrarPestana("current");v.mostrarPestana("history");v.mostrarPestana("counts");v.mostrarPestana("vouchers");
        verify(sesiones).buscarHistorial("",null,null,null,null,0,10);
        verify(recuentos).buscar("",null,null,null,null,null,null,0,10);
        verify(vales).buscarAdmin("",null,null,null,null,null,null,0,10);
        assertThat(((Component)ReflectionTestUtils.getField(v,"panelVales")).isVisible()).isTrue();
        assertThat(((Component)ReflectionTestUtils.getField(v,"panelMovimientos")).isVisible()).isFalse();verifyNoInteractions(movimientos);
    }
}
