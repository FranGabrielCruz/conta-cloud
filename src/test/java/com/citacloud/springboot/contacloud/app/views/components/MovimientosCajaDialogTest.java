package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.services.MovimientoCajaService;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MovimientosCajaDialogTest {
    final UI ui=new UI();final MovimientoCajaService service=mock(MovimientoCajaService.class);final UUID session=UUID.randomUUID();
    @BeforeEach void setup(){UI.setCurrent(ui);}
    @AfterEach void cleanup(){UI.setCurrent(null);}
    ConsultaMovimientosCajaDto data(EstadoSesionCaja estado){return new ConsultaMovimientosCajaDto(
        new ConsultaMovimientosCajaDto.Resumen(session,UUID.randomUUID(),UUID.randomUUID(),"Caja 01","Centro","Ana",
            OffsetDateTime.parse("2026-11-02T08:00:00-04:00"),"DOP",estado,new BigDecimal("5000"),new BigDecimal("1500"),new BigDecimal("750"),new BigDecimal("26250"),
            List.of(new ConsultaMovimientosCajaDto.FormaPago("custom","Pago móvil",new BigDecimal("250"))),new BigDecimal("250")),Page.empty());}
    Stream<Component> all(Component c){return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::all));}
    String text(Component c){return all(c).map(x->x.getElement().getText()).reduce("",(a,b)->a+" "+b);}
    Button action(MovimientosCajaDialog d,String label){return Stream.concat(all(d),d.getFooter().getElement().getChildren().flatMap(e->e.getComponent().stream()).flatMap(this::all)).filter(Button.class::isInstance).map(Button.class::cast).filter(b->label.equals(b.getElement().getAttribute("aria-label"))).findFirst().orElseThrow();}
    @Test void muestraResumenYMediosDinamicosSinDatosDeConteo(){
        when(service.consultar(session,"",null,null,0,10)).thenReturn(data(EstadoSesionCaja.OPEN));
        var d=new MovimientosCajaDialog(session,service,false,false);String content=text(d);
        assertThat(content).contains("SESIÓN ACTUAL","Ana","RESUMEN DE CAJA","FONDO INICIAL","EFECTIVO ESPERADO","26,250.00","Pago móvil","TOTAL OPERACIONES","DETALLE DE MOVIMIENTOS");
        assertThat(content).doesNotContain("EFECTIVO CONTADO","DIFERENCIA","FALTANTE","SOBRANTE","CUADRADO");
        AppGrid<?> grid=(AppGrid<?>)ReflectionTestUtils.getField(ReflectionTestUtils.getField(d,"panel"),"grid");assertThat(grid.isAllRowsVisible()).isTrue();
        assertThat(grid.getColumns()).hasSize(7);assertThat(action(d,"Actualizar movimientos").getText()).isEmpty();verify(service,never()).contexto(any());
    }
    @Test void actualizarObtieneEstadoNuevoYDesactivaCreacionTrasCierre(){
        when(service.consultar(session,"",null,null,0,10)).thenReturn(data(EstadoSesionCaja.OPEN),data(EstadoSesionCaja.CLOSED));
        var d=new MovimientosCajaDialog(session,service,true,false);assertThat(action(d,"Nuevo movimiento").isEnabled()).isTrue();
        action(d,"Actualizar movimientos").click();assertThat(text(d)).contains("CERRADA");assertThat(action(d,"Nuevo movimiento").isEnabled()).isFalse();
        verify(service,times(2)).consultar(session,"",null,null,0,10);verify(service,never()).crear(any());
    }
    @Test void errorNoDejaValoresAnterioresYPermiteReintentar(){
        when(service.consultar(session,"",null,null,0,10)).thenReturn(data(EstadoSesionCaja.OPEN)).thenThrow(new IllegalStateException("failure"));
        var d=new MovimientosCajaDialog(session,service,false,false);action(d,"Actualizar movimientos").click();
        assertThat(text(d)).doesNotContain("26,250.00","Pago móvil").contains("No fue posible actualizar");assertThat(action(d,"Actualizar movimientos").isEnabled()).isTrue();
    }
    @Test void sinSesionMuestraEstadoVacioSinTarjetasFalsas(){
        when(service.consultar(null,"",null,null,0,10)).thenReturn(new ConsultaMovimientosCajaDto(null,Page.empty()));
        var d=new MovimientosCajaDialog(null,service,false,false);assertThat(text(d)).contains("No hay una sesión de caja abierta.").doesNotContain("FONDO INICIAL","0.00");
    }
    @Test void paginacionConservaLaPaginaYLosFiltros(){
        when(service.consultar(eq(session),anyString(),isNull(),isNull(),anyInt(),eq(10))).thenReturn(new ConsultaMovimientosCajaDto(data(EstadoSesionCaja.OPEN).resumen(),new PageImpl<>(List.of(),PageRequest.of(0,10),30)));
        var d=new MovimientosCajaDialog(session,service,false,false);
        AppPagination pagination=(AppPagination)ReflectionTestUtils.getField(ReflectionTestUtils.getField(d,"panel"),"paginacion");ReflectionTestUtils.setField(pagination,"page",1);
        ReflectionTestUtils.invokeMethod(ReflectionTestUtils.getField(d,"panel"),"cargar");verify(service).consultar(session,"",null,null,1,10);
    }
}
