package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.DepreciacionPdfService;
import com.citacloud.springboot.contacloud.app.services.DepreciacionService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Route(value="activos-fijos/depreciaciones",layout=MainLayout.class)
@PageTitle("Depreciación | ContaCloud")
@PermitAll
public class DepreciacionesActivosView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES=DateTimeFormatter.ofPattern("MM/yyyy");
    private final DepreciacionService servicio;
    private final DepreciacionPdfService pdf;
    private final ModuleAuthorization autorizacion;
    private final AppActionButton procesar;
    private final AppGrid<Fila> historial=new AppGrid<>(Fila.class);
    private final AppPagination paginacion=new AppPagination(p->cargar());
    private final DatePicker desde=fecha("Desde");
    private final DatePicker hasta=fecha("Hasta");
    private final Select<String> estado=new Select<>();
    private final Div indicadores=new Div();

    public DepreciacionesActivosView(DepreciacionService servicio,DepreciacionPdfService pdf,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.pdf=pdf;
        this.autorizacion=autorizacion;
        setPadding(false);
        setSpacing(false);
        addClassName("cc-page");
        procesar=new AppActionButton(ActionType.PROCESS,ButtonSize.MAIN,"Procesar depreciación",e->seleccionarPeriodo());
        procesar.setVisible(false);
        indicadores.addClassName("cc-cartera-indicadores");
        estado.setLabel("Estado");
        estado.setItems("Todos","Procesado");
        estado.setValue("Todos");
        var filtros=new HorizontalLayout(desde,hasta,estado);
        filtros.setWidthFull();
        filtros.addClassName("cc-filter-bar");
        desde.addValueChangeListener(e->reiniciar());
        hasta.addValueChangeListener(e->reiniciar());
        // El historial contiene exclusivamente procesos consolidados.
        estado.addValueChangeListener(e->reiniciar());
        columna(historial,"Período",f->MES.format(f.periodo())).setFlexGrow(2);
        columna(historial,"Procesado",f->FECHA.format(f.fecha())).setFlexGrow(3);
        columna(historial,"Activos",Fila::activos).setFlexGrow(2).setTextAlign(ColumnTextAlign.CENTER);
        columna(historial,"Moneda",Fila::moneda).setFlexGrow(2);
        columna(historial,"Dep. período",f->PosNumberFormatter.money(f.importe()))
            .setFlexGrow(3).setTextAlign(ColumnTextAlign.END);
        historial.addComponentColumn(f->procesado()).setHeader("Estado").setAutoWidth(true).setFlexGrow(3);
        historial.addComponentColumn(f->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver depreciación",e->ver(f.id())))
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        historial.setEmptyStateText("No se encontraron depreciaciones procesadas.");
        add(new AppPageHeader("DEPRECIACIÓN","Calcula y consulta la depreciación mensual de activos fijos.",procesar),
            indicadores,filtros,historial,paginacion);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (!autorizacion.canAccess("ACTIVOS_FIJOS",DepreciacionService.VER)) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        var p=TenantContext.principalActual();
        procesar.setVisible(p.accesoTodasSucursales()&&p.permisos().contains(DepreciacionService.PROCESAR));
        actualizar();
    }

    private void actualizar() {
        try {
            var i=servicio.indicadores();
            indicadores.removeAll();
            indicadores.add(indicador("ÚLTIMO PERÍODO",i.ultimo()==null?"—":MES.format(i.ultimo())),
                indicador("ACTIVOS DEPRECIABLES",Long.toString(i.activos())),
                indicador("DEPRECIACIÓN ACUMULADA",i.acumulados().isEmpty()?"—":i.acumulados().stream()
                    .map(s->importe(s.moneda(),s.importe())).collect(Collectors.joining(" · "))));
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible cargar los indicadores.");
        }
        reiniciar();
    }

    private static AppDetailSection indicador(String etiqueta,String valor) {
        var seccion=new AppDetailSection(etiqueta).field("",valor);
        seccion.addClassName("cc-detail-full");
        return seccion;
    }

    private void reiniciar() { paginacion.reset(); cargar(); }

    private void cargar() {
        try {
            var p=paginacion.currentRequest();
            var pagina=servicio.historial(desde.getValue(),hasta.getValue(),p.page(),p.size());
            historial.setItems(pagina.getContent());
            paginacion.setTotal(pagina.getTotalElements());
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible consultar el historial de depreciación.");
        }
    }

    private void seleccionarPeriodo() {
        try {
            var d=new Dialog();
            d.setHeaderTitle("PROCESAR DEPRECIACIÓN");
            var dia=fecha("Mes del período");
            dia.setRequiredIndicatorVisible(true);
            dia.setValue(servicio.siguiente().atDay(1));
            var proceso=new Paragraph();
            Runnable mostrar=()->proceso.setText(dia.isEmpty()?"":"Fecha de proceso: "+FECHA.format(YearMonth.from(dia.getValue()).atEndOfMonth()));
            dia.addValueChangeListener(e->mostrar.run());
            mostrar.run();
            d.add(dia,proceso);
            d.getFooter().add(new AppActionButton(ActionType.CALCULATE,ButtonSize.MAIN,"Calcular depreciación",e->{
                try {
                    var previa=servicio.calcular(dia.isEmpty()?null:YearMonth.from(dia.getValue()));
                    d.close();
                    vistaPrevia(previa);
                } catch (RuntimeException ex) {
                    UiErrorHandler.show(ex,"No fue posible calcular la depreciación.");
                }
            }),cancelar(d));
            d.open();
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible abrir el proceso de depreciación.");
        }
    }

    private void vistaPrevia(VistaPrevia previa) {
        var d=dialogo("PROCESAR DEPRECIACIÓN — "+MES.format(previa.periodo()));
        d.add(new Paragraph("Fecha de proceso: "+FECHA.format(previa.fecha())));
        for (var incidencia:previa.incidencias()) d.add(new Paragraph(incidencia));
        if (previa.lineas().isEmpty()) d.add(new Paragraph("No existen activos elegibles para depreciar en este período."));
        else {
            d.add(new Paragraph("Activos incluidos: "+previa.lineas().size()),resumen(previa.totales()),lineas(previa.lineas(),true));
            var confirmar=new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Procesar depreciación",e->{
                e.getSource().setEnabled(false);
                try {
                    servicio.procesar(previa.periodo(),previa.revision(),previa.solicitud());
                    d.close();
                    actualizar();
                    Notification.show("Depreciación procesada correctamente.");
                } catch (RuntimeException ex) {
                    e.getSource().setEnabled(true);
                    UiErrorHandler.show(ex,"No fue posible procesar la depreciación.");
                }
            });
            d.getFooter().add(confirmar);
        }
        d.getFooter().add(cancelar(d));
        d.open();
    }

    private void ver(UUID id) {
        try {
            var detalle=servicio.detalle(id);
            var p=detalle.proceso();
            var d=dialogo("DEPRECIACIÓN — "+MES.format(p.periodo()));
            var informacion=new AppDetailSection("INFORMACIÓN GENERAL")
                .field("Período",MES.format(p.periodo())).field("Fecha de proceso",FECHA.format(p.fecha()))
                .badge("Estado",procesado()).field("Procesado por",p.nombreUsuario())
                .field("Activos incluidos",Integer.toString(detalle.lineas().size()))
                .field("Moneda",detalle.totales().stream().map(Total::moneda).collect(Collectors.joining(", ")));
            informacion.addClassName("cc-detail-three");
            d.add(informacion,resumen(detalle.totales()),new H3("DETALLE"),lineas(detalle.lineas(),false));
            if (TenantContext.principalActual().permisos().contains(DepreciacionService.PDF))
                d.getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Imprimir",e->{
                    try {
                        PdfEnPestana.abrir(pdf.generar(id));
                    } catch (RuntimeException ex) {
                        UiErrorHandler.show(ex,"No fue posible abrir el PDF de depreciación.");
                    }
                }));
            d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));
            d.open();
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible consultar la depreciación.");
        }
    }

    private static Div resumen(List<Total> totales) {
        var contenido=new Div();
        for (var t:totales) {
            var seccion=new AppDetailSection("RESUMEN · "+t.moneda())
                .field("Costo de activos",importe(t.moneda(),t.costo())).field("Depreciación anterior",importe(t.moneda(),t.anterior()))
                .field("Depreciación del período",importe(t.moneda(),t.importe()))
                .field("Depreciación acumulada",importe(t.moneda(),t.acumulada())).field("Valor en libros",importe(t.moneda(),t.libros()));
            seccion.addClassName("cc-detail-three");
            contenido.add(seccion);
        }
        return contenido;
    }

    private static AppGrid<Linea> lineas(List<Linea> lineas,boolean previa) {
        var grid=new AppGrid<>(Linea.class);
        columna(grid,"Código",Linea::codigo);
        columna(grid,"Activo",Linea::nombre).setFlexGrow(1);
        columnaMonetaria(grid,previa?"Base depreciable":"Costo",previa?Linea::base:Linea::costo);
        columnaMonetaria(grid,"Dep. anterior",Linea::anterior);
        columnaMonetaria(grid,"Dep. período",Linea::importe);
        if (!previa) columnaMonetaria(grid,"Dep. acumulada",Linea::acumulada);
        columnaMonetaria(grid,"Valor en libros",Linea::libros);
        grid.setItems(lineas);
        return grid;
    }

    private static <T> com.vaadin.flow.component.grid.Grid.Column<T> columna(AppGrid<T> grid,String titulo,Function<T,?> valor) {
        return grid.addColumn(valor::apply).setHeader(titulo).setAutoWidth(true).setFlexGrow(0);
    }

    private static void columnaMonetaria(AppGrid<Linea> grid,String titulo,Function<Linea,BigDecimal> valor) {
        columna(grid,titulo,l->importe(l.moneda(),valor.apply(l))).setTextAlign(ColumnTextAlign.END);
    }

    private static AppStatusBadge procesado() { return new AppStatusBadge("Procesado",AppStatusBadge.Variant.SUCCESS); }
    private static AppActionButton cancelar(Dialog d) {
        return new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->d.close());
    }
    private static Dialog dialogo(String titulo) {
        var d=new Dialog();
        d.setHeaderTitle(titulo);
        d.setWidth("min(1140px, 96vw)");
        d.setMaxHeight("92vh");
        return d;
    }
    private static DatePicker fecha(String etiqueta) {
        var d=new DatePicker(etiqueta);
        d.setLocale(Locale.forLanguageTag("es-DO"));
        d.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        d.setClearButtonVisible(true);
        return d;
    }
    private static String importe(String moneda,BigDecimal valor) { return moneda+" "+PosNumberFormatter.money(valor); }
}
