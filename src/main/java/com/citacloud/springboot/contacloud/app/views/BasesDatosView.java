package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.multitenancy.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.InfrastructureAdminService;
import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="bases-datos",layout=MainLayout.class)
@PageTitle("Bases de datos | ContaCloud")
@PermitAll
public class BasesDatosView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final InfrastructureAdminService service;
    private final boolean canViewDatabases=has("bases_datos.ver");
    private final boolean canViewMigrations=has("migraciones.ver");
    private final Tab databasesTab=new Tab("Bases de datos");
    private final Tab migrationsTab=new Tab("Migraciones");
    private final Tabs tabs=new Tabs();
    private final Div databasesContent=new Div();
    private final Div migrationsContent=new Div();
    private final AppGrid<DatabaseNodeDto> databaseGrid=new AppGrid<>(DatabaseNodeDto.class);
    private final TextField databaseSearch=search("Buscar...");
    private final ComboBox<DatabaseNodeType> databaseType=new ComboBox<>("Tipo");
    private final ComboBox<DatabaseNodeStatus> databaseStatus=new ComboBox<>("Estado");
    private AppPagination databasePagination;
    private final AppGrid<TenantMigrationDto> migrationGrid=new AppGrid<>(TenantMigrationDto.class);
    private final TextField migrationSearch=search("Buscar...");
    private final ComboBox<String> migrationStatus=new ComboBox<>("Estado");
    private AppPagination migrationPagination;

    public BasesDatosView(ObjectProvider<InfrastructureAdminService> provider){
        service=provider.getIfAvailable();addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        add(new AppPageHeader("BASES DE DATOS","Administra las bases de datos y las migraciones de tenants."));
        if(canViewDatabases)tabs.add(databasesTab);if(canViewMigrations)tabs.add(migrationsTab);tabs.addClassName("cc-infrastructure-tabs");add(tabs);
        databasesContent.addClassName("cc-tab-content");migrationsContent.addClassName("cc-tab-content");add(databasesContent,migrationsContent);
        if(service==null){var unavailable=new Paragraph("La administración multi-base no está habilitada en esta instalación.");unavailable.addClassName("cc-empty");databasesContent.add(unavailable);migrationsContent.add(new Paragraph(unavailable.getText()));}
        else{if(canViewDatabases)buildDatabases();if(canViewMigrations)buildMigrations();}
        tabs.addSelectedChangeListener(event->{if(!event.isFromClient())return;show(event.getSelectedTab()==migrationsTab?"migraciones":"bases",true);});
    }

    @Override public void beforeEnter(BeforeEnterEvent event){
        if(!canViewDatabases&&!canViewMigrations){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}
        String requested=event.getLocation().getQueryParameters().getParameters().getOrDefault("tab",List.of("bases")).stream().findFirst().orElse("bases");
        if("migraciones".equalsIgnoreCase(requested)&&canViewMigrations)show("migraciones",false);else if(canViewDatabases)show("bases",false);else show("migraciones",false);
    }

    private void buildDatabases(){
        var add=has("bases_datos.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nueva base de datos",e->databaseDialog(null)):null;
        databasesContent.add(sectionHeader("BASES DE DATOS","Administra las bases de datos disponibles para alojar tenants.",add));
        databaseType.setItems(DatabaseNodeType.values());databaseType.setItemLabelGenerator(BasesDatosView::typeLabel);databaseType.setPlaceholder("Todos");databaseType.setClearButtonVisible(true);
        databaseStatus.setItems(DatabaseNodeStatus.values());databaseStatus.setItemLabelGenerator(BasesDatosView::statusLabel);databaseStatus.setPlaceholder("Todos");databaseStatus.setClearButtonVisible(true);
        configureDatabaseGrid();databasePagination=new AppPagination(request->loadDatabases());
        databaseSearch.addValueChangeListener(e->resetDatabases());databaseType.addValueChangeListener(e->resetDatabases());databaseStatus.addValueChangeListener(e->resetDatabases());
        databasesContent.add(toolbar(databaseSearch,databaseType,databaseStatus),databaseGrid,databasePagination);loadDatabases();
    }

    private void configureDatabaseGrid(){
        databaseGrid.addColumn(DatabaseNodeDto::codigo).setHeader("Código").setAutoWidth(true);
        databaseGrid.addColumn(DatabaseNodeDto::nombre).setHeader("Nombre").setAutoWidth(true).setFlexGrow(1);
        databaseGrid.addColumn(n->typeLabel(n.tipo())).setHeader("Tipo").setAutoWidth(true);
        databaseGrid.addColumn(n->n.tenantsActuales()+"/"+n.tenantsMaximos()).setHeader("Tenants").setAutoWidth(true);
        databaseGrid.addColumn(n->n.capacidad().stripTrailingZeros().toPlainString()+"%").setHeader("Capacidad").setAutoWidth(true);
        databaseGrid.addColumn(n->statusLabel(n.estado())).setHeader("Estado").setAutoWidth(true);
        databaseGrid.addColumn(n->value(n.versionSchema())).setHeader("Versión").setAutoWidth(true);
        databaseGrid.addComponentColumn(this::databaseActions).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        databaseGrid.setEmptyStateText("No hay bases de datos para los filtros seleccionados.");
    }

    private Component databaseActions(DatabaseNodeDto node){var actions=new HorizontalLayout();actions.addClassName("cc-grid-actions");actions.setPadding(false);actions.setSpacing(false);actions.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver base de datos",e->viewDatabase(node.id())));if(has("bases_datos.editar")||has("bases_datos.cambiar_estado"))actions.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar base de datos",e->databaseDialog(service.obtenerBase(node.id()))));return actions;}
    private void resetDatabases(){if(databasePagination!=null){databasePagination.reset();loadDatabases();}}
    private void loadDatabases(){if(databasePagination==null||service==null)return;try{var request=databasePagination.currentRequest();var page=service.buscarBases(databaseSearch.getValue(),databaseType.getValue(),databaseStatus.getValue(),request.page(),request.size());databaseGrid.setItems(page.content());databasePagination.setTotal(page.total());}catch(RuntimeException ex){notifyError(ex,"No fue posible cargar las bases de datos.");}}

    private void buildMigrations(){
        boolean canCreate=has("migraciones.crear")&&has("migraciones.ejecutar");var add=canCreate?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nueva migración",e->migrationDialog()):null;
        migrationsContent.add(sectionHeader("MIGRACIONES","Administra las migraciones de tenants entre bases de datos.",add));
        migrationStatus.setItems("PENDIENTE","MIGRANDO","VERIFICANDO","COMPLETADA","FALLIDA");migrationStatus.setItemLabelGenerator(BasesDatosView::migrationStatusLabel);migrationStatus.setPlaceholder("Todos");migrationStatus.setClearButtonVisible(true);
        configureMigrationGrid();migrationPagination=new AppPagination(request->loadMigrations());migrationSearch.addValueChangeListener(e->resetMigrations());migrationStatus.addValueChangeListener(e->resetMigrations());
        migrationsContent.add(toolbar(migrationSearch,migrationStatus),migrationGrid,migrationPagination);loadMigrations();
    }

    private void configureMigrationGrid(){
        migrationGrid.addColumn(TenantMigrationDto::tenant).setHeader("Tenant").setAutoWidth(true).setFlexGrow(1);
        migrationGrid.addColumn(TenantMigrationDto::origen).setHeader("Base origen").setAutoWidth(true);
        migrationGrid.addColumn(TenantMigrationDto::destino).setHeader("Base destino").setAutoWidth(true);
        migrationGrid.addColumn(m->migrationStatusLabel(m.estado())).setHeader("Estado").setAutoWidth(true);
        migrationGrid.addColumn(m->format(m.iniciada())).setHeader("Iniciada").setAutoWidth(true);
        migrationGrid.addColumn(m->format(m.finalizada())).setHeader("Finalizada").setAutoWidth(true);
        migrationGrid.addComponentColumn(m->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver migración",e->viewMigration(m.id()))).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        migrationGrid.setEmptyStateText("No hay migraciones para los filtros seleccionados.");
    }

    private void resetMigrations(){if(migrationPagination!=null){migrationPagination.reset();loadMigrations();}}
    private void loadMigrations(){if(migrationPagination==null||service==null)return;try{var request=migrationPagination.currentRequest();var page=service.buscarMigraciones(migrationSearch.getValue(),migrationStatus.getValue(),request.page(),request.size());migrationGrid.setItems(page.content());migrationPagination.setTotal(page.total());}catch(RuntimeException ex){notifyError(ex,"No fue posible cargar las migraciones.");}}

    private void databaseDialog(DatabaseNodeDto current){
        boolean creating=current==null;var dialog=new Dialog();dialog.setHeaderTitle(creating?"Nueva base de datos":"Editar base de datos");dialog.setWidth("min(860px, 96vw)");
        var code=new TextField("Código");code.setRequired(true);code.setMaxLength(30);code.setReadOnly(!creating);if(current!=null)code.setValue(current.codigo());
        var name=new TextField("Nombre");name.setRequired(true);if(current!=null)name.setValue(current.nombre());
        var type=new ComboBox<DatabaseNodeType>("Tipo");type.setItems(DatabaseNodeType.values());type.setItemLabelGenerator(BasesDatosView::typeLabel);type.setRequired(true);type.setValue(current==null?DatabaseNodeType.COMPARTIDA:current.tipo());
        var status=new ComboBox<DatabaseNodeStatus>("Estado");status.setItems(DatabaseNodeStatus.values());status.setItemLabelGenerator(BasesDatosView::statusLabel);status.setRequired(true);status.setValue(current==null?DatabaseNodeStatus.INACTIVA:current.estado());
        var host=new TextField("Referencia de host");host.setRequired(true);host.setHelperText("Alias administrativo; no escriba credenciales ni cadenas JDBC.");
        var dbName=new TextField("Nombre de base");dbName.setRequired(true);if(current!=null)dbName.setValue(current.nombreBase()==null?"conservar":current.nombreBase());
        var region=new TextField("Región");if(current!=null)region.setValue(value(current.region()));var max=new IntegerField("Capacidad máxima de tenants");max.setMin(1);max.setStepButtonsVisible(true);max.setValue(current==null?100:current.tenantsMaximos());
        var threshold=new BigDecimalField("Umbral de capacidad (%)");threshold.setValue(current==null?BigDecimal.valueOf(90):current.umbralCapacidad());
        var schema=new TextField("Versión de schema");if(current!=null)schema.setValue(value(current.versionSchema()));
        var healthy=new com.vaadin.flow.component.checkbox.Checkbox("Saludable",current!=null&&current.saludable());
        if(current!=null){host.setPlaceholder("Conservar referencia actual");host.setValue("conservar");boolean canEdit=has("bases_datos.editar");name.setReadOnly(!canEdit);type.setReadOnly(!canEdit);host.setReadOnly(!canEdit);dbName.setReadOnly(!canEdit);region.setReadOnly(!canEdit);max.setReadOnly(!canEdit);threshold.setReadOnly(!canEdit);schema.setReadOnly(!canEdit);healthy.setReadOnly(!canEdit);status.setReadOnly(!has("bases_datos.cambiar_estado"));}
        var form=new Div(code,name,type,status,host,dbName,region,max,threshold,schema,healthy);form.addClassName("cc-dialog-form");dialog.add(form);
        var save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{String hostValue=current!=null&&"conservar".equals(host.getValue())?"conservar":host.getValue();var input=new DatabaseNodeInputDto(code.getValue(),name.getValue(),type.getValue(),status.getValue(),hostValue,dbName.getValue(),region.getValue(),max.getValue()==null?0:max.getValue(),threshold.getValue(),schema.getValue(),healthy.getValue());if(creating)service.crearBase(input);else service.actualizarBase(current.id(),input);dialog.close();loadDatabases();Notification.show("Base de datos guardada correctamente.");}catch(RuntimeException ex){notifyError(ex,"No fue posible guardar la base de datos.");}});
        dialog.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->dialog.close()));dialog.open();
    }

    private void viewDatabase(UUID id){try{DatabaseNodeDto n=service.obtenerBase(id);var dialog=new Dialog();dialog.setHeaderTitle("Detalle de base de datos");var details=new AppDetailSection("Información").field("Código",n.codigo()).field("Nombre",n.nombre()).field("Tipo",typeLabel(n.tipo())).field("Tenants",n.tenantsActuales()+"/"+n.tenantsMaximos()).field("Capacidad",n.capacidad()+"%").field("Estado",statusLabel(n.estado())).field("Versión",n.versionSchema()).field("Salud",n.saludable()?"Saludable":"No saludable");if(n.nombreBase()!=null)details.field("Nombre de base",n.nombreBase());dialog.add(details);dialog.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialog.close()));dialog.open();}catch(RuntimeException ex){notifyError(ex,"No fue posible cargar la base de datos.");}}

    private void migrationDialog(){
        var dialog=new Dialog();dialog.setHeaderTitle("Nueva migración");dialog.setWidth("min(720px, 96vw)");var tenant=new ComboBox<TenantDirectoryOption>("Tenant");tenant.setRequired(true);tenant.setWidthFull();tenant.setItems(service.buscarTenants(""));tenant.setItemLabelGenerator(t->t.nombre()+" · "+t.tenantId());
        var source=new TextField("Base de datos origen");source.setReadOnly(true);source.setWidthFull();var target=new ComboBox<DatabaseNodeDto>("Base de datos destino");target.setRequired(true);target.setWidthFull();target.setItemLabelGenerator(n->n.codigo()+" · "+n.nombre()+" · "+n.capacidad()+"%");
        tenant.addValueChangeListener(e->{target.clear();TenantDirectoryOption selected=e.getValue();source.setValue(selected==null?"":selected.databaseNode().code()+" · "+selected.databaseNode().name());target.setItems(selected==null?List.of():service.destinosElegibles(selected.tenantId()));});
        var form=new Div(tenant,source,target);form.addClassName("cc-dialog-form");dialog.add(form);var save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{if(tenant.getValue()==null||target.getValue()==null){Notification.show("Seleccione el tenant y la base de datos destino.");return;}var confirm=new ConfirmDialog();confirm.setHeader("Confirmar migración");confirm.setText("Esta operación moverá el tenant y todas sus empresas a la base de datos seleccionada. ¿Deseas continuar?");confirm.setCancelable(true);confirm.setCancelText("Cancelar");confirm.setConfirmText("Programar migración");confirm.setConfirmButtonTheme("primary");confirm.addConfirmListener(x->{try{service.programarMigracion(tenant.getValue().tenantId(),target.getValue().id());confirm.close();dialog.close();loadMigrations();Notification.show("Migración programada correctamente.");}catch(RuntimeException ex){notifyError(ex,"No fue posible programar la migración.");}});confirm.open();});dialog.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->dialog.close()));dialog.open();
    }

    private void viewMigration(UUID id){try{TenantMigrationDto m=service.obtenerMigracion(id);var dialog=new Dialog();dialog.setHeaderTitle("Detalle de migración");dialog.setWidth("min(780px, 96vw)");dialog.add(new AppDetailSection("Información").field("Tenant",m.tenant()).field("Estado",migrationStatusLabel(m.estado())).field("Base origen",m.origen()).field("Base destino",m.destino()).field("Fecha inicio",format(m.iniciada())).field("Fecha finalización",format(m.finalizada())));var stages=new Div();stages.addClassName("cc-migration-stages");stages.add(new H3("PROGRESO / ETAPAS"));for(String stage:stages(m.estado()))stages.add(new Paragraph(stage));dialog.add(stages);dialog.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialog.close()));dialog.open();}catch(RuntimeException ex){notifyError(ex,"No fue posible cargar la migración.");}}

    private void show(String key,boolean updateUrl){boolean migrations="migraciones".equals(key)&&canViewMigrations;databasesContent.setVisible(!migrations&&canViewDatabases);migrationsContent.setVisible(migrations);tabs.setSelectedTab(migrations?migrationsTab:databasesTab);if(updateUrl)UI.getCurrent().getPage().getHistory().replaceState(null,"bases-datos?tab="+(migrations?"migraciones":"bases"));}
    private static Div sectionHeader(String title,String description,Component action){var copy=new Div(new H2(title),new Paragraph(description));copy.addClassName("cc-section-header-copy");var header=new Div(copy);header.addClassName("cc-section-header");if(action!=null)header.add(action);return header;}
    private static HorizontalLayout toolbar(Component... fields){var layout=new HorizontalLayout(fields);layout.addClassName("cc-filter-bar");layout.setWidthFull();layout.setAlignItems(Alignment.END);return layout;}
    private static TextField search(String placeholder){var field=new TextField();field.setPlaceholder(placeholder);field.setClearButtonVisible(true);field.setValueChangeMode(ValueChangeMode.LAZY);field.setValueChangeTimeout(350);return field;}
    private static String typeLabel(DatabaseNodeType type){return type==DatabaseNodeType.DEDICADA?"Dedicada":"Compartida";}
    private static String statusLabel(DatabaseNodeStatus status){if(status==null)return "";return switch(status){case ACTIVA->"Activa";case DRENANDO->"Drenando";case LLENA->"Llena";case MANTENIMIENTO->"Mantenimiento";case INACTIVA->"Inactiva";};}
    private static String migrationStatusLabel(String status){if(status==null)return "";return switch(status){case "PENDIENTE"->"Pendiente";case "MIGRANDO"->"Migrando";case "VERIFICANDO"->"Verificando";case "COMPLETADA"->"Completada";case "FALLIDA"->"Fallida";default->status;};}
    private static List<String> stages(String status){List<String> names=List.of("Preparando destino","Copiando datos","Verificando","Actualizando routing","Invalidando caché","Completada");int completed=switch(value(status)){case "COMPLETADA"->6;case "VERIFICANDO"->2;case "MIGRANDO"->1;default->0;};List<String> result=new ArrayList<>();for(int i=0;i<names.size();i++)result.add((i<completed?"✓ ":"○ ")+names.get(i));return result;}
    private static String format(OffsetDateTime value){return value==null?"—":value.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME);}
    private static boolean has(String permission){return TenantContext.principalActual().permisos().contains(permission);}
    private static String value(String value){return value==null?"":value;}
    private static void notifyError(RuntimeException ex,String fallback){Notification.show(ex instanceof ReglaNegocioException&&ex.getMessage()!=null?ex.getMessage():fallback);}
}
