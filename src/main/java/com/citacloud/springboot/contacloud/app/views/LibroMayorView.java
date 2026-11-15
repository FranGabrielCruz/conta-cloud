package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.services.LibroMayorService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinService;
import jakarta.annotation.security.PermitAll;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Route(value="libro-mayor",layout=MainLayout.class)
@PageTitle("Libro Mayor | ContaCloud")
@PermitAll
public class LibroMayorView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final LibroMayorService servicio;
    private final ModuleAuthorization autorizacion;
    private final ComboBox<Cuenta> cuenta=new ComboBox<>("Cuenta");
    private final DatePicker desde=fecha("Desde");
    private final DatePicker hasta=fecha("Hasta");
    private final ComboBox<Tipo> tipo=combo("Tipo",Tipo::etiqueta,List.of(Tipo.values()));
    private final ComboBox<Origen> origen=combo("Origen",Origen::etiqueta,List.of(Origen.values()));
    private final TextField buscar=new TextField("Buscar");
    private final Div encabezado=new Div();
    private final Div resumen=new Div();
    private final Span estado=new Span();
    private final Grid<Movimiento> grid=new Grid<>();
    private final AppPagination pagina=new AppPagination(p->cargar());
    private final AppActionButton verPdf;
    private final Grid.Column<Movimiento> columnaCuenta;
    private final Grid.Column<Movimiento> columnaAsiento;

    public LibroMayorView(LibroMayorService servicio,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.autorizacion=autorizacion;
        addClassNames("cc-page","cc-general-ledger");
        setPadding(false);
        setSpacing(false);
        setWidthFull();

        verPdf=AppActionButton.verPdf(null);
        verPdf.getElement().executeJs("""
            this.addEventListener('click', () => {
                const url = this.getAttribute('data-pdf-url');
                if (url && !this.disabled) window.open(url, '_blank', 'noopener');
            });
            """);
        verPdf.setEnabled(false);
        cuenta.setRequiredIndicatorVisible(true);
        cuenta.setItemLabelGenerator(c->c.etiqueta()+(c.activo()?"":" (Inactiva)"));
        cuenta.setPlaceholder("Busca por código o nombre");
        cuenta.setClearButtonVisible(true);
        buscar.setMaxLength(150);
        buscar.setPlaceholder("Concepto, asiento, referencia o documento");
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        var filtros=new Div(cuenta,desde,hasta,tipo,origen,buscar);
        filtros.addClassName("cc-general-ledger-filters");
        for(var campo:List.<HasValue<?,?>>of(cuenta,desde,hasta,tipo,origen,buscar)) {
            campo.addValueChangeListener(e->{
                if(e.isFromClient()) {
                    pagina.reset();
                    cargar();
                }
            });
        }

        grid.setAllRowsVisible(true);
        grid.setWidthFull();
        grid.getElement().setAttribute("aria-label","Movimientos del Libro Mayor");
        grid.addColumn(m->FECHA.format(m.fecha())).setHeader("Fecha").setWidth("104px").setFlexGrow(0);
        columnaCuenta=grid.addComponentColumn(m->celda(m.codigo(),m.cuenta())).setHeader("Cuenta").setWidth("175px");
        grid.addColumn(Movimiento::numero).setHeader("Asiento").setWidth("126px").setFlexGrow(0);
        grid.addComponentColumn(LibroMayorView::concepto).setHeader("Concepto / Referencia").setWidth("210px").setFlexGrow(3);
        importe(grid.addColumn(m->PosNumberFormatter.money(m.debito())),"Débito");
        importe(grid.addColumn(m->PosNumberFormatter.money(m.credito())),"Crédito");
        importe(grid.addColumn(m->m.moneda()+" "+saldo(m.saldo())),"Saldo");
        columnaAsiento=grid.addComponentColumn(m->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver asiento",
            e->UI.getCurrent().navigate("asientos",QueryParameters.simple(Map.of("id",m.asientoId().toString())))))
            .setHeader("Acc.").setWidth("70px").setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        encabezado.addClassNames("cc-card","cc-general-ledger-card");
        resumen.addClassNames("cc-card","cc-general-ledger-card");
        estado.addClassNames("cc-card","cc-general-ledger-card");
        estado.getElement().setAttribute("role","status");
        add(new AppPageHeader("LIBRO MAYOR",null,verPdf),filtros,encabezado,estado,grid,resumen,pagina);
        mostrarEstado("Seleccione una cuenta para consultar su Libro Mayor.");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if(!autorizacion.canAccess("CONTABILIDAD","libro_mayor.ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        verPdf.setVisible(autorizacion.canAccess("CONTABILIDAD","libro_mayor.pdf"));
        columnaAsiento.setVisible(autorizacion.canAccess("CONTABILIDAD","asientos.ver"));
        ejecutar("No fue posible cargar el Libro Mayor.",()->{
            var mes=servicio.mesInicial();
            desde.setValue(mes.atDay(1));
            hasta.setValue(mes.atEndOfMonth());
            cuenta.setItems(servicio.cuentas());
        });
    }

    private void cargar() {
        boolean fechas=!desde.isEmpty()&&!hasta.isEmpty()&&!hasta.getValue().isBefore(desde.getValue());
        desde.setInvalid(desde.isEmpty());
        hasta.setInvalid(!fechas);
        verPdf.setEnabled(false);
        verPdf.getElement().removeAttribute("data-pdf-url");
        if(cuenta.isEmpty()||!fechas) {
            mostrarEstado(cuenta.isEmpty()?"Seleccione una cuenta para consultar su Libro Mayor.":"Indica un rango de fechas válido.");
            return;
        }
        encabezado.setVisible(false);
        resumen.setVisible(false);
        grid.setVisible(false);
        pagina.setVisible(false);
        estado.setVisible(false);
        ejecutar("No fue posible consultar el Libro Mayor.",()->{
            var p=pagina.currentRequest();
            var resultado=servicio.consultar(filtro(),p.page(),p.size());
            presentar(resultado);
            pagina.setTotal(resultado.total());
            verPdf.getElement().setAttribute("data-pdf-url",urlPdf(filtro()));
            verPdf.setEnabled(true);
        });
    }

    private void presentar(Resultado r) {
        var c=r.cuenta();
        String periodo=FECHA.format(r.desde())+" al "+FECHA.format(r.hasta());
        encabezado.removeAll();
        encabezado.add(new H3(c.etiqueta()),new Span(c.tipo().etiqueta()+" · Naturaleza: "+c.naturaleza().etiqueta()
            +" · Estado: "+(c.activo()?"Activa":"Inactiva")+" · Período: "+periodo));
        if(c.agrupadora()) encabezado.add(new Div(new Span("Consulta consolidada de cuentas descendientes.")));
        var inicial=new StringJoiner(" · ","Saldo inicial: ","");
        r.resumen().forEach(t->inicial.add((t.moneda()+" "+saldo(t.inicial())).strip()));
        encabezado.add(new Div(new Span(inicial.toString())));
        encabezado.setVisible(true);

        columnaCuenta.setVisible(c.agrupadora());
        grid.setItems(r.movimientos());
        grid.setVisible(!r.movimientos().isEmpty());
        estado.setText("No existen movimientos para los filtros seleccionados.");
        estado.setVisible(r.total()==0);

        resumen.removeAll();
        resumen.add(new H3("RESUMEN DEL PERÍODO"));
        for(var t:r.resumen()) {
            if(!t.moneda().isEmpty()) resumen.add(new Span(t.moneda()));
            var metricas=new Div(celda("Saldo inicial",saldo(t.inicial())),
                celda("Débitos",PosNumberFormatter.money(t.debitos())),celda("Créditos",PosNumberFormatter.money(t.creditos())),
                celda("Variación",saldo(t.variacion())),celda("Saldo final",saldo(t.cierre())));
            metricas.addClassName("cc-general-ledger-metrics");
            resumen.add(metricas);
        }
        if(!tipo.isEmpty()||!origen.isEmpty()||!buscar.getValue().isBlank()) {
            resumen.add(new Span("Débitos, créditos y variación corresponden a los filtros. Los saldos reflejan todo el período."));
        }
        resumen.setVisible(true);
        pagina.setVisible(true);
    }

    private void mostrarEstado(String mensaje) {
        estado.setText(mensaje);
        estado.setVisible(true);
        encabezado.setVisible(false);
        resumen.setVisible(false);
        grid.setVisible(false);
        pagina.setVisible(false);
    }

    private Filtro filtro() {
        return new Filtro(cuenta.getValue().id(),desde.getValue(),hasta.getValue(),tipo.getValue(),origen.getValue(),buscar.getValue());
    }

    private static String urlPdf(Filtro f) {
        String contexto=VaadinService.getCurrentRequest().getContextPath();
        var query=new StringJoiner("&",contexto+"/api/accounting/general-ledger/pdf?","");
        query.add("accountId="+f.accountId()).add("fromDate="+f.fromDate()).add("toDate="+f.toDate());
        if(f.type()!=null) query.add("type="+f.type().name());
        if(f.origin()!=null) query.add("origin="+f.origin().name());
        if(!f.search().isBlank()) query.add("search="+URLEncoder.encode(f.search(),StandardCharsets.UTF_8));
        return query.toString();
    }

    private static Div concepto(Movimiento m) {
        var meta=new StringJoiner(" · ").add(m.tipo().etiqueta()).add(m.origen().etiqueta()).add(m.estado().etiqueta());
        if(m.referencia()!=null) meta.add(m.referencia());
        if(m.documento()!=null&&!Objects.equals(m.referencia(),m.documento())) meta.add(m.documento());
        var d=celda(m.concepto(),meta.toString());
        if(m.descripcion()!=null&&!Objects.equals(m.descripcion(),m.concepto())) d.add(new Span(m.descripcion()));
        return d;
    }

    private static Div celda(String titulo,String detalle) {
        var texto=new Span(detalle);
        texto.addClassName("cc-detail-label");
        var d=new Div(new Span(titulo),texto);
        d.addClassName("cc-general-ledger-cell");
        return d;
    }

    private static void importe(Grid.Column<Movimiento> columna,String etiqueta) {
        columna.setHeader(etiqueta).setWidth("152px").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
    }

    private static String saldo(Saldo s) {
        return PosNumberFormatter.money(s.importe())+(s.lado()==Lado.CERO?"":" "+s.lado());
    }

    private static DatePicker fecha(String etiqueta) {
        var campo=new DatePicker(etiqueta);
        campo.setLocale(Locale.forLanguageTag("es-DO"));
        campo.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        campo.setRequiredIndicatorVisible(true);
        campo.setErrorMessage(etiqueta.equals("Desde")?"Indica la fecha inicial.":"Indica una fecha final igual o posterior a la inicial.");
        return campo;
    }

    private static <T> ComboBox<T> combo(String nombre, SerializableFunction<T,String> etiqueta,List<T> valores) {
        var c=new ComboBox<T>(nombre);
        c.setItems(valores);
        c.setItemLabelGenerator(etiqueta::apply);
        c.setPlaceholder("Todos");
        c.setClearButtonVisible(true);
        return c;
    }

    private static void ejecutar(String mensaje,Runnable operacion) {
        try {
            operacion.run();
        } catch(RuntimeException ex) {
            UiErrorHandler.show(ex,mensaje);
        }
    }
}
