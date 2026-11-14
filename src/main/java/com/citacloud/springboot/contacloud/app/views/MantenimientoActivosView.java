package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.Moneda;
import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.Opcion;
import com.citacloud.springboot.contacloud.app.dto.MantenimientoDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.MantenimientoPdfService;
import com.citacloud.springboot.contacloud.app.services.MantenimientoService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Route(value="activos-fijos/mantenimiento",layout=MainLayout.class)
@PageTitle("Mantenimiento | ContaCloud")
@PermitAll
public class MantenimientoActivosView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final MantenimientoService servicio;
    private final MantenimientoPdfService pdf;
    private final ModuleAuthorization autorizacion;
    private final Div indicadores=new Div();
    private final AppGrid<Fila> tabla=new AppGrid<>(Fila.class);
    private final AppPagination pagina=new AppPagination(p->cargar());
    private final DatePicker desde=fecha("Desde");
    private final DatePicker hasta=fecha("Hasta");
    private final ComboBox<Tipo> tipo=combo("Tipo",Tipo::nombre,List.of());
    private final ComboBox<String> estado=combo("Estado",String::toString,
        List.of("Programado","En proceso","Completado","Cancelado","Vencido"));
    private final TextField buscar=texto("Buscar",150);
    private final AppActionButton nuevo;

    public MantenimientoActivosView(MantenimientoService servicio,MantenimientoPdfService pdf,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.pdf=pdf;
        this.autorizacion=autorizacion;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        nuevo=accion(ActionType.NEW,"Nuevo mantenimiento",()->editar(null));
        nuevo.setVisible(false);
        indicadores.addClassName("cc-cartera-indicadores");
        buscar.setPlaceholder("Código, activo o proveedor");
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setClearButtonVisible(true);
        for(var f:List.<com.vaadin.flow.component.HasValue<?,?>>of(desde,hasta,tipo,estado,buscar))
            f.addValueChangeListener(e->{if(e.isFromClient()) reiniciar();});
        tipo.setPlaceholder("Todos");
        estado.setPlaceholder("Todos");
        var filtros=new Div(desde,hasta,tipo,estado,buscar);
        filtros.addClassName("cc-maintenance-filters");
        buscar.addClassName("cc-maintenance-search");
        configurarTabla(tabla,false);
        tabla.addComponentColumn(f->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver mantenimiento",e->ver(f.id())))
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.CENTER);
        tabla.setEmptyStateText("No se encontraron mantenimientos.");
        add(new AppPageHeader("MANTENIMIENTO","Programa y consulta el mantenimiento de activos fijos.",nuevo),indicadores,filtros,tabla,pagina);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if(!autorizacion.canAccess("ACTIVOS_FIJOS",MantenimientoService.RECURSO+"ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        nuevo.setVisible(puede("crear"));
        ejecutar("No fue posible cargar mantenimiento.",()->{
            tipo.setItems(servicio.tipos());
            actualizar();
        });
    }

    private void actualizar() {
        var i=servicio.indicadores();
        indicadores.removeAll();
        indicadores.add(tarjeta("PROGRAMADOS",Long.toString(i.programados())),tarjeta("PRÓXIMOS",Long.toString(i.proximos())),
            tarjeta("VENCIDOS",Long.toString(i.vencidos())),tarjeta("COSTO DEL MES",i.costos().isEmpty()?"—":i.costos().stream()
                .map(s->importe(s.moneda(),s.costo())).collect(Collectors.joining(" · "))));
        reiniciar();
    }

    private void reiniciar() { pagina.reset(); cargar(); }

    private void cargar() {
        ejecutar("No fue posible consultar el historial.",()->{
            var p=pagina.currentRequest();
            var elegido=java.util.Arrays.stream(Estado.values()).filter(x->x.etiqueta().equals(estado.getValue())).findFirst().orElse(null);
            var resultados=servicio.listar(new Filtro(desde.getValue(),hasta.getValue(),tipo.isEmpty()?null:tipo.getValue().codigo(),
                elegido,"Vencido".equals(estado.getValue()),buscar.getValue()),p.page(),p.size());
            tabla.setItems(resultados.getContent());
            pagina.setTotal(resultados.getTotalElements());
        });
    }

    private void ver(UUID id) {
        ejecutar("No fue posible consultar el mantenimiento.",()->{
            var d=servicio.detalle(id);
            var dialogo=dialogo("MANTENIMIENTO — "+d.codigo());
            var e=d.datos();
            var s=d.snapshot();
            var moneda=s==null?d.moneda():s.moneda();
            var activo=s==null?d.activo().etiqueta():s.activoCodigo()+" — "+s.activoNombre();
            dialogo.add(seccion("INFORMACIÓN GENERAL").field("Código",d.codigo()).field("Activo",activo)
                    .badge("Estado",etiqueta(d.estado(),d.estado().pendiente()&&e.programada().isBefore(servicio.hoy())))
                    .field("Tipo",s==null?d.tipo():s.tipo()).field("Prioridad",e.prioridad().etiqueta()).field("Fecha programada",formato(e.programada())),
                seccion("ASIGNACIÓN").field("Responsable interno",s==null?d.responsable():s.responsable())
                    .field("Proveedor / Técnico",s==null?d.proveedor():s.proveedor()).field("Moneda",moneda),
                seccion("INFORMACIÓN ECONÓMICA").field("Costo estimado",importe(moneda,e.estimado()))
                    .field("Costo real",importe(moneda,d.real())).field("Variación",importe(moneda,d.variacion()))
                    .field("Referencia",e.referencia()),
                anchoCompleto("DETALLE DEL MANTENIMIENTO","Descripción",e.descripcion()),
                seccion("PROGRAMACIÓN").field("Próximo mantenimiento",formato(e.proxima())).field("Fecha realizada",formato(d.realizada()))
                    .field("Activo fuera de servicio",d.fueraServicio()?"Sí":"No"));
            if(d.trabajo()!=null) dialogo.add(anchoCompleto("TRABAJO REALIZADO","Trabajo realizado",d.trabajo()));
            if(e.observacion()!=null) dialogo.add(anchoCompleto("OBSERVACIÓN","Observación",e.observacion()));
            if(d.motivo()!=null) dialogo.add(anchoCompleto("CANCELACIÓN","Motivo",d.motivo()));
            if(d.estado()==Estado.PROGRAMADO) {
                if(puede("editar")) dialogo.getFooter().add(accion(ActionType.EDIT,"Editar mantenimiento",()->{
                    if(editar(d)) dialogo.close();
                }));
                if(puede("iniciar")) dialogo.getFooter().add(accion(ActionType.PROCESS,"Iniciar mantenimiento",()->iniciar(d,dialogo)));
            }
            if(d.estado()==Estado.EN_PROCESO && puede("completar"))
                dialogo.getFooter().add(accion(ActionType.ACCEPT,"Completar mantenimiento",()->completar(d,dialogo)));
            if(d.estado().pendiente() && puede("cancelar"))
                dialogo.getFooter().add(accion(ActionType.VOID,"Cancelar mantenimiento",()->cancelar(d,dialogo)));
            if(d.estado()==Estado.COMPLETADO && puede("imprimir"))
                dialogo.getFooter().add(accion(ActionType.PRINT,"Imprimir",()->ejecutar("No fue posible abrir el PDF.",()->PdfEnPestana.abrir(pdf.generar(id)))));
            dialogo.getFooter().add(accion(ActionType.CLOSE,"Cerrar",dialogo::close));
            dialogo.open();
        });
    }

    private boolean editar(Detalle previo) {
        try {
            var c=servicio.catalogos();
            var d=dialogo(previo==null?"NUEVO MANTENIMIENTO":"EDITAR "+previo.codigo());
            var activo=combo("Activo",Activo::etiqueta,List.of());
            if(previo==null)
                activo.setItems(q->servicio.buscarActivos(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
            else {
                activo.setItems(previo.activo());
                activo.setValue(previo.activo());
                activo.setReadOnly(true);
            }
            var tipo=combo("Tipo",Tipo::nombre,c.tipos());
            var prioridad=combo("Prioridad",Prioridad::etiqueta,List.of(Prioridad.values()));
            var programada=fecha("Fecha programada");
            var proxima=fecha("Próximo mantenimiento");
            var responsable=opcion("Responsable interno",c.responsables(),previo==null?null:previo.datos().responsable(),previo==null?null:previo.responsable());
            var proveedor=opcion("Proveedor / Técnico",c.proveedores(),previo==null?null:previo.datos().proveedor(),previo==null?null:previo.proveedor());
            var monedas=new ArrayList<>(c.monedas());
            if(previo!=null && monedas.stream().noneMatch(m->m.id().equals(previo.datos().moneda())))
                monedas.add(new Moneda(previo.datos().moneda(),previo.moneda(),false));
            var moneda=combo("Moneda",Moneda::codigo,monedas);
            var estimado=dinero("Costo estimado",previo==null?BigDecimal.ZERO:previo.datos().estimado());
            var referencia=texto("Referencia",150);
            var descripcion=area("Descripción",2000);
            var observacion=area("Observación",2000);
            for(var campo:List.<com.vaadin.flow.component.HasValue<?,?>>of(activo,tipo,programada,moneda,descripcion))
                campo.setRequiredIndicatorVisible(true);
            if(previo==null) {
                prioridad.setValue(Prioridad.MEDIA);
                programada.setValue(servicio.hoy());
                monedas.stream().filter(Moneda::base).findFirst().ifPresent(moneda::setValue);
            } else {
                var e=previo.datos();
                c.tipos().stream().filter(t->t.codigo().equals(e.tipo())).findFirst().ifPresent(tipo::setValue);
                prioridad.setValue(e.prioridad());
                programada.setValue(e.programada());
                proxima.setValue(e.proxima());
                monedas.stream().filter(m->m.id().equals(e.moneda())).findFirst().ifPresent(moneda::setValue);
                referencia.setValue(vacio(e.referencia()));
                descripcion.setValue(e.descripcion());
                observacion.setValue(vacio(e.observacion()));
            }
            d.add(formulario("INFORMACIÓN GENERAL",activo,tipo,programada,prioridad,responsable,proveedor),
                descripcion,formulario("INFORMACIÓN ECONÓMICA",estimado,moneda,referencia),formulario("PROGRAMACIÓN",proxima),observacion);
            guardar(d,ActionType.SAVE,"Guardar",()->{
                var e=new Entrada(activo.isEmpty()?null:activo.getValue().id(),tipo.isEmpty()?null:tipo.getValue().codigo(),
                    prioridad.getValue(),programada.getValue(),proxima.getValue(),id(responsable),id(proveedor),
                    moneda.isEmpty()?null:moneda.getValue().id(),estimado.getValue(),referencia.getValue(),descripcion.getValue(),observacion.getValue());
                if(previo==null) servicio.crear(e);
                else servicio.editar(previo.id(),previo.version(),e);
                d.close();
                refrescarTrasGuardar();
                if(previo!=null) ver(previo.id());
            });
            d.open();
            return true;
        } catch(RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible abrir el formulario.");
            return false;
        }
    }

    private void iniciar(Detalle actual,Dialog origen) {
        var d=dialogo("INICIAR MANTENIMIENTO");
        var fuera=new Checkbox("¿Activo fuera de servicio durante el mantenimiento?");
        var token=UUID.randomUUID();
        d.add(fuera);
        guardar(d,ActionType.PROCESS,"Iniciar mantenimiento",()->{
            servicio.iniciar(actual.id(),actual.version(),fuera.getValue(),token);
            cerrarYActualizar(d,origen,actual.id());
        });
        d.open();
    }

    private void completar(Detalle actual,Dialog origen) {
        ejecutar("No fue posible abrir la finalización.",()->{
            var d=dialogo("COMPLETAR MANTENIMIENTO");
            var realizada=fecha("Fecha realizada");
            realizada.setValue(servicio.hoy());
            var proxima=fecha("Próximo mantenimiento");
            proxima.setValue(actual.datos().proxima());
            var costo=dinero("Costo real",null);
            var proveedor=opcion("Proveedor / Técnico",servicio.catalogos().proveedores(),actual.datos().proveedor(),actual.proveedor());
            var moneda=texto("Moneda",6);
            moneda.setValue(actual.moneda());
            moneda.setReadOnly(true);
            var trabajo=area("Trabajo realizado",2000);
            var observacion=area("Observación",2000);
            observacion.setValue(vacio(actual.datos().observacion()));
            for(var f:List.<com.vaadin.flow.component.HasValue<?,?>>of(realizada,costo,trabajo)) f.setRequiredIndicatorVisible(true);
            d.add(formulario("INFORMACIÓN GENERAL",realizada,costo,moneda,proveedor,proxima),trabajo,observacion);
            var token=UUID.randomUUID();
            guardar(d,ActionType.ACCEPT,"Completar mantenimiento",()->{
                servicio.completar(actual.id(),actual.version(),new Completar(realizada.getValue(),costo.getValue(),id(proveedor),
                    trabajo.getValue(),proxima.getValue(),observacion.getValue()),token);
                cerrarYActualizar(d,origen,actual.id());
            });
            d.open();
        });
    }

    private void cancelar(Detalle actual,Dialog origen) {
        var d=dialogo("CANCELAR MANTENIMIENTO");
        var motivo=area("Motivo",1000);
        motivo.setRequiredIndicatorVisible(true);
        var token=UUID.randomUUID();
        d.add(motivo);
        guardar(d,ActionType.VOID,"Cancelar mantenimiento",()->{
            servicio.cancelar(actual.id(),actual.version(),motivo.getValue(),token);
            cerrarYActualizar(d,origen,actual.id());
        });
        d.open();
    }

    private void cerrarYActualizar(Dialog d,Dialog origen,UUID id) {
        d.close();
        origen.close();
        refrescarTrasGuardar();
        ver(id);
    }

    private void refrescarTrasGuardar() {
        Notification.show("Mantenimiento guardado.");
        ejecutar("El mantenimiento se guardó. No fue posible actualizar el listado.",this::actualizar);
    }

    private static void guardar(Dialog d,ActionType tipo,String etiqueta,Runnable guardar) {
        d.getFooter().add(new AppActionButton(tipo,ButtonSize.MAIN,etiqueta,e->{
            e.getSource().setEnabled(false);
            try {
                ejecutar("No fue posible guardar el mantenimiento.",guardar);
            } finally {
                e.getSource().setEnabled(true);
            }
        }),accion(ActionType.CANCEL,"Cancelar",d::close));
    }

    static void configurarTabla(AppGrid<Fila> t,boolean contextual) {
        columna(t,"Código",Fila::codigo,1);
        if(!contextual) columna(t,"Activo",f->f.activoCodigo()+" — "+f.activoNombre(),3);
        columna(t,"Tipo",Fila::tipo,1);
        columna(t,"Fecha",f->formato(f.fecha()),1);
        if(contextual) columna(t,"Descripción",Fila::descripcion,3).setWidth("200px").setAutoWidth(false);
        columna(t,"Costo",f->importe(f.moneda(),f.costo()),2).setTextAlign(ColumnTextAlign.END);
        t.addComponentColumn(f->etiqueta(f.estado(),f.vencido())).setHeader("Estado").setAutoWidth(true).setFlexGrow(1);
    }

    private static com.vaadin.flow.component.grid.Grid.Column<Fila> columna(AppGrid<Fila> t,String titulo,Function<Fila,?> valor,int crecer) {
        return t.addColumn(valor::apply).setHeader(titulo).setAutoWidth(true).setFlexGrow(crecer);
    }

    private static AppStatusBadge etiqueta(Estado e,boolean vencido) {
        if(vencido) return new AppStatusBadge("Vencido",AppStatusBadge.Variant.DANGER);
        return new AppStatusBadge(e.etiqueta(),switch(e){
            case PROGRAMADO -> AppStatusBadge.Variant.PRIMARY;
            case EN_PROCESO -> AppStatusBadge.Variant.WARNING;
            case COMPLETADO -> AppStatusBadge.Variant.SUCCESS;
            case CANCELADO -> AppStatusBadge.Variant.NEUTRAL;
        });
    }

    private static Div tarjeta(String titulo,String valor) {
        var nombre=new Span(titulo);
        nombre.addClassName("cc-detail-label");
        var dato=new Span(valor);
        dato.addClassName("cc-detail-value");
        var d=new Div(nombre,dato);
        d.addClassName("cc-card");
        return d;
    }

    private static AppDetailSection seccion(String titulo) {
        var d=new AppDetailSection(titulo);
        d.addClassName("cc-detail-three");
        return d;
    }

    private static AppDetailSection anchoCompleto(String titulo,String etiqueta,String valor) {
        var d=new AppDetailSection(titulo).field(etiqueta,valor);
        d.addClassName("cc-detail-full");
        return d;
    }

    private static Div formulario(String titulo,Component... campos) {
        var cabecera=new H3(titulo);
        cabecera.addClassName("cc-section-heading");
        var camposGrid=new Div(campos);
        camposGrid.addClassName("cc-detail-grid");
        var d=new Div(cabecera,camposGrid);
        d.addClassNames("cc-form-section","cc-detail-three");
        return d;
    }

    private static Dialog dialogo(String titulo) {
        var d=new Dialog();
        d.setHeaderTitle(titulo);
        d.setWidth("min(960px,96vw)");
        d.setMaxHeight("92vh");
        return d;
    }

    private static DatePicker fecha(String etiqueta) {
        var c=new DatePicker(etiqueta);
        c.setLocale(Locale.forLanguageTag("es-DO"));
        c.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        c.setWidthFull();
        c.setClearButtonVisible(true);
        return c;
    }

    private static TextField texto(String etiqueta,int max) {
        var c=new TextField(etiqueta);
        c.setWidthFull();
        c.setMaxLength(max);
        return c;
    }

    private static TextArea area(String etiqueta,int max) {
        var c=new TextArea(etiqueta);
        c.setWidthFull();
        c.setMaxLength(max);
        return c;
    }

    private static BigDecimalField dinero(String etiqueta,BigDecimal valor) {
        var c=new BigDecimalField(etiqueta);
        c.setWidthFull();
        c.setValue(valor);
        c.addAttachListener(e->{if(c.getValue()!=null) PosNumberFormatter.showMoney(c);});
        c.addBlurListener(e->{if(c.getValue()!=null) PosNumberFormatter.showMoney(c);});
        c.addFocusListener(e->{if(c.getValue()!=null)
            c.getElement().executeJs("if(this.inputElement)this.inputElement.value=$0",c.getValue().toPlainString());});
        return c;
    }

    private static <T> ComboBox<T> combo(String etiqueta,SerializableFunction<T,String> nombre,List<T> valores) {
        var c=new ComboBox<T>(etiqueta);
        c.setItemLabelGenerator(nombre::apply);
        c.setItems(valores);
        c.setWidthFull();
        c.setClearButtonVisible(true);
        return c;
    }

    private static ComboBox<Opcion> opcion(String etiqueta,List<Opcion> opciones,UUID id,String nombre) {
        var valores=new ArrayList<>(opciones);
        if(id!=null && valores.stream().noneMatch(o->o.id().equals(id))) valores.add(new Opcion(id,nombre));
        var c=combo(etiqueta,Opcion::nombre,valores);
        valores.stream().filter(o->o.id().equals(id)).findFirst().ifPresent(c::setValue);
        return c;
    }

    private static UUID id(ComboBox<Opcion> c) { return c.isEmpty()?null:c.getValue().id(); }
    private static String vacio(String texto) { return texto==null?"":texto; }
    private static String formato(LocalDate fecha) { return fecha==null?null:FECHA.format(fecha); }
    private static String importe(String moneda,BigDecimal valor) { return valor==null?null:moneda+" "+PosNumberFormatter.money(valor); }
    private static boolean puede(String permiso) { return TenantContext.principalActual().permisos().contains(MantenimientoService.RECURSO+permiso); }
    private static AppActionButton accion(ActionType tipo,String etiqueta,Runnable accion) {
        return new AppActionButton(tipo,ButtonSize.MAIN,etiqueta,e->accion.run());
    }
    private static void ejecutar(String mensaje,Runnable operacion) {
        try { operacion.run(); }
        catch(RuntimeException ex) { UiErrorHandler.show(ex,mensaje); }
    }
}
