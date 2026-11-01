package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.EstadoAgenteImpresion;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.*;
import com.vaadin.flow.component.grid.Grid;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentesImpresionViewTest {
 final PrintAgentAdminService service=mock(PrintAgentAdminService.class);final UUID branch=UUID.randomUUID(),otra=UUID.randomUUID();
 final CajaAgenteImpresionDto caja1=new CajaAgenteImpresionDto(UUID.randomUUID(),"Caja 01"),caja2=new CajaAgenteImpresionDto(UUID.randomUUID(),"Caja 02"),caja3=new CajaAgenteImpresionDto(UUID.randomUUID(),"Caja 03");
 @BeforeEach void setup(){var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Admin","admin","",true,Set.of("agentes_impresion.ver","agentes_impresion.crear","agentes_impresion.editar","facturas.configurar_impresion"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));when(service.sucursales()).thenReturn(List.of(new PrintAgentAdminService.OpcionSucursal(branch,"Principal"),new PrintAgentAdminService.OpcionSucursal(otra,"Norte")));when(service.cajas(branch)).thenReturn(List.of(caja1,caja2));when(service.cajas(otra)).thenReturn(List.of(caja3));}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 @Test void nuevoIncluyeMultiseleccionYCheckboxMarcadoSinGuardarAlCambiarlo(){var view=new AgentesImpresionView(service,mock(EmpresaModuloService.class));var form=view.formulario(null);var checkbox=(Checkbox)descendientes(form).filter(c->c instanceof Checkbox).findFirst().orElseThrow();assertThat(checkbox.getLabel()).isEqualTo("Mostrar PDF al imprimir");assertThat(checkbox.getValue()).isTrue();checkbox.setValue(false);verify(service,never()).crear(any());assertThat(descendientes(form).filter(c->c instanceof MultiSelectComboBox).count()).isEqualTo(1);}
 @Test void editarConservaCajasYPreferenciaDelAgenteYCambiarSucursalLimpiaSeleccion(){var a=new AgenteImpresionDto(UUID.randomUUID(),"PA-1","Agente",branch,"Principal",EstadoAgenteImpresion.ACTIVE,null,false,null,null,null,0,List.of(caja1,caja2),false);var form=new AgentesImpresionView(service,mock(EmpresaModuloService.class)).formulario(a);var checkbox=(Checkbox)descendientes(form).filter(c->c instanceof Checkbox).findFirst().orElseThrow();assertThat(checkbox.getValue()).isFalse();@SuppressWarnings("unchecked") var cajas=(MultiSelectComboBox<CajaAgenteImpresionDto>)descendientes(form).filter(c->c instanceof MultiSelectComboBox).findFirst().orElseThrow();assertThat(cajas.getValue()).containsExactlyInAnyOrder(caja1,caja2);@SuppressWarnings("unchecked") var sucursal=(ComboBox<PrintAgentAdminService.OpcionSucursal>)descendientes(form).filter(c->c instanceof ComboBox).findFirst().orElseThrow();sucursal.setValue(new PrintAgentAdminService.OpcionSucursal(otra,"Norte"));assertThat(cajas.getValue()).isEmpty();assertThat(cajas.getListDataView().getItems().toList()).containsExactly(caja3);verify(service,never()).editar(any(),any());}
 @Test void detalleUsaCheckboxNormalDeSoloLectura(){var checkbox=AgentesImpresionView.crearCheckboxPdf(false,false);assertThat(checkbox.getValue()).isFalse();assertThat(checkbox.isReadOnly()).isTrue();}
 @Test void listadoTieneCajasFlexiblesYAccionesProtegidas() throws Exception {var view=new AgentesImpresionView(service,mock(EmpresaModuloService.class));var f=AgentesImpresionView.class.getDeclaredField("grid");f.setAccessible(true);Grid<?> grid=(Grid<?>)f.get(view);assertThat(grid.getColumns()).extracting(c->c.getHeaderText()).containsExactly("Nombre","Sucursal","Cajas","Identificador","Estado","Última conexión","Acc.");assertThat(grid.getColumns().get(2).getFlexGrow()).isPositive();assertThat(grid.getColumns().getLast().isAutoWidth()).isTrue();assertThat(grid.getColumns().getLast().isFrozenToEnd()).isTrue();}
 private java.util.stream.Stream<Component> descendientes(Component c){return java.util.stream.Stream.concat(java.util.stream.Stream.of(c),c.getChildren().flatMap(this::descendientes));}
}
