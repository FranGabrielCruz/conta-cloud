package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.combobox.*;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.*;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="inventario/conteo-fisico",layout=MainLayout.class)
@PageTitle("Conteo físico | ContaCloud")
@PermitAll
public class InventarioConteoFisicoView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormat NUMBER=new DecimalFormat("#,##0.####");
    private final ConteoFisicoService service;
    private final ConteoFisicoDocumentoService documents;
    private final EmpresaModuloService modules;
    private ConteoFisicoCatalogosDto catalogs;
    private final TextField search=new TextField("Buscar");
    private final DatePicker from=date("Desde"),to=date("Hasta");
    private final Select<EstadoConteoFisico> state=new Select<>();
    private final ComboBox<String> branch=new ComboBox<>("Sucursal");
    private final ComboBox<ConteoFisicoCatalogosDto.Almacen> warehouse=new ComboBox<>("Almacén");
    private final Select<TipoConteoFisico> type=new Select<>();
    private final AppGrid<ConteoFisicoDto> grid=new AppGrid<>(ConteoFisicoDto.class);
    private final AppPagination pagination;

    public InventarioConteoFisicoView(ConteoFisicoService service,ConteoFisicoDocumentoService documents,EmpresaModuloService modules){
        this.service=service;this.documents=documents;this.modules=modules;
        addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        Component add=can("conteo_fisico.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo conteo físico",e->form(null)):null;
        add(add==null?new AppPageHeader("CONTEOS FÍSICOS","Administra sesiones de conteo físico de inventario."):new AppPageHeader("CONTEOS FÍSICOS","Administra sesiones de conteo físico de inventario.",add));
        configureFilters();configureGrid();pagination=new AppPagination(x->load());
        HorizontalLayout filters=new HorizontalLayout(search,from,to,state,branch,warehouse,type);filters.addClassName("cc-filter-bar");filters.setAlignItems(Alignment.END);filters.setWidthFull();
        add(filters,grid,pagination);
    }

    @Override public void beforeEnter(BeforeEnterEvent event){
        if(!modules.habilitado("INVENTARIO")||!can("conteo_fisico.ver")){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}
        try{catalogs=service.catalogos();warehouse.setItems(catalogs.almacenes());branch.setItems(catalogs.almacenes().stream().map(ConteoFisicoCatalogosDto.Almacen::sucursal).distinct().sorted().toList());load();}
        catch(RuntimeException e){error(e,"No fue posible cargar los conteos físicos.");}
    }

    private void configureFilters(){
        search.setPlaceholder("Número, referencia, almacén o producto...");search.setClearButtonVisible(true);search.setValueChangeMode(ValueChangeMode.LAZY);
        state.setLabel("Estado");state.setItems(EstadoConteoFisico.values());state.setItemLabelGenerator(x->x==null?"Todos":x.getEtiqueta());state.setEmptySelectionAllowed(true);state.setEmptySelectionCaption("Todos");
        type.setLabel("Tipo");type.setItems(TipoConteoFisico.values());type.setItemLabelGenerator(x->x==null?"Todos":x.getEtiqueta());type.setEmptySelectionAllowed(true);type.setEmptySelectionCaption("Todos");
        warehouse.setClearButtonVisible(true);warehouse.setItemLabelGenerator(x->x.nombre()+" · "+x.sucursal());branch.setClearButtonVisible(true);
        branch.addValueChangeListener(e->{if(catalogs!=null){warehouse.clear();warehouse.setItems(catalogs.almacenes().stream().filter(x->e.getValue()==null||x.sucursal().equals(e.getValue())).toList());}pagination.reset();load();});
        for(HasValue<?,?> field:List.of(search,from,to,state,warehouse,type))field.addValueChangeListener(e->{if(e.isFromClient()){pagination.reset();load();}});
    }

    private void configureGrid(){
        grid.addColumn(ConteoFisicoDto::numero).setHeader("Número").setAutoWidth(true);
        grid.addColumn(x->DATE.format(x.fecha())).setHeader("Fecha").setAutoWidth(true);
        grid.addColumn(x->x.almacen()+" · "+x.sucursal()).setHeader("Almacén").setFlexGrow(1);
        grid.addColumn(x->x.tipo().getEtiqueta()).setHeader("Tipo").setAutoWidth(true);
        grid.addComponentColumn(x->badge(x.estado())).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No hay conteos físicos registrados.");
    }

    private Component actions(ConteoFisicoDto c){
        HorizontalLayout actions=new HorizontalLayout();actions.addClassName("cc-grid-actions");actions.setPadding(false);actions.setSpacing(false);
        actions.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver conteo",e->view(c.id())));
        if(c.estado()==EstadoConteoFisico.DRAFT&&can("conteo_fisico.editar"))actions.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar borrador",e->form(c.id())));
        if(c.estado()==EstadoConteoFisico.IN_PROGRESS&&can("conteo_fisico.contar"))actions.add(new AppActionButton(ActionType.COUNT,ButtonSize.GRID_ACTION,"Realizar conteo",e->perform(c.id())));
        if(c.estado()==EstadoConteoFisico.PENDING_CONFIRMATION&&can("conteo_fisico.confirmar"))actions.add(new AppActionButton(ActionType.REVIEW,ButtonSize.GRID_ACTION,"Revisar conteo",e->review(c.id())));
        return actions;
    }

    private void load(){if(catalogs==null)return;try{var r=pagination.currentRequest();var page=service.buscar(search.getValue(),from.getValue(),to.getValue(),state.getValue(),warehouse.getValue()==null?null:warehouse.getValue().id(),type.getValue(),r.page(),r.size());grid.setItems(page.getContent());pagination.setTotal(page.getTotalElements());}catch(RuntimeException e){error(e,"No fue posible cargar los conteos físicos.");}}

    private void form(UUID id){
        try{
            ConteoFisicoDto current=id==null?null:service.obtenerCabecera(id);Dialog d=dialog(current==null?"NUEVO CONTEO FÍSICO":"EDITAR CONTEO FÍSICO",1000);
            DatePicker date=date("Fecha");date.setRequiredIndicatorVisible(true);date.setValue(current==null?LocalDate.now():current.fecha());
            ComboBox<ConteoFisicoCatalogosDto.Almacen> w=new ComboBox<>("Almacén");w.setItems(catalogs.almacenes());w.setItemLabelGenerator(x->x.nombre()+" · "+x.sucursal());w.setRequiredIndicatorVisible(true);
            Span branchName=new Span("Selecciona un almacén");VerticalLayout warehouseBlock=new VerticalLayout(w,branchName);warehouseBlock.setPadding(false);warehouseBlock.setSpacing(false);warehouseBlock.setWidthFull();w.setWidthFull();w.addValueChangeListener(e->branchName.setText(e.getValue()==null?"Selecciona un almacén":e.getValue().sucursal()));
            Select<TipoConteoFisico> countType=new Select<>();countType.setLabel("Tipo");countType.setItems(TipoConteoFisico.values());countType.setItemLabelGenerator(TipoConteoFisico::getEtiqueta);countType.setValue(current==null?TipoConteoFisico.COMPLETE:current.tipo());
            Select<ModoConteoFisico> mode=new Select<>();mode.setLabel("Modo");mode.setItems(ModoConteoFisico.values());mode.setItemLabelGenerator(ModoConteoFisico::getEtiqueta);mode.setValue(current==null?ModoConteoFisico.VISIBLE:current.modo());
            ComboBox<ConteoFisicoCatalogosDto.Categoria> category=new ComboBox<>("Categoría (opcional)");category.setItems(catalogs.categorias());category.setItemLabelGenerator(ConteoFisicoCatalogosDto.Categoria::nombre);category.setClearButtonVisible(true);
            MultiSelectComboBox<ConteoFisicoCatalogosDto.Producto> products=new MultiSelectComboBox<>("Productos específicos (opcional)");products.setItems(catalogs.productos());products.setItemLabelGenerator(x->x.codigo()+" · "+x.nombre());
            TextField reference=new TextField("Referencia");reference.setMaxLength(100);TextArea notes=new TextArea("Observación");notes.setMaxLength(1000);notes.addClassName("cc-dialog-span-2");
            countType.addValueChangeListener(e->{boolean partial=e.getValue()==TipoConteoFisico.PARTIAL;category.setVisible(partial);products.setVisible(partial);});
            if(current!=null){catalogs.almacenes().stream().filter(x->x.id().equals(current.almacenId())).findFirst().ifPresent(w::setValue);catalogs.categorias().stream().filter(x->Objects.equals(x.id(),current.categoriaId())).findFirst().ifPresent(category::setValue);Set<ConteoFisicoCatalogosDto.Producto> selected=new HashSet<>();catalogs.productos().stream().filter(x->current.productosAlcance().contains(x.id())).forEach(selected::add);products.setValue(selected);reference.setValue(value(current.referencia()));notes.setValue(value(current.observacion()));}else{category.setVisible(false);products.setVisible(false);}
            d.add(section("INFORMACIÓN GENERAL",date,warehouseBlock,countType,mode,category,products,reference,notes));
            java.util.function.Supplier<ConteoFisicoInput> input=()->new ConteoFisicoInput(date.getValue(),w.getValue()==null?null:w.getValue().id(),category.getValue()==null?null:category.getValue().id(),countType.getValue(),mode.getValue(),reference.getValue(),notes.getValue(),current==null?null:current.version(),products.getSelectedItems().stream().map(ConteoFisicoCatalogosDto.Producto::id).toList());
            AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{if(current==null)service.crear(input.get());else service.actualizar(current.id(),input.get());d.close();load();Notification.show("Conteo guardado como borrador.");}catch(RuntimeException x){error(x,"No fue posible guardar el conteo.");}});
            AppActionButton start=new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Iniciar conteo",e->{try{ConteoFisicoDto saved=current==null?service.crear(input.get()):service.actualizar(current.id(),input.get());service.iniciar(saved.id());d.close();load();perform(saved.id());}catch(RuntimeException x){error(x,"No fue posible iniciar el conteo.");}});
            d.getFooter().add(save,start,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close()));d.open();
        }catch(RuntimeException e){error(e,"No fue posible abrir el conteo físico.");}
    }

    private void view(UUID id){readOnly(id,false);}
    private void review(UUID id){readOnly(id,true);}

    private void readOnly(UUID id,boolean review){
        try{
            ConteoFisicoDto c=service.obtenerCabecera(id);Dialog d=dialog(review?"RESULTADO DEL CONTEO":"CONTEO FÍSICO",1250);d.addClassName("cc-count-dialog");
            Div heading=new Div(new H2(c.numero()),badge(c.estado()));heading.addClassName("cc-detail-heading");d.add(heading,information(c,false));
            Div summary=summary();d.add(summary);
            TextField productSearch=new TextField("Buscar producto");productSearch.setPlaceholder("Código o producto...");productSearch.setClearButtonVisible(true);productSearch.setValueChangeMode(ValueChangeMode.LAZY);
            AppGrid<ConteoFisicoDto.Linea> table=readOnlyGrid(c);AppPagination[] pager={null};Runnable loadLines=()->{try{var request=pager[0].currentRequest();PaginaLineasConteoFisicoDto page=service.buscarLineas(c.id(),productSearch.getValue(),request.page(),request.size());table.setItems(page.items());pager[0].setTotal(page.total());updateSummary(summary,c,page.contados(),page.pendientes());}catch(RuntimeException e){error(e,"No fue posible cargar los productos del conteo.");}};
            pager[0]=new AppPagination(x->loadLines.run());productSearch.addValueChangeListener(e->{if(e.isFromClient()){pager[0].reset();loadLines.run();}});d.add(productSearch,table,pager[0]);loadLines.run();
            if(review&&c.estado()==EstadoConteoFisico.PENDING_CONFIRMATION&&can("conteo_fisico.confirmar"))d.getFooter().add(new AppActionButton(ActionType.ISSUE,ButtonSize.MAIN,"Confirmar conteo",e->confirm("CONFIRMAR CONTEO","Se recalcularán las existencias y se generarán movimientos solamente por las diferencias.",()->{try{service.confirmar(c.id());d.close();load();Notification.show("Conteo confirmado.");try{openPdf(documents.resultado(c.id()));}catch(RuntimeException pdf){Notification.show("El conteo fue confirmado, pero el PDF no pudo abrirse. Puede generarlo desde Ver conteo.");}}catch(RuntimeException x){error(x,"No fue posible confirmar el conteo.");}})));
            if(!review&&((c.estado()==EstadoConteoFisico.IN_PROGRESS||c.estado()==EstadoConteoFisico.PENDING_CONFIRMATION)&&can("conteo_fisico.hoja_pdf")))d.getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Generar hoja de conteo",e->openPdf(documents.hoja(c.id()))));
            if(!review&&c.estado()==EstadoConteoFisico.CONFIRMED&&can("conteo_fisico.resultado_pdf"))d.getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Generar PDF",e->openPdf(documents.resultado(c.id()))));
            d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));d.open();
        }catch(RuntimeException e){error(e,"No fue posible consultar el conteo.");}
    }

    private void perform(UUID id){
        try{
            ConteoFisicoDto c=service.obtenerCabecera(id);if(c.estado()!=EstadoConteoFisico.IN_PROGRESS)throw new ReglaNegocioException("Solo los conteos en curso permiten capturar cantidades.");
            Dialog d=dialog("REALIZAR CONTEO FÍSICO",1250);d.addClassName("cc-count-dialog");Div heading=new Div(new H2(c.numero()),badge(c.estado()));heading.addClassName("cc-detail-heading");d.add(heading,information(c,true));
            TextField productSearch=new TextField("Buscar");productSearch.setPlaceholder("Buscar por código o producto...");productSearch.setClearButtonVisible(true);productSearch.setValueChangeMode(ValueChangeMode.LAZY);
            Span counted=new Span(),pending=new Span();HorizontalLayout counters=new HorizontalLayout(counted,pending);counters.addClassName("cc-count-counters");counters.setWidthFull();
            AppGrid<ConteoFisicoDto.Linea> table=new AppGrid<>(ConteoFisicoDto.Linea.class);table.addColumn(ConteoFisicoDto.Linea::codigo).setHeader("Código").setAutoWidth(true);table.addColumn(ConteoFisicoDto.Linea::producto).setHeader("Producto").setFlexGrow(1);table.addColumn(ConteoFisicoDto.Linea::unidad).setHeader("Unidad").setAutoWidth(true);if(c.modo()==ModoConteoFisico.VISIBLE)table.addColumn(x->number(x.inicial())).setHeader("Teórico").setAutoWidth(true);
            Map<UUID,BigDecimal> dirty=new LinkedHashMap<>();table.addComponentColumn(line->{BigDecimalField field=new BigDecimalField();field.setWidth("150px");field.setClearButtonVisible(true);field.setValue(line.contado());field.addValueChangeListener(e->{if(e.isFromClient())dirty.put(line.id(),e.getValue());});return field;}).setHeader("Cantidad física").setAutoWidth(true).setFlexGrow(0);
            AppPagination[] pager={null};Runnable persist=()->{if(dirty.isEmpty())return;service.guardarCaptura(c.id(),new LinkedHashMap<>(dirty));dirty.clear();};Runnable loadLines=()->{var request=pager[0].currentRequest();PaginaLineasConteoFisicoDto page=service.buscarLineas(c.id(),productSearch.getValue(),request.page(),request.size());table.setItems(page.items());pager[0].setTotal(page.total());counted.setText("Contados: "+page.contados());pending.setText("Pendientes: "+page.pendientes());};
            pager[0]=new AppPagination(x->{try{persist.run();loadLines.run();}catch(RuntimeException e){error(e,"No fue posible cambiar de página.");}},5,5,10,20,50);
            productSearch.addValueChangeListener(e->{if(e.isFromClient())try{persist.run();pager[0].reset();loadLines.run();}catch(RuntimeException x){error(x,"No fue posible buscar productos.");}});
            HorizontalLayout tools=new HorizontalLayout(productSearch);tools.addClassName("cc-count-tools");tools.setWidthFull();d.add(tools,counters,table,pager[0]);loadLines.run();
            d.getFooter().add(new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{try{persist.run();loadLines.run();Notification.show("Progreso guardado.");}catch(RuntimeException x){error(x,"No fue posible guardar el conteo.");}}),new AppActionButton(ActionType.FINALIZE,ButtonSize.MAIN,"Finalizar conteo",e->{try{persist.run();service.finalizar(c.id());d.close();load();review(c.id());}catch(RuntimeException x){error(x,"No fue posible finalizar el conteo.");}}),new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->{try{persist.run();d.close();load();}catch(RuntimeException x){error(x,"No fue posible guardar los cambios antes de cerrar.");}}));d.open();
        }catch(RuntimeException e){error(e,"No fue posible abrir la captura del conteo.");}
    }

    private AppGrid<ConteoFisicoDto.Linea> readOnlyGrid(ConteoFisicoDto c){
        AppGrid<ConteoFisicoDto.Linea> table=new AppGrid<>(ConteoFisicoDto.Linea.class);table.addColumn(ConteoFisicoDto.Linea::codigo).setHeader("Código").setAutoWidth(true);table.addColumn(ConteoFisicoDto.Linea::producto).setHeader("Producto").setFlexGrow(1);table.addColumn(ConteoFisicoDto.Linea::unidad).setHeader("Unidad").setAutoWidth(true);
        if(c.modo()==ModoConteoFisico.VISIBLE||c.estado()!=EstadoConteoFisico.IN_PROGRESS)table.addColumn(x->number(c.estado()==EstadoConteoFisico.CONFIRMED||c.estado()==EstadoConteoFisico.PENDING_CONFIRMATION?x.teorico():x.inicial())).setHeader(c.estado()==EstadoConteoFisico.CONFIRMED||c.estado()==EstadoConteoFisico.PENDING_CONFIRMATION?"Teórico":"Teórico inicial").setAutoWidth(true);
        if(c.estado()!=EstadoConteoFisico.DRAFT){table.addColumn(x->x.contado()==null?"Pendiente":number(x.contado())).setHeader("Físico").setAutoWidth(true);}
        if(c.estado()==EstadoConteoFisico.PENDING_CONFIRMATION||c.estado()==EstadoConteoFisico.CONFIRMED)table.addColumn(x->signed(x.diferencia())).setHeader("Diferencia").setAutoWidth(true);
        return table;
    }

    private AppDetailSection information(ConteoFisicoDto c,boolean showProducts){AppDetailSection info=new AppDetailSection("INFORMACIÓN GENERAL");info.addClassName("cc-count-detail");return info.field("Número",c.numero()).field("Fecha",DATE.format(c.fecha())).field("Almacén",c.almacen()).field("Sucursal",c.sucursal()).field("Tipo",c.tipo().getEtiqueta()).field("Modo",c.modo().getEtiqueta()).field("Estado",c.estado().getEtiqueta()).field(showProducts?"Productos":"Referencia",showProducts?String.valueOf(c.cantidadProductos()):display(c.referencia()));}
    private static Div summary(){Div d=new Div();d.addClassNames("cc-count-summary","cc-card");return d;}
    private static void updateSummary(Div d,ConteoFisicoDto c,long counted,long pending){d.removeAll();long total=counted+pending;if(c.estado()==EstadoConteoFisico.CONFIRMED||c.estado()==EstadoConteoFisico.PENDING_CONFIRMATION){long differences=c.cantidadDiferencias();d.add(metric("Productos incluidos",String.valueOf(total)),metric("Sin diferencias",String.valueOf(Math.max(0,total-differences))),metric("Con diferencias",String.valueOf(differences)),metric("Total",String.valueOf(total)));}else d.add(metric("Productos incluidos",String.valueOf(total)),metric("Contados",String.valueOf(counted)),metric("Pendientes",String.valueOf(pending)),metric("Diferencias","—"));}
    private static Div metric(String label,String value){Span amount=new Span(value);amount.addClassName("cc-count-metric-value");Div d=new Div(new Span(label),amount);d.addClassName("cc-count-metric");return d;}
    private void openPdf(ArchivoPdfDto file){try{StreamResource resource=new StreamResource(file.nombreArchivo(),()->new ByteArrayInputStream(file.contenido()));resource.setContentType("application/pdf");resource.setCacheTime(0);var registered=VaadinSession.getCurrent().getResourceRegistry().registerResource(resource);UI.getCurrent().getPage().open(registered.getResourceUri().toString(),"_blank");}catch(RuntimeException e){error(e,"No fue posible abrir el PDF.");}}
    private static void confirm(String title,String text,Runnable action){ConfirmDialog d=new ConfirmDialog(title,text,"Confirmar",e->action.run(),"Cancelar",e->{});d.open();}
    private static Div section(String title,Component...fields){Div s=new Div();s.addClassName("cc-form-section");H3 h=new H3(title);h.addClassName("cc-section-heading");Div f=new Div(fields);f.addClassName("cc-dialog-form");s.add(h,f);return s;}
    private static Dialog dialog(String title,int width){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth("min("+width+"px, 96vw)");return d;}
    private static DatePicker date(String label){DatePicker d=new DatePicker(label);d.setLocale(new Locale("es","DO"));d.setPlaceholder("DD/MM/YYYY");d.setClearButtonVisible(true);return d;}
    private static Span badge(EstadoConteoFisico state){Span b=new Span(state.getEtiqueta());b.getElement().getThemeList().add("badge "+(state==EstadoConteoFisico.CONFIRMED?"success":state==EstadoConteoFisico.CANCELLED?"error":state==EstadoConteoFisico.PENDING_CONFIRMATION?"warning":"contrast"));return b;}
    private static String number(BigDecimal value){return value==null?"—":NUMBER.format(value);}
    private static String signed(BigDecimal value){return value==null?"—":(value.signum()>0?"+":"")+NUMBER.format(value);}
    private static String display(String value){return value==null||value.isBlank()?"—":value;}
    private static String value(String value){return value==null?"":value;}
    private static boolean can(String permission){return TenantContext.principalActual().permisos().contains(permission);}
    private static void error(RuntimeException e,String fallback){Notification.show(e instanceof ReglaNegocioException||e instanceof RecursoNoEncontradoException?e.getMessage():fallback);}
}
