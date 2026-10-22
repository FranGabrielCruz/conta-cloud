package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.*;import java.util.*;

@Route(value="configuracion/facturacion/descuentos",layout=MainLayout.class)
@PageTitle("Configuración de descuentos | ContaCloud") @PermitAll
public class ConfiguracionDescuentosView extends VerticalLayout implements BeforeEnterObserver {
    private final ConfiguracionDescuentoService service;private ConfiguracionDescuentoDto datos;
    private boolean integrada;
    private final Checkbox habilitado=new Checkbox("Permitir descuentos en facturación");
    private final Checkbox venta=new Checkbox("Permitir descuento general de la venta");
    private final Checkbox linea=new Checkbox("Permitir descuento por producto");
    private final Checkbox porcentaje=new Checkbox("Permitir descuento por porcentaje");
    private final Checkbox monto=new Checkbox("Permitir descuento por monto");
    private final Checkbox motivo=new Checkbox("Requerir motivo del descuento");
    private final Checkbox autorizacion=new Checkbox("Permitir autorización superior");
    private final AppGrid<ConfiguracionDescuentoDto.Limite> gridLimites=new AppGrid<>(ConfiguracionDescuentoDto.Limite.class);
    private final AppGrid<ConfiguracionDescuentoDto.Motivo> gridMotivos=new AppGrid<>(ConfiguracionDescuentoDto.Motivo.class);
    public ConfiguracionDescuentosView(ConfiguracionDescuentoService service){this.service=service;addClassNames("cc-page","cc-discount-config-page");setSpacing(true);setPadding(true);setWidthFull();habilitado.addValueChangeListener(e->actualizarDisponibilidadPolitica());}
    @Override public void beforeEnter(BeforeEnterEvent event){event.forwardTo(ConfiguracionDescuentosRecargosView.class);}
    public void prepararIntegrada(){integrada=true;removeClassName("cc-page");setPadding(false);construir();}
    private void construir(){removeAll();try{datos=service.obtener();if(integrada)add(politica(),limites(),motivos());else add(new AppPageHeader("Configuración de descuentos","Define las políticas comerciales, límites por rol y motivos utilizados en Facturación."),politica(),limites(),motivos());}catch(RuntimeException ex){error(ex,"No fue posible cargar la configuración de descuentos.");}}
    private Component politica(){cargarPolitica();boolean editable=can("descuentos_configuracion.editar");for(Checkbox campo:camposPolitica())campo.setReadOnly(!editable);Div campos=new Div(habilitado,venta,linea,porcentaje,monto,motivo,autorizacion);campos.addClassName("cc-discount-policy-grid");AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{datos=service.guardarPolitica(new ConfiguracionDescuentoInput(habilitado.getValue(),venta.getValue(),linea.getValue(),porcentaje.getValue(),monto.getValue(),motivo.getValue(),autorizacion.getValue(),datos.version()));cargarPolitica();Notification.show("Configuración guardada correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible guardar la configuración.");}});guardar.setVisible(editable);Div acciones=new Div(guardar);acciones.addClassName("cc-form-actions");return seccion("POLÍTICA GENERAL",campos,acciones);}
    private void cargarPolitica(){habilitado.setValue(datos.habilitado());venta.setValue(datos.permiteVenta());linea.setValue(datos.permiteLinea());porcentaje.setValue(datos.permitePorcentaje());monto.setValue(datos.permiteMonto());motivo.setValue(datos.requiereMotivo());autorizacion.setValue(datos.permiteAutorizacion());actualizarDisponibilidadPolitica();}
    private void actualizarDisponibilidadPolitica(){boolean disponible=habilitado.getValue()&&can("descuentos_configuracion.editar");for(Checkbox campo:List.of(venta,linea,porcentaje,monto,motivo,autorizacion))campo.setEnabled(disponible);}
    private List<Checkbox> camposPolitica(){return List.of(habilitado,venta,linea,porcentaje,monto,motivo,autorizacion);}
    private Component limites(){gridLimites.removeAllColumns();gridLimites.addColumn(ConfiguracionDescuentoDto.Limite::rol).setHeader("Rol").setFlexGrow(1);gridLimites.addColumn(x->decimal(x.porcentajeMaximo(),"Sin límite", "%")).setHeader("Máx. %").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);gridLimites.addColumn(x->x.montoMaximo()==null?"Sin límite":datos.monedaBase()+" "+money(x.montoMaximo())).setHeader("Máx. monto").setAutoWidth(true).setTextAlign(ColumnTextAlign.END);gridLimites.addColumn(x->x.activo()?"Activo":"Inactivo").setHeader("Estado").setAutoWidth(true);if(can("descuentos_configuracion.editar"))gridLimites.addComponentColumn(x->new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar límite",e->formLimite(x))).setHeader("Acciones").setAutoWidth(true);gridLimites.setItems(datos.limites());gridLimites.setEmptyStateText("No hay límites configurados.");AppActionButton agregar=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Agregar límite",e->formLimite(null));agregar.setVisible(can("descuentos_configuracion.editar"));return seccionConAccion("LÍMITES POR ROL",agregar,gridLimites);}
    private Component motivos(){gridMotivos.removeAllColumns();gridMotivos.addColumn(ConfiguracionDescuentoDto.Motivo::nombre).setHeader("Motivo").setFlexGrow(1);gridMotivos.addColumn(x->x.otro()?"Requiere detalle":"Estándar").setHeader("Tipo").setAutoWidth(true);gridMotivos.addColumn(x->x.activo()?"Activo":"Inactivo").setHeader("Estado").setAutoWidth(true);if(can("descuentos_configuracion.editar"))gridMotivos.addComponentColumn(x->new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar motivo",e->formMotivo(x))).setHeader("Acciones").setAutoWidth(true);gridMotivos.setItems(datos.motivos());gridMotivos.setEmptyStateText("No hay motivos configurados.");AppActionButton agregar=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Agregar motivo",e->formMotivo(null));agregar.setVisible(can("descuentos_configuracion.editar"));return seccionConAccion("MOTIVOS DE DESCUENTO",agregar,gridMotivos);}
    private void formLimite(ConfiguracionDescuentoDto.Limite actual){Dialog d=dialog(actual==null?"AGREGAR LÍMITE":"EDITAR LÍMITE");ComboBox<ConfiguracionDescuentoDto.RolOpcion> rol=new ComboBox<>("Rol");rol.setItems(datos.roles());rol.setItemLabelGenerator(ConfiguracionDescuentoDto.RolOpcion::nombre);rol.setWidthFull();TextField pct=numero("Máximo porcentaje sin autorización");TextField importe=numero("Máximo monto sin autorización ("+datos.monedaBase()+")");Checkbox activo=new Checkbox("Activo",true);if(actual!=null){rol.setValue(datos.roles().stream().filter(x->x.id().equals(actual.rolId())).findFirst().orElse(null));rol.setReadOnly(true);valor(pct,actual.porcentajeMaximo());valor(importe,actual.montoMaximo());activo.setValue(actual.activo());}d.add(rol,pct,importe,activo);AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{service.guardarLimite(actual==null?null:actual.id(),new LimiteDescuentoInput(rol.getValue()==null?null:rol.getValue().id(),numero(pct),numero(importe),activo.getValue(),actual==null?null:actual.version()));d.close();construir();Notification.show("Límite guardado correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible guardar el límite.");}});d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private void formMotivo(ConfiguracionDescuentoDto.Motivo actual){Dialog d=dialog(actual==null?"AGREGAR MOTIVO":"EDITAR MOTIVO");TextField nombre=new TextField("Nombre");nombre.setMaxLength(120);nombre.setWidthFull();Checkbox otro=new Checkbox("Solicitar detalle cuando se utilice");Checkbox activo=new Checkbox("Activo",true);if(actual!=null){nombre.setValue(actual.nombre());otro.setValue(actual.otro());activo.setValue(actual.activo());}d.add(nombre,otro,activo);AppActionButton guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{service.guardarMotivo(actual==null?null:actual.id(),new MotivoDescuentoInput(nombre.getValue(),otro.getValue(),activo.getValue(),actual==null?null:actual.version()));d.close();construir();Notification.show("Motivo guardado correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible guardar el motivo.");}});d.getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();}
    private static Div seccion(String titulo,Component... contenido){Div d=new Div();d.addClassName("cc-form-section");H3 h=new H3(titulo);h.addClassName("cc-section-heading");d.add(h);d.add(contenido);return d;}
    private static Div seccionConAccion(String titulo,Component accion,Component contenido){Div cabecera=new Div(new H3(titulo),accion);cabecera.addClassName("cc-section-header-actions");Div d=new Div(cabecera,contenido);d.addClassName("cc-form-section");return d;}
    private static Dialog dialog(String titulo){Dialog d=new Dialog();d.setHeaderTitle(titulo);d.setWidth("min(560px,94vw)");d.setCloseOnOutsideClick(false);return d;}
    private static TextField numero(String label){TextField f=new TextField(label);f.setPlaceholder("0.00");f.setWidthFull();return f;}
    private static BigDecimal numero(TextField f){String x=f.getValue()==null?"":f.getValue().replace(",","").trim();if(x.isEmpty())return null;try{return new BigDecimal(x);}catch(NumberFormatException ex){throw new ReglaNegocioException("Ingresa un valor numérico válido.");}}
    private static void valor(TextField f,BigDecimal v){f.setValue(v==null?"":v.stripTrailingZeros().toPlainString());}
    private static String decimal(BigDecimal v,String vacio,String sufijo){return v==null?vacio:v.stripTrailingZeros().toPlainString()+sufijo;}
    private static String money(BigDecimal v){return String.format(Locale.US,"%,.2f",v);}
    private static boolean can(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static void error(RuntimeException ex,String fallback){com.citacloud.springboot.contacloud.app.errors.UiErrorHandler.show(ex,fallback);}
}
