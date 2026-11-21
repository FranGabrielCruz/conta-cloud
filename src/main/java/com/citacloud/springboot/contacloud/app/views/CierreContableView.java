package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.CierreContableService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Route(value="cierres-contables",layout=MainLayout.class)
@PageTitle("Cierre contable | ContaCloud")
@PermitAll
public class CierreContableView extends VerticalLayout implements BeforeEnterObserver,AfterNavigationObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CierreContableService servicio;
    private final ModuleAuthorization autorizacion;
    private final ComboBox<Periodo> periodo=new ComboBox<>("Período");
    private final Select<Estado> estado=new Select<>();
    private final TextField buscar=new TextField("Buscar");
    private final Grid<Periodo> grid=new Grid<>();
    private final Span vacio=new Span();
    private final AppPagination pagina=new AppPagination(p->cargar());
    private UUID detalleInicial;

    public CierreContableView(CierreContableService servicio,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.autorizacion=autorizacion;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        addClassNames("cc-page","cc-accounting-close");
        periodo.setItemLabelGenerator(Periodo::nombre);
        periodo.setPlaceholder("Todos");
        periodo.setClearButtonVisible(true);
        periodo.setItems(q->servicio.opciones(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
        estado.setLabel("Estado");
        estado.setItems(Estado.values());
        estado.setEmptySelectionAllowed(true);
        estado.setEmptySelectionCaption("Todos");
        estado.setItemLabelGenerator(e->e==null?"Todos":etiqueta(e));
        buscar.setPlaceholder("Nombre, año o mes del período");
        buscar.setClearButtonVisible(true);
        buscar.setMaxLength(150);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        periodo.addValueChangeListener(e->{if(e.isFromClient()) reiniciar();});
        estado.addValueChangeListener(e->{if(e.isFromClient()) reiniciar();});
        buscar.addValueChangeListener(e->{if(e.isFromClient()) reiniciar();});
        var filtros=new Div(periodo,estado,buscar);
        filtros.addClassName("cc-accounting-close-filters");
        grid.setAllRowsVisible(true);
        grid.setWidthFull();
        grid.addClassName("cc-accounting-close-grid");
        grid.addColumn(Periodo::nombre).setHeader("Período").setFlexGrow(3);
        grid.addColumn(p->FECHA.format(p.desde())).setHeader("Desde").setAutoWidth(true);
        grid.addColumn(p->FECHA.format(p.hasta())).setHeader("Hasta").setAutoWidth(true);
        grid.addComponentColumn(p->badge(p.estado())).setHeader("Estado").setAutoWidth(true);
        grid.addColumn(p->p.cerradoEn()==null?"—":HORA.format(p.cerradoEn())).setHeader("Fecha cierre").setAutoWidth(true);
        grid.addComponentColumn(p->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver cierre",e->ver(p.id())))
            .setHeader("Acc.").setFlexGrow(0).setWidth("62px").setFrozenToEnd(true)
            .setPartNameGenerator(p->"accounting-close-action");
        vacio.getElement().setAttribute("role","status");
        add(new AppPageHeader("CIERRE CONTABLE",null),filtros,vacio,grid,pagina);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if(!puede("ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        event.getLocation().getQueryParameters().getSingleParameter("periodo").ifPresent(id->{
            try { detalleInicial=UUID.fromString(id); }
            catch(IllegalArgumentException ex) { Notification.show("El período del enlace no es válido."); }
        });
        cargar();
    }

    @Override public void afterNavigation(AfterNavigationEvent event) {
        if(detalleInicial==null) return;
        UUID id=detalleInicial;
        detalleInicial=null;
        ver(id);
    }

    private void cargar() {
        ejecutar("No fue posible consultar los períodos.",()->{
            var p=pagina.currentRequest();
            var r=servicio.listar(new Filtro(periodo.isEmpty()?null:periodo.getValue().id(),estado.getValue(),buscar.getValue()),p.page(),p.size());
            grid.setItems(r.periodos());
            grid.setVisible(!r.periodos().isEmpty());
            vacio.setVisible(r.total()==0);
            vacio.setText(periodo.isEmpty()&&estado.isEmpty()&&buscar.isEmpty()?"No existen períodos contables disponibles."
                :"No se encontraron períodos para los filtros seleccionados.");
            pagina.setVisible(r.total()>0);
            pagina.setTotal(r.total());
        });
    }

    private void reiniciar() { pagina.reset(); cargar(); }

    private void ver(UUID id) {
        ejecutar("No fue posible abrir el cierre contable.",()->{
            var detalle=servicio.detalle(id);
            var p=detalle.periodo();
            var d=dialogo("CIERRE CONTABLE — "+p.nombre());
            d.addClassName("cc-accounting-close-dialog");
            d.setWidth("min(1120px, 94vw)");
            var e=detalle.evaluacion();
            var datos=e!=null?e:detalle.historial().stream().findFirst().map(c->c.resumen().evaluacion()).orElse(null);
            d.add(detalleSuperior(p,datos,e!=null&&puedeConfigurar()
                ?()->configurar(p.id(),e.estadoCuentaResultado()==EstadoCuentaResultado.VALIDA?e.cuentaResultado():null,d):null));
            if(datos!=null&&datos.resultadoPeriodo()!=null) d.add(resultadoPeriodo(datos));
            if(e!=null) {
                if(puede("ejecutar")) {
                    var cerrar=boton(ActionType.ACCEPT,e.tipo()==Tipo.EJERCICIO?"Cerrar ejercicio":"Cerrar período",
                        ()->confirmarCierre(p,e,d));
                    cerrar.setEnabled(e.permiteCerrar());
                    if(!e.permiteCerrar()) cerrar.setTooltipText("Revisa las validaciones previas antes de cerrar.");
                    d.getFooter().add(cerrar);
                }
            }
            for(var cierre:detalle.historial()) d.add(historia(p,cierre));
            if(p.estado()==Estado.CERRADO&&detalle.historial().isEmpty()) {
                d.add(new AppDetailSection("CIERRE ANTERIOR").field("Fecha de cierre",p.cerradoEn()==null?"—":HORA.format(p.cerradoEn()))
                    .field("Historial","Cierre anterior al registro de cierres contables."));
            }
            if(detalle.reabrible()&&puede("reabrir")) {
                var vigente=detalle.historial().stream().filter(c->c.reapertura()==null).findFirst().orElse(null);
                d.getFooter().add(boton(ActionType.REOPEN_PERIOD,"Reabrir período",()->confirmarReapertura(p,vigente,d)));
            }
            d.getFooter().add(boton(ActionType.CLOSE,"Cerrar",d::close));
            d.open();
        });
    }

    static Div detalleSuperior(Periodo p,Evaluacion e,Runnable configurar) {
        var periodo=new AppDetailSection("PERÍODO").field("Período",p.nombre())
            .field("Desde",FECHA.format(p.desde())).field("Hasta",FECHA.format(p.hasta())).badge("Estado",badge(p.estado()));
        var superior=new Div(periodo);
        superior.addClassName("cc-accounting-close-detail");
        if(e==null) return superior;
        var t=e.resultadoEjercicio();
        var ejercicio=new AppDetailSection("CIERRE DE EJERCICIO")
            .field("Ejercicio",e.inicioFiscal()==null?Integer.toString(p.hasta().getYear()):ejercicio(e.inicioFiscal()))
            .field("Tipo de cierre",e.tipo().etiqueta())
            .field("Resultado del ejercicio",t==null?"No disponible":importe(e,t.neto())+" — "+t.tipo())
            .field("Cuenta de resultado",e.cuentaResultado()==null?"No configurada":e.cuentaResultado().etiqueta());
        if(e.estadoCuentaResultado()==EstadoCuentaResultado.INVALIDA)
            ejercicio.add(new Paragraph("⚠ Configuración inválida"));
        else if(e.cuentaResultado()==null&&e.tipo()==Tipo.EJERCICIO)
            ejercicio.add(new Paragraph("⚠ Configuración requerida"));
        if(configurar!=null)
            ejercicio.add(boton(ActionType.EDIT,"Configurar cuenta de resultado",configurar));
        var titulo=new H2("VALIDACIONES PREVIAS");
        titulo.addClassName("cc-section-heading");
        var lista=new UnorderedList();
        lista.addClassName("cc-accounting-close-checks");
        for(var v:e.validaciones()) {
            var icono=new Span(v.correcta()?"✓":"✕");
            icono.getElement().setAttribute("role","img");
            icono.getElement().setAttribute("aria-label",v.correcta()?"Correcta":"Bloqueante");
            icono.addClassName(v.correcta()?"cc-close-check-ok":"cc-close-check-error");
            String valor=v.valor()==null?(v.correcta()?v.detalle():"Pendiente"):v.valor();
            var texto=new Div(new Span(v.nombre()),new Span(valor));
            if(!v.correcta()&&!valor.equals(v.detalle())) texto.add(new Paragraph(v.detalle()));
            if(v.nombre().equals("Períodos anteriores")&&e.periodosAnteriores()!=null)
                texto.add(new Paragraph("Períodos registrados: "+e.periodosAnteriores().existentes()));
            var fila=new ListItem(icono,texto);
            lista.add(fila);
        }
        var validaciones=new Div(titulo,lista);
        validaciones.addClassNames("cc-card","cc-detail-section","cc-accounting-close-validation");
        superior.add(ejercicio,validaciones);
        return superior;
    }

    private static String ejercicio(java.time.LocalDate inicio) {
        int fin=inicio.plusYears(1).minusDays(1).getYear();
        return inicio.getYear()==fin?Integer.toString(fin):inicio.getYear()+" / "+fin;
    }

    static AppDetailSection resultadoPeriodo(Evaluacion e) {
        var t=e.resultadoPeriodo();
        var resultado=new AppDetailSection("RESULTADO DEL PERÍODO")
            .field("Ingresos",importe(e,t.ingresos())).field("Costos",importe(e,t.costos())).field("Gastos",importe(e,t.gastos()))
            .field("Resultado",importe(e,t.neto())+" — "+t.tipo());
        resultado.addClassName("cc-accounting-close-period-result");
        return resultado;
    }

    private Div historia(Periodo periodo,Cierre c) {
        var r=c.resumen();
        var zona=ZoneId.of(r.zonaHoraria());
        var bloque=new AppDetailSection(c.reapertura()==null?"CIERRE REGISTRADO":"CIERRE ANTERIOR REABIERTO")
            .field("Tipo",r.evaluacion().tipo().etiqueta()).field("Fecha de cierre",HORA.format(c.fecha().atZoneSameInstant(zona)))
            .field("Cerrado por",r.usuario()).field("Resultado",importe(r.evaluacion(),r.evaluacion().resultado().neto())
                +" · "+r.evaluacion().resultado().tipo())
            .field("Observación",r.observacion().isBlank()?"—":r.observacion());
        var acciones=new Div();
        acciones.addClassName("cc-accounting-close-history-actions");
        if(puede("pdf")) {
            var pdf=AppActionButton.verPdfEnlace();
            pdf.actualizarEnlacePdf("/api/accounting/closures/"+periodo.id()+"/pdf",Map.of("closeId",c.id()));
            acciones.add(pdf);
        }
        if(c.asientoId()!=null) {
            bloque.field("Asiento de cierre",c.asientoNumero());
            if(autorizacion.canAccess("CONTABILIDAD","asientos.ver"))
                acciones.add(boton(ActionType.VIEW,"Ver asiento de cierre",()->{
                    UI.getCurrent().navigate("asientos",QueryParameters.simple(Map.of("id",c.asientoId().toString())));
                }));
        }
        if(c.reapertura()!=null) {
            var a=c.reapertura();
            bloque.field("Reabierto por",a.usuario()).field("Fecha de reapertura",HORA.format(a.fecha().atZoneSameInstant(zona)))
                .field("Motivo",a.motivo());
            if(a.reversaNumero()!=null) bloque.field("Asiento de reversión",a.reversaNumero());
        }
        return new Div(bloque,acciones);
    }

    private void configurar(UUID id,Cuenta actual,Dialog origen) {
        var d=dialogo("CUENTA DE RESULTADO DEL EJERCICIO");
        var cuenta=new ComboBox<Cuenta>("Cuenta habilitada para resultado del ejercicio");
        cuenta.setWidthFull();
        cuenta.setItemLabelGenerator(Cuenta::etiqueta);
        cuenta.setItems(q->servicio.buscarResultado(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
        if(actual!=null) cuenta.setValue(actual);
        d.add(cuenta);
        d.getFooter().add(boton(ActionType.SAVE,"Guardar",()->ejecutar("No fue posible configurar la cuenta.",()->{
            servicio.configurarResultado(cuenta.isEmpty()?null:cuenta.getValue().id());
            d.close();
            origen.close();
            ver(id);
        })),boton(ActionType.CANCEL,"Cancelar",d::close));
        d.open();
    }

    private void confirmarCierre(Periodo p,Evaluacion e,Dialog origen) {
        var d=dialogo(e.tipo()==Tipo.EJERCICIO?"CONFIRMAR CIERRE DE EJERCICIO":"CONFIRMAR CIERRE DE PERÍODO");
        var nota=texto("Observación");
        d.add(new Span(e.tipo()==Tipo.EJERCICIO?"Se cerrarán las cuentas de resultados del ejercicio y se bloqueará el período."
            :"El período dejará de aceptar nuevas contabilizaciones."),nota);
        var clave=UUID.randomUUID();
        d.getFooter().add(boton(ActionType.ACCEPT,"Confirmar cierre",()->ejecutar("No fue posible cerrar el período.",()->{
            servicio.cerrar(p.id(),clave,nota.getValue());
            d.close();
            origen.close();
            reiniciar();
            Notification.show("Período cerrado correctamente.");
            ver(p.id());
        })),boton(ActionType.CANCEL,"Cancelar",d::close));
        d.open();
    }

    private void confirmarReapertura(Periodo p,Cierre cierre,Dialog origen) {
        var d=dialogo("REABRIR PERÍODO");
        var motivo=texto("Motivo");
        motivo.setRequiredIndicatorVisible(true);
        motivo.setErrorMessage("Indica el motivo de la reapertura.");
        d.add(new Span("El período volverá a aceptar contabilizaciones."),motivo);
        var clave=UUID.randomUUID();
        d.getFooter().add(boton(ActionType.REOPEN_PERIOD,"Reabrir período",()->ejecutar("No fue posible reabrir el período.",()->{
            if(motivo.getValue().isBlank()) {
                motivo.setInvalid(true);
                motivo.focus();
                return;
            }
            servicio.reabrir(p.id(),cierre==null?null:cierre.id(),clave,motivo.getValue());
            d.close();
            origen.close();
            reiniciar();
            Notification.show("Período reabierto correctamente.");
            ver(p.id());
        })),boton(ActionType.CANCEL,"Cancelar",d::close));
        d.open();
    }

    private static TextArea texto(String titulo) {
        var campo=new TextArea(titulo);
        campo.setWidthFull();
        campo.setMaxLength(1000);
        return campo;
    }

    private static Dialog dialogo(String titulo) {
        var d=new Dialog(titulo);
        d.setWidth("min(820px, 94vw)");
        d.setCloseOnOutsideClick(false);
        return d;
    }

    private static AppActionButton boton(ActionType tipo,String etiqueta,Runnable ejecutar) {
        return new AppActionButton(tipo,ButtonSize.MAIN,etiqueta,e->ejecutar.run());
    }

    private static String importe(Evaluacion e,java.math.BigDecimal monto) {
        return (e.moneda()+" "+PosNumberFormatter.money(monto)).strip();
    }

    private static String etiqueta(Estado e) {
        return switch(e) {case ABIERTO->"Abierto";case CERRADO->"Cerrado";case BLOQUEADO->"Bloqueado";};
    }

    private static AppStatusBadge badge(Estado e) {
        return new AppStatusBadge(etiqueta(e),e==Estado.ABIERTO?AppStatusBadge.Variant.SUCCESS:AppStatusBadge.Variant.NEUTRAL);
    }

    private boolean puede(String permiso) { return autorizacion.canAccess("CONTABILIDAD","cierres_contables."+permiso); }

    private static boolean puedeConfigurar() {
        var permisos=TenantContext.principalActual().permisos();
        return permisos.contains("configuracion_contable.editar")||permisos.contains("CONFIGURACION_CONTABLE_EDITAR");
    }

    private static void ejecutar(String mensaje,Runnable operacion) {
        try {
            operacion.run();
        } catch(RuntimeException ex) {
            UiErrorHandler.show(ex,mensaje);
        }
    }
}
