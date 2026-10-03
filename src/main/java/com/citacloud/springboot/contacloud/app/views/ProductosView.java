package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.TipoProducto;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

@Route(value="productos",layout=MainLayout.class)
@RouteAlias(value="productos/:id",layout=MainLayout.class)
@PageTitle("Productos | ContaCloud")
@PermitAll
public class ProductosView extends VerticalLayout implements BeforeEnterObserver {
    private final ProductService service;private final EmpresaModuloService modules;
    private final TextField search=new TextField("Buscar");private final ComboBox<ProductoCatalogosDto.CategoriaOpcion> category=new ComboBox<>("Categoría");
    private final Select<String> type=new Select<>(),state=new Select<>();private final AppGrid<ProductoDto> grid=new AppGrid<>(ProductoDto.class);
    private final AppPagination pagination;private UUID requestedDetail;
    public ProductosView(ProductService service,EmpresaModuloService modules){this.service=service;this.modules=modules;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        Component create=can("productos.crear")?new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo producto",e->form(null)):null;
        add(create==null?new AppPageHeader("PRODUCTOS","Administra los productos y servicios utilizados en las operaciones de la empresa."):
            new AppPageHeader("PRODUCTOS","Administra los productos y servicios utilizados en las operaciones de la empresa.",create));
        search.setPlaceholder("Buscar por nombre, código o categoría...");search.setClearButtonVisible(true);search.setValueChangeMode(ValueChangeMode.LAZY);search.setValueChangeTimeout(400);
        category.setPlaceholder("Todas");category.setClearButtonVisible(true);category.setItemLabelGenerator(ProductoCatalogosDto.CategoriaOpcion::nombre);
        type.setLabel("Tipo");type.setItems("Todos","Producto","Servicio");type.setValue("Todos");state.setLabel("Estado");state.setItems("Todos","Activo","Inactivo");state.setValue("Todos");
        search.addValueChangeListener(e->{if(e.isFromClient())reset();});category.addValueChangeListener(e->{if(e.isFromClient())reset();});type.addValueChangeListener(e->{if(e.isFromClient())reset();});state.addValueChangeListener(e->{if(e.isFromClient())reset();});
        HorizontalLayout filters=new HorizontalLayout(search,category,type,state);filters.addClassName("cc-filter-bar");filters.setAlignItems(Alignment.END);configureGrid();pagination=new AppPagination(r->load());add(filters,grid,pagination);}
    @Override public void beforeEnter(BeforeEnterEvent event){if(!modules.habilitado("INVENTARIO")||!can("productos.ver")){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}
        String path=event.getLocation().getPath();if(path.startsWith("productos/")){try{requestedDetail=UUID.fromString(path.substring("productos/".length()));}catch(IllegalArgumentException ex){event.rerouteTo(ProductosView.class);return;}}
        try{category.setItems(service.catalogs().categorias());load();if(requestedDetail!=null){UUID id=requestedDetail;requestedDetail=null;UI.getCurrent().beforeClientResponse(this,x->detail(id));}}catch(RuntimeException ex){error(ex,"No fue posible cargar los productos.");}}
    private void configureGrid(){grid.addColumn(ProductoDto::codigo).setHeader("Código").setAutoWidth(true);grid.addColumn(ProductoDto::nombre).setHeader("Nombre").setFlexGrow(1);
        grid.addColumn(p->value(p.categoriaNombre())).setHeader("Categoría").setAutoWidth(true);grid.addColumn(p->typeLabel(p.tipo())).setHeader("Tipo").setAutoWidth(true);
        grid.addColumn(p->money(p.precioVenta())).setHeader("Precio").setAutoWidth(true);grid.addComponentColumn(p->badge(p.activo())).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);grid.setEmptyStateText(emptyMessage());}
    private Component actions(ProductoDto item){HorizontalLayout actions=actions();actions.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver producto",e->detail(item.id())));
        if(can("productos.editar"))actions.add(new AppActionButton(ActionType.EDIT,ButtonSize.GRID_ACTION,"Editar producto",e->form(item.id())));return actions;}
    private void load(){try{var request=pagination.currentRequest();var page=service.searchProducts(search.getValue(),category.getValue()==null?null:category.getValue().id(),typeFilter(),stateFilter(),request.page(),request.size(),"nombre",true);
        grid.setItems(page.getContent());grid.setEmptyStateText(page.getTotalElements()==0&&search.getValue().isBlank()&&category.getValue()==null&&typeFilter()==null&&stateFilter()==null?emptyMessage():"No se encontraron productos con los filtros seleccionados.");pagination.setTotal(page.getTotalElements());}catch(RuntimeException ex){error(ex,"No fue posible cargar los productos.");}}
    private void reset(){pagination.reset();load();}
    private String emptyMessage(){return can("productos.crear")?"No hay productos registrados. Crea el primer producto para comenzar a utilizarlo en las operaciones de la empresa.":"No hay productos registrados.";}
    private TipoProducto typeFilter(){return switch(type.getValue()==null?"Todos":type.getValue()){case"Producto"->TipoProducto.PRODUCT;case"Servicio"->TipoProducto.SERVICE;default->null;};}
    private Boolean stateFilter(){return switch(state.getValue()==null?"Todos":state.getValue()){case"Activo"->true;case"Inactivo"->false;default->null;};}

    private void form(UUID id){try{ProductoDto current=id==null?null:service.getProduct(id);ProductoCatalogosDto catalogs=service.catalogs();Dialog dialog=dialog(current==null?"NUEVO PRODUCTO":"EDITAR PRODUCTO");
        TextField name=new TextField("Nombre");name.setRequiredIndicatorVisible(true);name.setMaxLength(180);Select<TipoProducto> productType=new Select<>();productType.setLabel("Tipo");productType.setItems(TipoProducto.PRODUCT,TipoProducto.SERVICE);productType.setItemLabelGenerator(ProductosView::typeLabel);productType.setRequiredIndicatorVisible(true);
        ComboBox<ProductoCatalogosDto.CategoriaOpcion> productCategory=new ComboBox<>("Categoría");productCategory.setItems(catalogs.categorias());productCategory.setItemLabelGenerator(ProductoCatalogosDto.CategoriaOpcion::nombre);productCategory.setClearButtonVisible(true);
        ComboBox<ProductoCatalogosDto.UnidadOpcion> unit=new ComboBox<>("Unidad de medida");List<ProductoCatalogosDto.UnidadOpcion> unitOptions=new ArrayList<>(catalogs.unidades());if(current!=null&&unitOptions.stream().noneMatch(u->Objects.equals(u.id(),current.unidadMedidaId())))unitOptions.add(service.unitOption(current.unidadMedidaId()));unit.setItems(unitOptions);unit.setItemLabelGenerator(u->u.nombre()+" ("+u.abreviatura()+")");unit.setRequiredIndicatorVisible(true);
        TextField code=new TextField("Código interno");code.setReadOnly(true);code.setValue(current==null?"Automático al guardar":current.codigo());TextField barcode=new TextField("Código de barras");barcode.setMaxLength(100);
        TextArea description=new TextArea("Descripción");description.setMaxLength(1000);description.addClassName("cc-dialog-span-2");
        TextField purchaseCost=decimalField("Costo de compra"),salePrice=decimalField("Precio de venta");ComboBox<ProductoCatalogosDto.MonedaOpcion> currency=new ComboBox<>("Moneda");currency.setItems(catalogs.monedas());currency.setItemLabelGenerator(m->m.codigo()+" · "+m.nombre());currency.setClearButtonVisible(true);
        ComboBox<ProductoCatalogosDto.ImpuestoOpcion> purchaseTax=taxField("Impuesto de compra",catalogs),salesTax=taxField("Impuesto de venta",catalogs);
        Checkbox track=new Checkbox("Controlar existencia",true),negative=new Checkbox("Permitir existencia negativa",false);TextField minimum=decimalField("Stock mínimo");Div inventory=section("CONTROL DE INVENTARIO",track,negative,minimum);
        Checkbox active=new Checkbox("Activo",true);active.addClassName("cc-dialog-span-2");
        if(current==null){productType.setValue(TipoProducto.PRODUCT);selectId(currency,catalogs.monedas(),catalogs.monedaBaseId(),ProductoCatalogosDto.MonedaOpcion::id);}
        else{name.setValue(valueEmpty(current.nombre()));productType.setValue(current.tipo());selectId(productCategory,catalogs.categorias(),current.categoriaId(),ProductoCatalogosDto.CategoriaOpcion::id);selectId(unit,unitOptions,current.unidadMedidaId(),ProductoCatalogosDto.UnidadOpcion::id);barcode.setValue(valueEmpty(current.codigoBarras()));description.setValue(valueEmpty(current.descripcion()));setDecimal(purchaseCost,current.costoCompra());setDecimal(salePrice,current.precioVenta());selectId(currency,catalogs.monedas(),current.monedaId(),ProductoCatalogosDto.MonedaOpcion::id);selectId(purchaseTax,catalogs.impuestos(),current.impuestoCompraId(),ProductoCatalogosDto.ImpuestoOpcion::id);selectId(salesTax,catalogs.impuestos(),current.impuestoVentaId(),ProductoCatalogosDto.ImpuestoOpcion::id);track.setValue(current.controlaExistencia());negative.setValue(current.permiteExistenciaNegativa());setDecimal(minimum,current.stockMinimo());active.setValue(current.activo());active.setReadOnly(true);}
        Runnable inventoryState=()->{boolean product=productType.getValue()==TipoProducto.PRODUCT;inventory.setVisible(product);if(!product){track.setValue(false);negative.setValue(false);minimum.clear();}boolean tracked=product&&track.getValue();negative.setEnabled(tracked);minimum.setEnabled(tracked);if(!tracked){negative.setValue(false);minimum.clear();}};productType.addValueChangeListener(e->inventoryState.run());track.addValueChangeListener(e->inventoryState.run());inventoryState.run();
        dialog.add(section("INFORMACIÓN GENERAL",name,productType,productCategory,unit,code,barcode,description),section("COMPRA Y VENTA",purchaseCost,salePrice,currency),section("IMPUESTOS",purchaseTax,salesTax),inventory,section("INFORMACIÓN ADICIONAL",active));
        Dirty dirty=new Dirty();List.of(name,barcode,purchaseCost,salePrice,minimum).forEach(f->f.addValueChangeListener(e->dirty.mark(e.isFromClient())));description.addValueChangeListener(e->dirty.mark(e.isFromClient()));productType.addValueChangeListener(e->dirty.mark(e.isFromClient()));productCategory.addValueChangeListener(e->dirty.mark(e.isFromClient()));unit.addValueChangeListener(e->dirty.mark(e.isFromClient()));currency.addValueChangeListener(e->dirty.mark(e.isFromClient()));purchaseTax.addValueChangeListener(e->dirty.mark(e.isFromClient()));salesTax.addValueChangeListener(e->dirty.mark(e.isFromClient()));track.addValueChangeListener(e->dirty.mark(e.isFromClient()));negative.addValueChangeListener(e->dirty.mark(e.isFromClient()));
        AppActionButton save=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",null);save.addClickListener(e->{save.setEnabled(false);try{ProductoInput input=new ProductoInput(name.getValue(),productType.getValue(),id(productCategory),id(unit),barcode.getValue(),description.getValue(),decimal(purchaseCost),decimal(salePrice),id(currency),id(purchaseTax),id(salesTax),track.getValue(),negative.getValue(),decimal(minimum),active.getValue(),current==null?null:current.version());if(current==null)service.createProduct(input);else service.updateProduct(current.id(),input);dirty.value=false;dialog.close();load();Notification.show(current==null?"Producto creado correctamente.":"Producto actualizado correctamente.");}catch(RuntimeException ex){save.setEnabled(true);error(ex,"No fue posible guardar el producto.");}});
        dialog.getFooter().add(save,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->closeForm(dialog,dirty)));dialog.setCloseOnEsc(false);dialog.setCloseOnOutsideClick(false);dialog.addDialogCloseActionListener(e->closeForm(dialog,dirty));dialog.open();
    }catch(RuntimeException ex){error(ex,"No fue posible abrir el producto.");}}

    private void detail(UUID id){try{ProductoDto p=service.getProduct(id);Dialog dialog=dialog(p.tipo()==TipoProducto.SERVICE?"SERVICIO":"PRODUCTO");Div content=new Div(
        new AppDetailSection("INFORMACIÓN GENERAL").field("Código",p.codigo()).field("Tipo",typeLabel(p.tipo())).field("Nombre",p.nombre()).field("Categoría",value(p.categoriaNombre())).field("Unidad",unit(p)).field("Código de barras",value(p.codigoBarras())).field("Estado",p.activo()?"Activo":"Inactivo"),
        new AppDetailSection("COMPRA Y VENTA").field("Costo de compra",moneyCurrency(p.costoCompra(),p.monedaCodigo())).field("Precio de venta",moneyCurrency(p.precioVenta(),p.monedaCodigo())),
        new AppDetailSection("IMPUESTOS").field("Impuesto de compra",tax(p.impuestoCompraNombre(),p.impuestoCompraTasa())).field("Impuesto de venta",tax(p.impuestoVentaNombre(),p.impuestoVentaTasa())));
        if(p.tipo()==TipoProducto.PRODUCT)content.add(new AppDetailSection("CONTROL DE INVENTARIO").field("Controlar existencia",yesNo(p.controlaExistencia())).field("Permitir negativo",yesNo(p.permiteExistenciaNegativa())).field("Stock mínimo",decimalDisplay(p.stockMinimo())));
        content.add(new AppDetailSection("DESCRIPCIÓN").field("Descripción",value(p.descripcion())));content.addClassName("cc-supplier-detail");dialog.add(content);
        if(can("productos.editar"))dialog.getFooter().add(new AppActionButton(ActionType.EDIT,ButtonSize.MAIN,"Editar producto",e->{dialog.close();form(p.id());}));
        if(p.activo()&&can("productos.desactivar"))dialog.getFooter().add(new AppActionButton(ActionType.DEACTIVATE,ButtonSize.MAIN,"Desactivar producto",e->confirmState(p,dialog)));
        if(!p.activo()&&can("productos.reactivar"))dialog.getFooter().add(new AppActionButton(ActionType.ACTIVATE,ButtonSize.MAIN,"Reactivar producto",e->confirmState(p,dialog)));
        dialog.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialog.close()));dialog.open();
    }catch(RuntimeException ex){error(ex,"No fue posible consultar el producto.");}}
    private void confirmState(ProductoDto p,Dialog parent){boolean deactivate=p.activo();ConfirmDialog confirm=new ConfirmDialog();confirm.setHeader(deactivate?"Desactivar producto":"Reactivar producto");confirm.setText(deactivate?"El producto permanecerá disponible en documentos históricos, pero no se propondrá en nuevas operaciones.":"El producto volverá a estar disponible para nuevas operaciones. ¿Deseas continuar?");confirm.setCancelable(true);confirm.setCancelText("Cancelar");confirm.setConfirmText(deactivate?"Desactivar":"Reactivar");if(deactivate)confirm.setConfirmButtonTheme("error primary");confirm.addConfirmListener(e->{try{if(deactivate)service.deactivateProduct(p.id());else service.reactivateProduct(p.id());parent.close();load();Notification.show(deactivate?"Producto desactivado correctamente.":"Producto reactivado correctamente.");}catch(RuntimeException ex){error(ex,"No fue posible cambiar el estado del producto.");}});confirm.open();}

    private static TextField decimalField(String label){TextField field=new TextField(label);field.setPlaceholder("0.00");field.addBlurListener(e->{try{BigDecimal value=decimal(field);field.setInvalid(false);setDecimal(field,value);}catch(ReglaNegocioException ex){field.setInvalid(true);field.setErrorMessage("Ingresa un valor numérico válido.");}});return field;}
    static BigDecimal decimal(TextField field){String raw=field.getValue()==null?"":field.getValue().trim();if(raw.isEmpty())return null;try{return new BigDecimal(raw.replace(",",""));}catch(NumberFormatException ex){throw new ReglaNegocioException("Ingresa un valor numérico válido.");}}
    private static void setDecimal(TextField field,BigDecimal value){field.setValue(value==null?"":formatter().format(value));}
    private static DecimalFormat formatter(){DecimalFormatSymbols symbols=DecimalFormatSymbols.getInstance(Locale.US);DecimalFormat format=new DecimalFormat("#,##0.00##",symbols);format.setParseBigDecimal(true);return format;}
    static String typeLabel(TipoProducto type){return type==TipoProducto.SERVICE?"Servicio":"Producto";}
    private static ComboBox<ProductoCatalogosDto.ImpuestoOpcion> taxField(String label,ProductoCatalogosDto catalogs){ComboBox<ProductoCatalogosDto.ImpuestoOpcion> field=new ComboBox<>(label);field.setItems(catalogs.impuestos());field.setItemLabelGenerator(i->tax(i.nombre(),i.tasa()));field.setClearButtonVisible(true);return field;}
    private static <T> void selectId(ComboBox<T> field,List<T> items,UUID id,java.util.function.Function<T,UUID> getter){field.setValue(items.stream().filter(x->Objects.equals(getter.apply(x),id)).findFirst().orElse(null));}
    private static <T> UUID id(ComboBox<T> field){Object value=field.getValue();if(value==null)return null;if(value instanceof ProductoCatalogosDto.CategoriaOpcion x)return x.id();if(value instanceof ProductoCatalogosDto.UnidadOpcion x)return x.id();if(value instanceof ProductoCatalogosDto.MonedaOpcion x)return x.id();return ((ProductoCatalogosDto.ImpuestoOpcion)value).id();}
    private static Div section(String title,Component...fields){Div section=new Div();section.addClassName("cc-form-section");H3 heading=new H3(title);heading.addClassName("cc-section-heading");Div form=new Div(fields);form.addClassName("cc-dialog-form");section.add(heading,form);return section;}
    private static Dialog dialog(String title){Dialog d=new Dialog();d.setHeaderTitle(title);d.setWidth("min(900px, 96vw)");return d;}
    private static HorizontalLayout actions(){HorizontalLayout h=new HorizontalLayout();h.addClassName("cc-grid-actions");h.setPadding(false);h.setSpacing(false);return h;}
    private static Span badge(boolean active){Span badge=new Span(active?"Activo":"Inactivo");badge.getElement().getThemeList().add("badge "+(active?"success":"contrast"));return badge;}
    private static String money(BigDecimal value){return value==null?"—":formatter().format(value);}
    private static String moneyCurrency(BigDecimal value,String currency){return value==null?"—":money(value)+(currency==null?"":" "+currency);}
    private static String decimalDisplay(BigDecimal value){return value==null?"—":value.stripTrailingZeros().toPlainString();}
    private static String tax(String name,BigDecimal rate){return name==null?"—":name+(rate==null?"":" ("+rate.stripTrailingZeros().toPlainString()+"%)");}
    private static String unit(ProductoDto p){return p.unidadMedidaNombre()==null?"—":p.unidadMedidaNombre()+(p.unidadMedidaAbreviatura()==null?"":" ("+p.unidadMedidaAbreviatura()+")");}
    private static String yesNo(boolean value){return value?"Sí":"No";}private static String value(String value){return value==null||value.isBlank()?"—":value;}private static String valueEmpty(String value){return value==null?"":value;}
    private static boolean can(String permission){return TenantContext.principalActual().permisos().contains(permission);}
    private static void closeForm(Dialog dialog,Dirty dirty){if(!dirty.value){dialog.close();return;}ConfirmDialog confirm=new ConfirmDialog();confirm.setHeader("Cambios sin guardar");confirm.setText("Hay cambios sin guardar. ¿Deseas descartarlos?");confirm.setCancelable(true);confirm.setCancelText("Continuar editando");confirm.setConfirmText("Descartar cambios");confirm.setConfirmButtonTheme("error primary");confirm.addConfirmListener(e->{dirty.value=false;dialog.close();});confirm.open();}
    private static void error(RuntimeException ex,String fallback){String message=ex instanceof ReglaNegocioException||ex instanceof RecursoNoEncontradoException?ex.getMessage():fallback;Notification.show(message==null||message.isBlank()?fallback:message);}
    private static final class Dirty{boolean value;void mark(boolean client){if(client)value=true;}}
}
