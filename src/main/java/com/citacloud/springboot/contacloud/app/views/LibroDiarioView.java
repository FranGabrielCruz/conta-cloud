package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.LibroDiarioPdfService;
import com.citacloud.springboot.contacloud.app.services.LibroDiarioService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="libro-diario",layout=MainLayout.class)
@PageTitle("Libro Diario | ContaCloud")
@PermitAll
public class LibroDiarioView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final LibroDiarioService servicio;
    private final LibroDiarioPdfService pdf;
    private final ModuleAuthorization autorizacion;
    private final DatePicker desde=fecha("Desde");
    private final DatePicker hasta=fecha("Hasta");
    private final ComboBox<Tipo> tipo=combo("Tipo",Tipo::etiqueta,List.of(Tipo.values()));
    private final ComboBox<Origen> origen=combo("Origen",Origen::etiqueta,List.of(Origen.values()));
    private final TextField buscar=new TextField("Buscar");
    private final Div listado=new Div();
    private final Div totales=new Div();
    private final AppPagination pagina=new AppPagination(p->cargar());
    private final AppActionButton verPdf;
    private boolean puedeVerAsientos;

    public LibroDiarioView(LibroDiarioService servicio,LibroDiarioPdfService pdf,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.pdf=pdf;
        this.autorizacion=autorizacion;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        verPdf=AppActionButton.verPdf(e->ejecutar("No fue posible abrir el PDF del Libro Diario.",()
            ->PdfEnPestana.abrir(pdf.generar(filtro()))));
        verPdf.setVisible(false);
        buscar.setWidthFull();
        buscar.setMaxLength(150);
        buscar.setPlaceholder("Asiento, cuenta, concepto, referencia o documento");
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.addClassName("cc-maintenance-search");
        var filtros=new Div(desde,hasta,tipo,origen,buscar);
        filtros.addClassName("cc-maintenance-filters");
        for(var campo:List.<HasValue<?,?>>of(desde,hasta,tipo,origen,buscar))
            campo.addValueChangeListener(e->{if(e.isFromClient()) {pagina.reset();cargar();}});
        listado.addClassName("cc-daily-journal-list");
        totales.addClassNames("cc-card","cc-daily-journal-totals");
        add(new AppPageHeader("LIBRO DIARIO",null,verPdf),filtros,listado,totales,pagina);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if(!autorizacion.canAccess("CONTABILIDAD","libro_diario.ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        puedeVerAsientos=autorizacion.canAccess("CONTABILIDAD","asientos.ver");
        verPdf.setVisible(TenantContext.principalActual().permisos().contains("libro_diario.pdf"));
        ejecutar("No fue posible cargar el Libro Diario.",()->{
            var inicial=servicio.periodoInicial();
            desde.setValue(inicial.desde());
            hasta.setValue(inicial.hasta());
            cargar();
        });
    }

    private Filtro filtro() { return new Filtro(desde.getValue(),hasta.getValue(),tipo.getValue(),origen.getValue(),buscar.getValue()); }

    private void cargar() {
        boolean valido=!desde.isEmpty()&&!hasta.isEmpty()&&!hasta.getValue().isBefore(desde.getValue());
        desde.setInvalid(desde.isEmpty());
        desde.setErrorMessage("Indica la fecha inicial.");
        hasta.setInvalid(!valido);
        hasta.setErrorMessage("Indica una fecha final igual o posterior a la inicial.");
        verPdf.setEnabled(valido);
        listado.removeAll();
        totales.removeAll();
        if(!valido) {
            pagina.setTotal(0);
            return;
        }
        ejecutar("No fue posible consultar el Libro Diario.",()->{
            var p=pagina.currentRequest();
            var r=servicio.consultar(filtro(),p.page(),p.size());
            if(r.asientos().isEmpty()) listado.add(new Span("No se encontraron asientos contabilizados para estos filtros."));
            else {
                var encabezado=new Div(new Span("Fecha"),new Span("Asiento"),new Span("Cuenta / Concepto"),
                    new Span("Débito"),new Span("Crédito"),new Span("Acc."));
                encabezado.addClassNames("cc-daily-journal-row","cc-daily-journal-columns");
                listado.add(encabezado);
                r.asientos().forEach(a->listado.add(bloque(a)));
            }
            totales.add(new H3("TOTALES DEL PERÍODO"));
            if(r.totales().isEmpty()) totales.add(totales("",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
            else for(var t:r.totales())
                totales.add(totales(t.moneda(),t.total().debito(),t.total().credito(),t.total().diferencia()));
            pagina.setTotal(r.totalAsientos());
        });
    }

    private Div bloque(Asiento a) {
        var datos=new StringJoiner(" · ").add(a.tipo().etiqueta()).add(a.origen().etiqueta()).add(a.estado().etiqueta()).add(a.moneda());
        if(a.referencia()!=null) datos.add(a.referencia());
        if(a.documento()!=null&&!Objects.equals(a.referencia(),a.documento())) datos.add(a.documento());
        var metadata=new Span(datos.toString());
        metadata.addClassName("cc-detail-label");
        var concepto=new Div(new Span(a.concepto()),metadata);
        concepto.addClassName("cc-daily-journal-concept");
        Component accion=puedeVerAsientos
            ?new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver asiento",e->
                UI.getCurrent().navigate("asientos",QueryParameters.simple(Map.of("id",a.id().toString()))))
            :new Span();
        var encabezado=new Div(new Span(FECHA.format(a.fecha())),new Span(a.numero()),concepto,new Span(),new Span(),accion);
        encabezado.addClassNames("cc-daily-journal-row","cc-daily-journal-heading");
        var bloque=new Div(encabezado);
        bloque.addClassName("cc-daily-journal-entry");
        bloque.getElement().setAttribute("role","group");
        bloque.getElement().setAttribute("aria-label",a.numero());
        for(var l:a.movimientos()) {
            var cuenta=new Div(new Span(l.codigo()+" — "+l.cuenta()));
            if(l.centroCosto()!=null) {
                var centro=new Span("Centro: "+l.centroCosto());
                centro.addClassName("cc-detail-label");
                cuenta.add(centro);
            }
            if(l.descripcion()!=null) {
                var desc=new Span(l.descripcion());
                desc.addClassName("cc-detail-label");
                cuenta.add(desc);
            }
            cuenta.addClassName("cc-daily-journal-concept");
            var fila=new Div(new Span(),new Span(),cuenta,monto("Débito",l.debito()),monto("Crédito",l.credito()),new Span());
            fila.addClassNames("cc-daily-journal-row","cc-daily-journal-movement");
            bloque.add(fila);
        }
        var pie=new Div(new Span(),new Span(),new Span("Total "+a.numero()),
            monto("Débito",a.total().debito()),monto("Crédito",a.total().credito()),new Span());
        pie.addClassNames("cc-daily-journal-row","cc-daily-journal-movement","cc-daily-journal-subtotal");
        bloque.add(pie);
        return bloque;
    }

    private static Div totales(String moneda,BigDecimal debe,BigDecimal haber,BigDecimal diferencia) {
        var d=new Div(new Span(moneda),monto("Débitos",debe),monto("Créditos",haber),
            new AppStatusBadge("Diferencia "+importe(diferencia)+(diferencia.signum()==0?" ✓":" · Descuadrado"),
                diferencia.signum()==0?AppStatusBadge.Variant.SUCCESS:AppStatusBadge.Variant.DANGER));
        d.addClassName("cc-daily-journal-period");
        return d;
    }

    private static Div monto(String etiqueta,BigDecimal valor) {
        var label=new Span(etiqueta);
        label.addClassName("cc-daily-journal-amount-label");
        var d=new Div(label,new Span(importe(valor)));
        d.addClassName("cc-daily-journal-amount");
        return d;
    }

    private static DatePicker fecha(String etiqueta) {
        var d=new DatePicker(etiqueta);
        d.setLocale(Locale.forLanguageTag("es-DO"));
        d.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        d.setRequiredIndicatorVisible(true);
        d.setWidthFull();
        return d;
    }

    private static <T> ComboBox<T> combo(String etiqueta,SerializableFunction<T,String> nombre,List<T> valores) {
        var c=new ComboBox<T>(etiqueta);
        c.setItems(valores);
        c.setItemLabelGenerator(nombre::apply);
        c.setPlaceholder("Todos");
        c.setClearButtonVisible(true);
        c.setWidthFull();
        return c;
    }

    private static String importe(BigDecimal v) { return PosNumberFormatter.money(v); }

    private static void ejecutar(String mensaje,Runnable operacion) {
        try {operacion.run();} catch(RuntimeException ex) {UiErrorHandler.show(ex,mensaje);}
    }
}
