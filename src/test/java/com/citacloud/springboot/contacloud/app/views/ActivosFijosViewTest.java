package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.services.ActivoFijoService;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.citacloud.springboot.contacloud.app.views.components.AppDetailSection;
import com.citacloud.springboot.contacloud.app.views.components.AppGrid;
import com.citacloud.springboot.contacloud.app.views.components.AppStatusBadge;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.citacloud.springboot.contacloud.app.services.ActivoFijoService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ActivosFijosViewTest {
    private final ActivoFijoService servicio=mock(ActivoFijoService.class);
    private final Moneda base=new Moneda(UUID.randomUUID(),"USD",true);
    private final Catalogos opciones=new Catalogos(List.of(new Categoria("TERRENOS","Terrenos")),
        List.of(new Opcion(UUID.randomUUID(),"Principal")),List.of(),List.of(new Moneda(UUID.randomUUID(),"DOP",false),base),
        List.of(),List.of());

    @AfterEach
    void cerrarSesion() { SecurityContextHolder.clearContext(); }

    @Test
    void nuevoNoExponeCodigoYProponeMonedaBase() {
        when(servicio.formulario()).thenReturn(opciones);
        var form=new ActivoFijoForm(servicio,null,a->{},()->{});
        assertThat(arbol(form).filter(TextField.class::isInstance).map(TextField.class::cast).map(TextField::getLabel))
            .noneMatch(l->l.equalsIgnoreCase("Código"));
        var moneda=arbol(form).filter(ComboBox.class::isInstance).map(ComboBox.class::cast)
            .filter(c->"Moneda".equals(c.getLabel())).findFirst().orElseThrow();
        assertThat(moneda.getValue()).isEqualTo(base);
        var depreciable=arbol(form).filter(Checkbox.class::isInstance).map(Checkbox.class::cast).findFirst().orElseThrow();
        var metodo=arbol(form).filter(ComboBox.class::isInstance).map(ComboBox.class::cast)
            .filter(c->"Método".equals(c.getLabel())).findFirst().orElseThrow();
        assertThat(metodo.isVisible()).isFalse();
        depreciable.setValue(true);
        assertThat(metodo.isVisible()).isTrue();
        assertThat(metodo.isRequiredIndicatorVisible()).isTrue();
    }

    @Test
    void numeroDeSerieEsTextoSinAutocompletadoEnNuevoYEditar() {
        when(servicio.formulario()).thenReturn(opciones);
        var valor="SN-DL-2026-00125";
        var fecha=LocalDate.of(2026,11,9);
        var datos=new Datos("Equipo","TERRENOS",null,null,valor,
            new Adquisicion(fecha,BigDecimal.TEN,base.id(),null,null,null),
            new Asignacion(opciones.sucursales().getFirst().id(),null,null),
            new Depreciacion(false,null,null,null,null),null);
        var resumen=new Resumen(UUID.randomUUID(),"AF-000001","Equipo","Terrenos","Principal",null,
            Estado.ACTIVO,"Disponible",fecha,base.codigo(),BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN);
        var registro=new Registro(resumen,0,datos,new Destino("Principal",null,null),null,null,false);
        var nuevo=new ActivoFijoForm(servicio,null,a->{},()->{});
        var editar=new ActivoFijoForm(servicio,registro,a->{},()->{});

        assertThat(serie(editar).getValue()).isEqualTo(valor);
        for(var form:List.of(nuevo,editar)) {
            var serie=serie(form);
            assertThat(serie.getElement().getTag()).isEqualTo("vaadin-text-field");
            assertThat(serie.getId()).contains("fixed-asset-serial-number");
            assertThat(serie.getElement().getAttribute("autocomplete")).isEqualTo("off");
            assertThat(serie.getElement().getAttribute("name")).isEqualTo("assetSerialNumber");
            var input=serie.getElement().getChild(0);
            assertThat(input.getTag()).isEqualTo("input");
            assertThat(input.getAttribute("slot")).isEqualTo("input");
            assertThat(input.getAttribute("type")).isEqualTo("text");
            assertThat(input.getAttribute("inputmode")).isEqualTo("text");
            assertThat(input.getAttribute("name")).isEqualTo("assetSerialNumber");
            assertThat(input.getAttribute("autocomplete")).isEqualTo("off");
            assertThat(input.hasAttribute("role")).isFalse();
            var propietario=form.getChildren().filter(c->c.getId().stream().anyMatch(input.getAttribute("form")::equals))
                .findFirst().orElseThrow();
            assertThat(propietario.getElement().getTag()).isEqualTo("form");
            assertThat(propietario.getElement().getAttribute("autocomplete")).isEqualTo("off");
            assertThat(propietario.getChildren()).isEmpty();
            assertThat(arbol(form).filter(c->"form".equals(c.getElement().getTag())))
                .hasSize(2).allMatch(c->"off".equals(c.getElement().getAttribute("autocomplete")));
            serie.setValue(valor);
            assertThat(serie.getValue()).isEqualTo(valor);
        }
    }

    private static TextField serie(ActivoFijoForm formulario) {
        return arbol(formulario).filter(TextField.class::isInstance).map(TextField.class::cast)
            .filter(c->"Número de serie".equals(c.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void detalleOrdenaLosDatosEnCuatroSeccionesDeTresColumnas() {
        var fecha=LocalDate.of(2026,11,9);
        var datos=new Datos("Equipo","TERRENOS",null,null,null,
            new Adquisicion(fecha,BigDecimal.TEN,base.id(),null,null,"REF-17"),
            new Asignacion(opciones.sucursales().getFirst().id(),"Administración",null),
            new Depreciacion(false,null,null,null,null),"Observación extensa");
        var resumen=new Resumen(UUID.randomUUID(),"AF-000001","Equipo","Terrenos","Principal","Administración",
            Estado.ACTIVO,"Disponible",fecha,base.codigo(),BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN);
        var registro=new Registro(resumen,0,datos,new Destino("Principal","Administración","Juan"),"Proveedor","FAC-17",false);
        var detalle=ActivosFijosView.detalle(new Detalle(registro,List.of(),List.of()));
        var secciones=detalle.getChildren().filter(AppDetailSection.class::isInstance)
            .map(AppDetailSection.class::cast).toList();
        assertThat(secciones.stream().filter(s->s.hasClassName("cc-detail-three")).map(ActivosFijosViewTest::etiquetas))
            .containsExactly(
                List.of("Código","Activo","Estado","Categoría","Tipo","Marca","Modelo","Número de serie","Condición operativa"),
                List.of("Fecha de adquisición","Costo de adquisición","Moneda","Proveedor","Documento de compra"),
                List.of("Sucursal","Ubicación","Responsable"),
                List.of("Depreciable","Método","Vida útil","Inicio de depreciación","Valor residual","Depreciación acumulada","Valor en libros"));
        assertThat(arbol(secciones.getFirst()).filter(AppStatusBadge.class::isInstance).map(AppStatusBadge.class::cast))
            .extracting(AppStatusBadge::getText).containsExactly("Activo","Disponible");
        assertThat(arbol(secciones.get(1)).map(c->c.getElement().getText())).contains("FAC-17 · REF-17","09/11/2026","USD 10.00");
        assertThat(secciones.getLast().hasClassName("cc-detail-full")).isTrue();
        assertThat(arbol(detalle).filter(AppGrid.class::isInstance)).hasSize(2);
    }

    private static List<String> etiquetas(AppDetailSection seccion) {
        return arbol(seccion).filter(c->c.hasClassName("cc-detail-label")).map(c->c.getElement().getText()).toList();
    }

    @Test
    void usuarioDeConsultaSoloTieneVerYElListadoIniciaEnDiez() {
        autenticar();
        var auth=mock(ModuleAuthorization.class);
        when(auth.canAccess(MODULO,VER)).thenReturn(true);
        when(servicio.filtros()).thenReturn(opciones);
        when(servicio.consultar(any(Filtro.class),eq(0),eq(10))).thenReturn(new Listado(Page.empty(),List.of()));
        var vista=new ActivosFijosView(servicio,null,auth);
        vista.beforeEnter(mock(BeforeEnterEvent.class));
        assertThat(arbol(vista).filter(AppActionButton.class::isInstance).map(AppActionButton.class::cast))
            .noneMatch(b->b.isVisible() && b.getAriaLabel().stream().anyMatch(s->s.equals("Nuevo activo")||s.equals("Reporte de activos")));
        var grid=arbol(vista).filter(AppGrid.class::isInstance).map(c->(AppGrid<?>)c).findFirst().orElseThrow();
        assertThat(grid.isAllRowsVisible()).isTrue();
        assertThat(grid.getColumns()).hasSize(7);
        @SuppressWarnings("unchecked")
        var renderer=(ComponentRenderer<AppActionButton,Resumen>)grid.getColumns().getLast().getRenderer();
        var fila=new Resumen(UUID.randomUUID(),"AF-000001","Activo","Terrenos","Principal",null,Estado.ACTIVO,
            "Disponible",LocalDate.now(),"USD",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN);
        var ver=renderer.createComponent(fila);
        assertThat(ver.getAriaLabel()).contains("Ver activo");
        assertThat(ver.getText()).isEmpty();
        verify(servicio).consultar(any(Filtro.class),eq(0),eq(10));
    }

    private static Stream<Component> arbol(Component c) {
        return Stream.concat(Stream.of(c),c.getChildren().flatMap(ActivosFijosViewTest::arbol));
    }

    private static void autenticar() {
        var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","test","",true,Set.of(VER));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
