package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.*;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.services.BalanzaService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

@Route(value="balanza",layout=MainLayout.class)
@PageTitle("Balanza de Comprobación | ContaCloud")
@PermitAll
public class BalanzaView extends VerticalLayout implements BeforeEnterObserver {
    private final BalanzaService servicio;
    private final ModuleAuthorization autorizacion;
    private final DatePicker desde=fecha("Desde");
    private final DatePicker hasta=fecha("Hasta");
    private final Select<Integer> nivel=new Select<>();
    private final Select<Mostrar> mostrar=new Select<>();
    private final TextField buscar=new TextField("Buscar");
    private final AppActionButton verPdf=AppActionButton.verPdfEnlace();
    private final Grid<Cuenta> grid=new Grid<>();
    private final Span estado=new Span();
    private final Span contexto=new Span();
    private final Div totales=new Div();
    private final AppPagination pagina=new AppPagination(p->cargar());
    private final Grid.Column<Cuenta> accion;

    public BalanzaView(BalanzaService servicio,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.autorizacion=autorizacion;
        addClassNames("cc-page","cc-trial-balance");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        nivel.setLabel("Nivel");
        nivel.setItemLabelGenerator(n->n==0?"Todos":"Nivel "+n);
        mostrar.setLabel("Mostrar");
        mostrar.setItems(Mostrar.values());
        mostrar.setItemLabelGenerator(Mostrar::etiqueta);
        mostrar.setValue(Mostrar.MOVIMIENTOS_O_SALDO);
        buscar.setPlaceholder("Código o nombre de cuenta");
        buscar.setMaxLength(150);
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        var filtros=new Div(desde,hasta,nivel,mostrar,buscar);
        filtros.addClassName("cc-trial-balance-filters");
        for(var campo:List.<HasValue<?,?>>of(desde,hasta,nivel,mostrar,buscar)) {
            campo.addValueChangeListener(e->{
                if(e.isFromClient()) {
                    pagina.reset();
                    cargar();
                }
            });
        }
        grid.setWidthFull();
        grid.setAllRowsVisible(true);
        grid.addClassName("cc-trial-balance-grid");
        grid.getElement().setAttribute("aria-label","Cuentas de la Balanza de Comprobación");
        grid.addColumn(Cuenta::codigo).setHeader("Código").setWidth("112px").setFlexGrow(0);
        grid.addComponentColumn(BalanzaView::nombreCuenta).setHeader("Cuenta").setWidth("230px").setFlexGrow(3);
        var aperturaDebito=importe(0,"Débito");
        var aperturaCredito=importe(1,"Crédito");
        var movimientosDebito=importe(2,"Débito");
        var movimientosCredito=importe(3,"Crédito");
        var cierreDebito=importe(4,"Débito");
        var cierreCredito=importe(5,"Crédito");
        accion=grid.addComponentColumn(c->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,
            "Ver Libro Mayor",e->abrirMayor(c)))
            .setHeader("Acc.").setWidth("60px").setFlexGrow(0).setFrozenToEnd(true)
            .setPartNameGenerator(c->"trial-balance-action").setTextAlign(ColumnTextAlign.CENTER);
        var grupos=grid.prependHeaderRow();
        grupos.join(aperturaDebito,aperturaCredito).setText("Saldo inicial");
        grupos.join(movimientosDebito,movimientosCredito).setText("Movimientos del período");
        grupos.join(cierreDebito,cierreCredito).setText("Saldo final");

        estado.getElement().setAttribute("role","status");
        estado.addClassNames("cc-card","cc-general-ledger-card");
        contexto.addClassName("cc-detail-label");
        totales.addClassNames("cc-card","cc-general-ledger-card");
        add(new AppPageHeader("BALANZA DE COMPROBACIÓN",null,verPdf),filtros,estado,contexto,grid,totales,pagina);
        ocultarResultados();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if(!autorizacion.canAccess("CONTABILIDAD","balanza.ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        verPdf.setVisible(autorizacion.canAccess("CONTABILIDAD","balanza.pdf"));
        accion.setVisible(autorizacion.canAccess("CONTABILIDAD","libro_mayor.ver"));
        try {
            var inicial=servicio.inicial();
            desde.setValue(inicial.mes().atDay(1));
            hasta.setValue(inicial.mes().atEndOfMonth());
            nivel.setItems(IntStream.rangeClosed(0,inicial.niveles()).boxed().toList());
            nivel.setValue(0);
            cargar();
        } catch(RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible cargar la Balanza de Comprobación.");
        }
    }

    private void cargar() {
        boolean valido=!desde.isEmpty()&&!hasta.isEmpty()&&!hasta.getValue().isBefore(desde.getValue());
        desde.setInvalid(desde.isEmpty());
        hasta.setInvalid(!valido);
        verPdf.actualizarEnlacePdf(null,Map.of());
        ocultarResultados();
        if(!valido) {
            estado.setText("Indica un rango de fechas válido.");
            estado.setVisible(true);
            return;
        }
        try {
            var p=pagina.currentRequest();
            var r=servicio.consultar(filtro(),p.page(),p.size());
            presentar(r);
            pagina.setTotal(r.total());
            verPdf.actualizarEnlacePdf("/api/accounting/trial-balance/pdf",parametrosPdf(r.filtro()));
        } catch(RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible consultar la Balanza de Comprobación.");
        }
    }

    private void presentar(Resultado r) {
        grid.setItems(r.cuentas());
        grid.setVisible(!r.cuentas().isEmpty());
        estado.setText("No existen cuentas para los filtros seleccionados.");
        estado.setVisible(r.total()==0);
        if(r.filtro().level()==null&&!r.cuentas().isEmpty()&&r.cuentas().getFirst().padre()!=null) {
            contexto.setText("Cuenta superior: "+r.cuentas().getFirst().padre());
            contexto.setVisible(true);
        }
        totales.removeAll();
        totales.add(new H3("TOTALES DE LA CONSULTA"));
        r.totales().filtrados().forEach(t->totales.add(bloqueTotales(t,false)));
        totales.add(new H3("COMPROBACIÓN GENERAL DEL PERÍODO"));
        r.totales().generales().forEach(t->totales.add(bloqueTotales(t,true)));
        totales.setVisible(true);
        pagina.setVisible(true);
    }

    private static Div bloqueTotales(Importes t,boolean comprobar) {
        var bloque=new Div();
        if(!t.moneda().isBlank()) bloque.add(new Span(t.moneda()));
        var grupos=new Div(par("Saldo inicial",t.inicialDebito(),t.inicialCredito(),comprobar),
            par("Movimientos del período",t.debito(),t.credito(),comprobar),
            par("Saldo final",t.finalDebito(),t.finalCredito(),comprobar));
        grupos.addClassName("cc-trial-balance-totals");
        bloque.add(grupos);
        return bloque;
    }

    private static Div par(String titulo,BigDecimal debe,BigDecimal haber,boolean comprobar) {
        var grupo=new Div(new Span(titulo),new Span("Débito "+PosNumberFormatter.money(debe)),
            new Span("Crédito "+PosNumberFormatter.money(haber)));
        grupo.addClassName("cc-general-ledger-cell");
        if(comprobar) {
            var diferencia=debe.subtract(haber);
            boolean cuadra=diferencia.signum()==0;
            grupo.add(new AppStatusBadge("Diferencia "+PosNumberFormatter.money(diferencia)+(cuadra?" · Cuadra":" · Descuadrado"),
                cuadra?AppStatusBadge.Variant.SUCCESS:AppStatusBadge.Variant.DANGER));
        }
        return grupo;
    }

    private Grid.Column<Cuenta> importe(int indice,String etiqueta) {
        return grid.addComponentColumn(c->{
            var celda=new Div();
            celda.addClassName("cc-trial-balance-amount");
            if(c.importes().isEmpty()) celda.add(new Span("0.00"));
            for(var t:c.importes()) {
                String prefijo=c.importes().size()>1?t.moneda()+" ":"";
                celda.add(new Span(prefijo+PosNumberFormatter.money(t.columnas().get(indice))));
            }
            return celda;
        }).setHeader(etiqueta).setWidth("116px").setTextAlign(ColumnTextAlign.END);
    }

    private static Div nombreCuenta(Cuenta c) {
        var tipo=new Span(c.tipo().etiqueta()+(c.activa()?"":" · Inactiva"));
        tipo.addClassName("cc-detail-label");
        var nombre=new Span(c.nombre());
        if(c.tipo()==Tipo.AGRUPADORA) nombre.getStyle().set("font-weight","700");
        var d=new Div(nombre,tipo);
        d.addClassName("cc-general-ledger-cell");
        d.getStyle().set("padding-inline-start",Math.min(c.nivel()-1,12)*8+"px");
        return d;
    }

    private void abrirMayor(Cuenta c) {
        UI.getCurrent().navigate("libro-mayor",QueryParameters.simple(Map.of("accountId",c.id().toString(),
            "fromDate",desde.getValue().toString(),"toDate",hasta.getValue().toString())));
    }

    private void ocultarResultados() {
        estado.setVisible(false);
        contexto.setVisible(false);
        grid.setVisible(false);
        totales.setVisible(false);
        pagina.setVisible(false);
    }

    private Filtro filtro() {
        Integer valor=nivel.getValue();
        return new Filtro(desde.getValue(),hasta.getValue(),valor==null||valor==0?null:valor,mostrar.getValue(),buscar.getValue());
    }

    private static Map<String,Object> parametrosPdf(Filtro filtro) {
        var p=new LinkedHashMap<String,Object>();
        p.put("fromDate",filtro.fromDate());
        p.put("toDate",filtro.toDate());
        p.put("display",filtro.display().name());
        if(filtro.level()!=null) p.put("level",filtro.level());
        if(!filtro.search().isBlank()) p.put("search",filtro.search());
        return p;
    }

    private static DatePicker fecha(String etiqueta) {
        var campo=new DatePicker(etiqueta);
        campo.setLocale(Locale.forLanguageTag("es-DO"));
        campo.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        campo.setRequiredIndicatorVisible(true);
        campo.setErrorMessage(etiqueta.equals("Desde")?"Indica la fecha inicial.":"Indica una fecha final igual o posterior a la inicial.");
        return campo;
    }
}
