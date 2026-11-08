package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.services.DevolucionVentaService;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DevolucionesVentasCompactDialogTest {
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    private Stream<Component> tree(Component c){return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::tree));}
    @SuppressWarnings("unchecked")
    @ParameterizedTest @ValueSource(ints={1,5,6,12})
    void paginaCincoSinSelectorYConservaCantidadesEntrePaginas(int count){
        var principal=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,
                Set.of("devoluciones_venta.crear","devoluciones_venta.confirmar"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        var service=mock(DevolucionVentaService.class);
        when(service.buscar(anyString(),any(),any(),any(),anyInt())).thenReturn(Page.empty());
        var detail=mock(DevolucionVentaDetalleDto.class);
        when(detail.facturaNumero()).thenReturn("FAC-1");when(detail.fechaFactura()).thenReturn(OffsetDateTime.now());
        when(detail.cliente()).thenReturn("Cliente");when(detail.estado()).thenReturn("PAGADA");
        when(detail.moneda()).thenReturn("DOP");when(detail.totalFactura()).thenReturn(BigDecimal.TEN);
        List<DevolucionVentaDetalleDto.Linea> lines=IntStream.range(0,count).mapToObj(i->{
            var l=mock(DevolucionVentaDetalleDto.Linea.class);
            when(l.lineaFacturaId()).thenReturn(UUID.randomUUID());when(l.producto()).thenReturn("Producto "+i);
            when(l.facturado()).thenReturn(BigDecimal.TEN);when(l.disponible()).thenReturn(i==count-1?BigDecimal.ZERO:BigDecimal.TEN);
            when(l.total()).thenReturn(BigDecimal.TEN);return l;
        }).toList();
        when(detail.lineas()).thenReturn(lines);when(detail.motivos()).thenReturn(List.of());when(detail.almacenes()).thenReturn(List.of());
        var dialog=new DevolucionesVentasDialog(service,id->{},()->{});
        ReflectionTestUtils.setField(dialog,"detalle",detail);
        ReflectionTestUtils.invokeMethod(dialog,"mostrarDetalle");
        var grid=(Grid<DevolucionVentaDetalleDto.Linea>)tree(dialog).filter(c->c.getElement().getClassList().contains("cc-sales-return-detail-grid")).findFirst().orElseThrow();
        var pagination=(PosDialogPagination)tree(dialog).filter(c->c.getElement().getClassList().contains("cc-sales-return-pagination")).findFirst().orElseThrow();
        assertFalse(tree(pagination).anyMatch(Select.class::isInstance));
        assertEquals(Math.min(5,count),grid.getListDataView().getItemCount());
        var renderer=(ComponentRenderer<BigDecimalField,DevolucionVentaDetalleDto.Linea>)grid.getColumns().getLast().getRenderer();
        var first=renderer.createComponent(lines.getFirst());
        if(count>1)first.setValue(BigDecimal.ONE);
        var next=(Button)tree(pagination).filter(Button.class::isInstance).filter(c->"Página siguiente".equals(c.getElement().getAttribute("aria-label"))).findFirst().orElseThrow();
        assertEquals(count>5,next.isEnabled());
        while(next.isEnabled())next.click();
        assertEquals((count-1)/5,pagination.currentRequest().page());
        assertFalse(renderer.createComponent(lines.getLast()).isEnabled());
        var previous=(Button)tree(pagination).filter(Button.class::isInstance).filter(c->"Página anterior".equals(c.getElement().getAttribute("aria-label"))).findFirst().orElseThrow();
        while(previous.isEnabled())previous.click();
        assertSame(first,renderer.createComponent(lines.getFirst()));
        assertEquals(count>1?BigDecimal.ONE:BigDecimal.ZERO,first.getValue());
        assertEquals(6,grid.getColumns().size());
    }
}
