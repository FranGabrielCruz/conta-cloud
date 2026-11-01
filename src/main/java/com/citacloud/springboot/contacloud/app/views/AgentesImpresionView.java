package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.EstadoAgenteImpresion;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Route(value="configuracion/impresion-pos/agentes",layout=MainLayout.class)
@PageTitle("Agentes de impresión | ContaCloud") @PermitAll
public class AgentesImpresionView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final PrintAgentAdminService service;
    private final EmpresaModuloService modules;
    private final TextField search=new TextField("Buscar");
    private final ComboBox<EstadoAgenteImpresion> status=new ComboBox<>("Estado");
    private final AppGrid<AgenteImpresionDto> grid=new AppGrid<>(AgenteImpresionDto.class);
    private final AppPagination pagination;

    public AgentesImpresionView(PrintAgentAdminService service,EmpresaModuloService modules){
        this.service=service;this.modules=modules;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        Component create=can("agentes_impresion.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo agente de impresión",e->abrirFormulario(null)):null;
        add(create==null?new AppPageHeader("AGENTES DE IMPRESIÓN","Administra los dispositivos autorizados para contaCloudPrint."):
            new AppPageHeader("AGENTES DE IMPRESIÓN","Administra los dispositivos autorizados para contaCloudPrint.",create));
        search.setPlaceholder("Nombre o identificador...");search.setClearButtonVisible(true);search.setValueChangeMode(ValueChangeMode.LAZY);search.setValueChangeTimeout(400);
        status.setItems(EstadoAgenteImpresion.values());status.setItemLabelGenerator(AgentesImpresionView::estado);status.setClearButtonVisible(true);
        search.addValueChangeListener(e->{if(e.isFromClient())reset();});status.addValueChangeListener(e->{if(e.isFromClient())reset();});
        HorizontalLayout filters=new HorizontalLayout(search,status);filters.addClassName("cc-filter-bar");filters.setAlignItems(Alignment.END);filters.setWidthFull();
        configureGrid();pagination=new AppPagination(r->load());add(filters,grid,pagination);
    }
    @Override public void beforeEnter(BeforeEnterEvent event){
        if(!modules.habilitado("CONFIGURACION")||!can("agentes_impresion.ver")){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}load();
    }
    private void configureGrid(){
        grid.addColumn(AgenteImpresionDto::nombre).setHeader("Nombre").setWidth("0").setFlexGrow(2);
        grid.addColumn(AgenteImpresionDto::sucursal).setHeader("Sucursal").setWidth("0").setFlexGrow(1);
        grid.addComponentColumn(this::resumenCajas).setHeader("Cajas").setWidth("0").setFlexGrow(2);
        grid.addColumn(AgenteImpresionDto::codigo).setHeader("Identificador").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::badge).setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(a->fecha(a)).setHeader("Última conexión").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("Acc.").setAutoWidth(true).setFlexGrow(0).setFrozenToEnd(true);
        grid.setEmptyStateText("No hay agentes de impresión registrados.");
    }
    private Component resumenCajas(AgenteImpresionDto a){
        String primeras=a.cajas().stream().limit(2).map(CajaAgenteImpresionDto::nombre).collect(Collectors.joining(", "));
        if(primeras.isEmpty())primeras="Sin asignar";
        if(a.cajas().size()>2)primeras+=" +"+(a.cajas().size()-2);
        Span resumen=new Span(primeras);resumen.addClassName("cc-agent-register-summary");Tooltip.forComponent(resumen).setText(nombresCajas(a));return resumen;
    }
    private Component actions(AgenteImpresionDto a){
        HorizontalLayout row=new HorizontalLayout();row.addClassName("cc-grid-actions");row.setPadding(false);row.setSpacing(false);
        row.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver agente de impresión",e->detail(a.id())));
        if(can("agentes_impresion.editar"))row.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar agente de impresión",e->editar(a.id())));
        return row;
    }
    private void load(){if(pagination==null)return;try{var req=pagination.currentRequest();var page=service.buscar(search.getValue(),status.getValue(),req.page(),req.size());grid.setItems(page.getContent());grid.recalculateColumnWidths();pagination.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar los agentes de impresión.");}}
    private void reset(){pagination.reset();load();}
    private void editar(UUID id){try{abrirFormulario(service.obtener(id));}catch(RuntimeException ex){error(ex,"No fue posible consultar el agente.");}}
    private void abrirFormulario(AgenteImpresionDto actual){try{formulario(actual).open();}catch(RuntimeException ex){error(ex,"No fue posible abrir el formulario.");}}

    Dialog formulario(AgenteImpresionDto actual){
        Dialog d=dialog(actual==null?"NUEVO AGENTE DE IMPRESIÓN":"EDITAR AGENTE DE IMPRESIÓN","720px");
        TextField name=new TextField("Nombre");name.setMaxLength(150);name.setRequiredIndicatorVisible(true);
        ComboBox<PrintAgentAdminService.OpcionSucursal> branch=new ComboBox<>("Sucursal");
        List<PrintAgentAdminService.OpcionSucursal> sucursales=new ArrayList<>(service.sucursales());
        if(actual!=null&&sucursales.stream().noneMatch(s->s.id().equals(actual.sucursalId())))sucursales.add(new PrintAgentAdminService.OpcionSucursal(actual.sucursalId(),actual.sucursal()));
        branch.setItems(sucursales);branch.setItemLabelGenerator(PrintAgentAdminService.OpcionSucursal::nombre);branch.setRequiredIndicatorVisible(true);
        MultiSelectComboBox<CajaAgenteImpresionDto> cajas=new MultiSelectComboBox<>("Cajas");
        cajas.setItemLabelGenerator(CajaAgenteImpresionDto::nombre);cajas.setRequiredIndicatorVisible(true);cajas.setClearButtonVisible(true);
        cajas.setPlaceholder("Buscar y seleccionar cajas...");cajas.setErrorMessage("Seleccione al menos una caja.");cajas.setWidthFull();cajas.getStyle().set("grid-column","1 / -1");
        branch.addValueChangeListener(event->{cajas.clear();cajas.setItems(event.getValue()==null?List.of():service.cajas(event.getValue().id()));});
        Checkbox mostrar=crearCheckboxPdf(actual==null||actual.mostrarPdfAlImprimir(),can("facturas.configurar_impresion"));
        mostrar.getStyle().set("grid-column","1 / -1");
        if(actual!=null){name.setValue(actual.nombre());branch.setValue(sucursales.stream().filter(s->s.id().equals(actual.sucursalId())).findFirst().orElseThrow());
            // Keep historical inactive selections visible so the administrator can explicitly remove them.
            Map<UUID,CajaAgenteImpresionDto> opciones=new LinkedHashMap<>();service.cajas(actual.sucursalId()).forEach(c->opciones.put(c.id(),c));actual.cajas().forEach(c->opciones.putIfAbsent(c.id(),c));
            cajas.setItems(opciones.values());cajas.setValue(new LinkedHashSet<>(actual.cajas()));}
        Div fields=new Div(name,branch,cajas,mostrar);fields.addClassName("cc-dialog-form");
        d.add(fields,new Paragraph("Todas las cajas seleccionadas compartirán la impresora local configurada en este agente."));
        AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);
        save.addClickListener(event->{
            name.setInvalid(name.isEmpty()||name.getValue().isBlank());branch.setInvalid(branch.isEmpty());cajas.setInvalid(cajas.isEmpty());
            if(name.isInvalid()||branch.isInvalid()||cajas.isInvalid())return;
            save.setEnabled(false);
            try{var ids=cajas.getValue().stream().map(CajaAgenteImpresionDto::id).sorted().toList();
                if(actual==null)service.crear(new CrearAgenteImpresionInput(name.getValue(),branch.getValue().id(),ids,mostrar.getValue()));
                else service.editar(actual.id(),new EditarAgenteImpresionInput(name.getValue(),branch.getValue().id(),ids,mostrar.getValue(),actual.version()));
                d.close();load();Notification.show("Agente guardado correctamente.");
            }catch(RuntimeException ex){save.setEnabled(true);error(ex,"No fue posible guardar el agente de impresión.");}
        });
        d.getFooter().add(save,new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));return d;
    }
    static Checkbox crearCheckboxPdf(boolean valor,boolean editable){Checkbox mostrar=new Checkbox("Mostrar PDF al imprimir");mostrar.setValue(valor);mostrar.setReadOnly(!editable);return mostrar;}

    private void detail(UUID id){try{
        var a=service.obtener(id);Dialog d=dialog("AGENTE DE IMPRESIÓN","820px");Div heading=new Div(new H2(a.nombre()),badge(a));heading.addClassName("cc-detail-heading");
        d.add(heading,new AppDetailSection("INFORMACIÓN GENERAL").field("Identificador",a.codigo()).field("Empresa",TenantContext.principalActual().empresaCodigo())
            .field("Sucursal",a.sucursal()).field("Cajas",nombresCajas(a)).field("Estado",estado(a.estado())).field("Conectividad",a.conectado()?"Conectado":"Desconectado")
            .field("Última conexión",fecha(a)).field("Equipo",display(a.nombreMaquina())).field("Versión de contaCloudPrint",display(a.versionAplicacion())).field("Sistema operativo",display(a.sistemaOperativo())),crearCheckboxPdf(a.mostrarPdfAlImprimir(),false));
        if(can("agentes_impresion.editar"))d.getFooter().add(new AppActionButton(ActionType.EDIT,ButtonSize.MAIN,"Editar agente de impresión",e->{d.close();editar(id);}));
        if(can("agentes_impresion.codigo"))d.getFooter().add(new AppActionButton(ActionType.LINK,ButtonSize.MAIN,"Generar nuevo código de vinculación",e->confirmCode(a,d)));
        if(a.estado()!=EstadoAgenteImpresion.REVOKED&&can("agentes_impresion.revocar"))d.getFooter().add(new AppActionButton(ActionType.VOID,ButtonSize.MAIN,"Revocar agente",e->confirmRevoke(a,d)));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
    }catch(RuntimeException ex){error(ex,"No fue posible consultar el agente de impresión.");}}
    private void confirmCode(AgenteImpresionDto a,Dialog d){
        Runnable generar=()->{try{showCode(service.generarCodigo(a.id()),a);d.close();load();}catch(RuntimeException ex){error(ex,"No fue posible generar el código.");}};
        if(a.estado()!=EstadoAgenteImpresion.ACTIVE){generar.run();return;}
        ConfirmDialog c=new ConfirmDialog();c.setHeader("Generar nuevo código de vinculación");c.setText("Este agente ya tiene un equipo vinculado. El equipo actual continuará funcionando hasta completar la nueva vinculación. ¿Deseas continuar?");
        c.setCancelable(true);c.setCancelText("Cancelar");c.setConfirmText("Generar código");c.addConfirmListener(e->generar.run());c.open();
    }
    private void showCode(CodigoVinculacionDto code,AgenteImpresionDto a){
        Dialog d=dialog("CÓDIGO DE VINCULACIÓN","560px");H1 value=new H1(code.codigo());value.getStyle().set("letter-spacing","0.25em").set("text-align","center");
        d.add(new Paragraph(a.codigo()+" — "+a.nombre()),new Paragraph("Cajas: "+nombresCajas(a)),new Paragraph("Ingresa este código en contaCloudPrint. Es temporal, de un solo uso y el código anterior quedó invalidado."),value,
            new Paragraph("Expira: "+DATE.format(code.expiraEn().atZoneSameInstant(ZoneId.systemDefault()))));
        d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
    }
    private void confirmRevoke(AgenteImpresionDto a,Dialog parent){
        ConfirmDialog c=new ConfirmDialog();c.setHeader("Revocar agente");
        c.setText("Este agente está asignado a "+a.cajas().size()+" cajas. Al revocarlo, estas cajas dejarán de tener disponible este agente para impresión. El dispositivo perderá acceso inmediatamente. ¿Deseas continuar?");
        c.setCancelable(true);c.setCancelText("Cancelar");c.setConfirmText("Revocar");c.setConfirmButtonTheme("error primary");
        c.addConfirmListener(e->{try{service.revocar(a.id());parent.close();load();Notification.show("Agente revocado correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible revocar el agente.");}});c.open();
    }
    private Span badge(AgenteImpresionDto a){Span badge=new Span(estadoVisual(a));String theme=a.estado()==EstadoAgenteImpresion.REVOKED?"error":a.estado()==EstadoAgenteImpresion.PENDING?"contrast":a.conectado()?"success":"contrast";badge.getElement().getThemeList().add("badge "+theme);return badge;}
    private static String estadoVisual(AgenteImpresionDto a){return a.estado()==EstadoAgenteImpresion.PENDING?"Pendiente":a.estado()==EstadoAgenteImpresion.REVOKED?"Revocado":a.conectado()?"Conectado":"Desconectado";}
    private static String estado(EstadoAgenteImpresion e){return switch(e){case PENDING->"Pendiente";case ACTIVE->"Activo";case REVOKED->"Revocado";};}
    private static String nombresCajas(AgenteImpresionDto a){return a.cajas().isEmpty()?"Sin asignar":a.cajas().stream().map(CajaAgenteImpresionDto::nombre).collect(Collectors.joining(", "));}
    private static String fecha(AgenteImpresionDto a){return a.ultimaConexion()==null?"—":DATE.format(a.ultimaConexion().atZoneSameInstant(ZoneId.systemDefault()));}
    private static Dialog dialog(String title,String width){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth(width);return d;}
    private static boolean can(String permiso){return TenantContext.principalActual().permisos().contains(permiso);}
    private static String display(String value){return value==null||value.isBlank()?"—":value;}
    private static void error(RuntimeException ex,String fallback){String message=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(message==null||message.isBlank()?fallback:message);}
}
