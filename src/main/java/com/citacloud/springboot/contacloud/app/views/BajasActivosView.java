package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.BajaActivoDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.BajaActivoPdfService;
import com.citacloud.springboot.contacloud.app.services.BajaActivoService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "activos-fijos/bajas", layout = MainLayout.class)
@PageTitle("Bajas | ContaCloud")
@PermitAll
public class BajasActivosView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final BajaActivoService servicio;
    private final BajaActivoPdfService pdf;
    private final ModuleAuthorization autorizacion;
    private final Div indicadores = new Div();
    private final AppGrid<Fila> tabla = new AppGrid<>(Fila.class);
    private final AppPagination pagina = new AppPagination(p -> cargar());
    private final DatePicker desde = fecha("Desde");
    private final DatePicker hasta = fecha("Hasta");
    private final ComboBox<Tipo> tipo = combo("Tipo de baja", Tipo::nombre, List.of());
    private final ComboBox<Estado> estado = combo("Estado", Estado::etiqueta, List.of(Estado.values()));
    private final TextField buscar = texto("Buscar", 150);
    private final AppActionButton nuevo;

    public BajasActivosView(BajaActivoService servicio, BajaActivoPdfService pdf, ModuleAuthorization autorizacion) {
        this.servicio = servicio;
        this.pdf = pdf;
        this.autorizacion = autorizacion;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        addClassName("cc-page");
        nuevo = accion(ActionType.NEW, "Nueva baja", () -> ejecutar("No fue posible abrir el formulario.", () -> editar(null)));
        nuevo.setVisible(false);
        indicadores.addClassName("cc-cartera-indicadores");
        var filtros = new Div(desde, hasta, tipo, estado, buscar);
        filtros.addClassName("cc-maintenance-filters");
        tipo.setPlaceholder("Todos");
        estado.setPlaceholder("Todos");
        buscar.addClassName("cc-maintenance-search");
        buscar.setPlaceholder("Código de baja, activo, marca, modelo o número de serie");
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        for (var campo : List.<HasValue<?, ?>>of(desde, hasta, tipo, estado, buscar))
            campo.addValueChangeListener(e -> { if (e.isFromClient()) { pagina.reset(); cargar(); } });
        tabla.addColumn(Fila::codigo).setHeader("Código").setAutoWidth(true);
        tabla.addColumn(Fila::activo).setHeader("Activo").setFlexGrow(2);
        tabla.addColumn(f -> formato(f.fecha())).setHeader("Fecha efectiva").setAutoWidth(true);
        tabla.addColumn(Fila::tipo).setHeader("Tipo").setAutoWidth(true);
        tabla.addColumn(f -> importe(f.moneda(), f.libros())).setHeader("Valor en libros").setAutoWidth(true);
        tabla.addComponentColumn(f -> badge(f.estado())).setHeader("Estado").setAutoWidth(true);
        tabla.addComponentColumn(f -> new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION, "Ver baja", e -> ver(f.id())))
            .setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        tabla.setWidthFull();
        tabla.setEmptyStateText("No se encontraron bajas.");
        add(new AppPageHeader("BAJAS", "Registra y consulta el retiro de activos fijos.", nuevo), indicadores, filtros, tabla, pagina);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!autorizacion.canAccess("ACTIVOS_FIJOS", "bajas_activos.ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        nuevo.setVisible(puede("crear"));
        ejecutar("No fue posible cargar Bajas.", () -> {
            tipo.setItems(servicio.tipos());
            actualizar();
        });
    }

    private void actualizar() {
        var totales = servicio.totales();
        indicadores.removeAll();
        indicadores.add(tarjeta("BAJAS DEL MES", Long.toString(totales.stream().mapToLong(Total::cantidad).sum())),
            tarjeta("COSTO ORIGINAL DEL MES", totales.stream().map(t -> importe(t.moneda(), t.costo()))
                .collect(Collectors.joining(" · " ,"", totales.isEmpty() ? "—" : ""))),
            tarjeta("VALOR EN LIBROS DEL MES", totales.stream().map(t -> importe(t.moneda(), t.libros()))
                .collect(Collectors.joining(" · ", "", totales.isEmpty() ? "—" : ""))));
        pagina.reset();
        cargar();
    }

    private void cargar() {
        ejecutar("No fue posible consultar las bajas.", () -> {
            var solicitud = pagina.currentRequest();
            var resultados = servicio.listar(new Filtro(desde.getValue(), hasta.getValue(),
                tipo.isEmpty() ? null : tipo.getValue().codigo(), estado.getValue(), buscar.getValue()), solicitud.page(), solicitud.size());
            tabla.setItems(resultados.getContent());
            pagina.setTotal(resultados.getTotalElements());
        });
    }

    private void ver(UUID id) {
        ejecutar("No fue posible consultar la baja.", () -> {
            var d = servicio.detalle(id);
            var e = d.datos();
            var modal = dialogo("BAJA — " + d.codigo());
            modal.add(seccion("INFORMACIÓN GENERAL").field("Código", d.codigo()).badge("Estado", badge(d.estado()))
                .field("Fecha efectiva", formato(e.fecha())).field("Tipo", d.snapshot() == null ? d.tipo() : d.snapshot().tipo())
                .field("Referencia", e.referencia()).field("Factura de venta", d.factura())
                .field("Confirmada por", d.snapshot() == null ? null : d.snapshot().confirmadoPor())
                .field("Fecha de confirmación", d.confirmadoEn() == null ? null : formato(d.confirmadoEn().toLocalDate())));
            if (d.activo() != null) modal.add(datosActivo(d.activo()), economia(d.activo(), e.obtenido()));
            modal.add(textoCompleto("MOTIVO", "Motivo", e.motivo()));
            if (e.observacion() != null) modal.add(textoCompleto("OBSERVACIONES", "Observaciones", e.observacion()));
            if (d.estado() == Estado.BORRADOR) {
                if (puede("editar")) modal.getFooter().add(accion(ActionType.EDIT, "Editar baja", () ->
                    ejecutar("No fue posible editar la baja.", () -> { editar(d); modal.close(); })));
                if (puede("confirmar")) modal.getFooter().add(accion(ActionType.ACCEPT, "Confirmar baja", () -> confirmar(d, modal)));
            } else if (puede("imprimir")) {
                modal.getFooter().add(accion(ActionType.PRINT, "Imprimir", () ->
                    ejecutar("No fue posible abrir el PDF.", () -> PdfEnPestana.abrir(pdf.generar(id)))));
            }
            modal.getFooter().add(accion(ActionType.CLOSE, "Cerrar", modal::close));
            modal.open();
        });
    }

    private void editar(Detalle previo) {
        var modal = dialogo(previo == null ? "NUEVA BAJA" : "EDITAR " + previo.codigo());
        var tipos = servicio.tipos();
        var tipoBaja = combo("Tipo de baja", Tipo::nombre, tipos);
        var activo = combo("Activo", Activo::etiqueta, List.of());
        var efectiva = fecha("Fecha efectiva");
        var motivo = area("Motivo", 2000);
        var obtenido = dinero("Valor obtenido", previo == null ? BigDecimal.ZERO : previo.datos().obtenido());
        var referencia = texto("Referencia", 150);
        var observacion = area("Observaciones", 2000);
        var factura = combo("Factura de venta (opcional)", Factura::codigo, List.of());
        var resumen = new Div();
        activo.setItems(q -> servicio.buscarActivos(q.getFilter().orElse(""), q.getOffset(), q.getLimit()).stream());
        factura.setItems(q -> activo.isEmpty() ? java.util.stream.Stream.empty()
            : servicio.facturas(activo.getValue().id(), q.getFilter().orElse(""), q.getOffset(), q.getLimit(), null).stream());
        efectiva.setValue(servicio.hoy());
        for (var campo : List.<HasValue<?, ?>>of(activo, efectiva, tipoBaja, motivo)) campo.setRequiredIndicatorVisible(true);
        if (previo != null) {
            var e = previo.datos();
            if (previo.activo() != null) activo.setValue(previo.activo());
            tipos.stream().filter(t -> t.codigo().equals(e.tipo())).findFirst().ifPresent(tipoBaja::setValue);
            efectiva.setValue(e.fecha());
            motivo.setValue(e.motivo());
            referencia.setValue(vacio(e.referencia()));
            observacion.setValue(vacio(e.observacion()));
            if (e.factura() != null && !activo.isEmpty())
                servicio.facturas(e.activo(), "", 0, 1, e.factura()).stream().findFirst().ifPresent(factura::setValue);
        }
        Runnable refrescar = () -> ejecutar("No fue posible consultar el activo.", () -> {
            resumen.removeAll();
            if (!activo.isEmpty()) {
                var a = servicio.situacion(activo.getValue().id(), efectiva.getValue());
                resumen.add(datosActivo(a), economia(a, obtenido.getValue() == null ? BigDecimal.ZERO : obtenido.getValue()));
            }
        });
        factura.setVisible(esVenta(tipoBaja));
        tipoBaja.addValueChangeListener(e -> {
            factura.setVisible(esVenta(tipoBaja));
            if (!esVenta(tipoBaja)) factura.clear();
        });
        activo.addValueChangeListener(e -> { factura.clear(); factura.getDataProvider().refreshAll(); refrescar.run(); });
        efectiva.addValueChangeListener(e -> refrescar.run());
        obtenido.addValueChangeListener(e -> refrescar.run());
        refrescar.run();
        modal.add(formulario("INFORMACIÓN GENERAL", activo, tipoBaja, efectiva), motivo,
            formulario("DISPOSICIÓN DEL ACTIVO", obtenido, referencia, factura), resumen, observacion);
        modal.getFooter().add(accion(ActionType.SAVE, "Guardar borrador", () -> ejecutar("No fue posible guardar la baja.", () -> {
            var e = new Entrada(activo.isEmpty() ? null : activo.getValue().id(), tipoBaja.isEmpty() ? null : tipoBaja.getValue().codigo(),
                efectiva.getValue(), motivo.getValue(), obtenido.getValue(), referencia.getValue(),
                factura.isEmpty() ? null : factura.getValue().id(), observacion.getValue());
            UUID id = previo == null ? servicio.crear(e) : previo.id();
            if (previo != null) servicio.editar(id, previo.version(), e);
            modal.close();
            Notification.show("Borrador guardado.");
            actualizar();
            ver(id);
        })), accion(ActionType.CANCEL, "Cancelar", modal::close));
        modal.open();
    }

    private void confirmar(Detalle d, Dialog detalle) {
        ejecutar("No fue posible preparar la confirmación.", () -> {
            var revision = servicio.revisar(d.id());
            var solicitud = UUID.randomUUID();
            var modal = dialogo("CONFIRMAR BAJA — " + d.codigo());
            modal.add(new Span("El activo saldrá del registro activo en la fecha indicada. Esta baja quedará en el historial."),
                seccion("BAJA").field("Activo", revision.activo().etiqueta()).field("Tipo", revision.detalle().tipo())
                    .field("Fecha efectiva", formato(revision.detalle().datos().fecha())),
                economia(revision.activo(), revision.detalle().datos().obtenido()));
            modal.getFooter().add(accion(ActionType.ACCEPT, "Confirmar baja definitiva", () ->
                ejecutar("No fue posible confirmar la baja.", () -> {
                    servicio.confirmar(revision, solicitud);
                    modal.close();
                    detalle.close();
                    Notification.show("Baja confirmada.");
                    actualizar();
                    ver(d.id());
                })), accion(ActionType.CANCEL, "Cancelar", modal::close));
            modal.open();
        });
    }

    private static boolean esVenta(ComboBox<Tipo> tipo) {
        return !tipo.isEmpty() && "VENTA".equals(tipo.getValue().codigo());
    }

    private static AppDetailSection datosActivo(Activo a) {
        return seccion("INFORMACIÓN DEL ACTIVO").field("Activo", a.etiqueta()).field("Categoría", a.categoria())
            .field("Fecha de adquisición", formato(a.adquisicion())).field("Marca", a.marca()).field("Modelo", a.modelo())
            .field("Número de serie", a.serie()).field("Inicio de depreciación", formato(a.inicio()))
            .field("Última depreciación", formato(a.ultima())).field("Moneda", a.moneda());
    }

    static AppDetailSection economia(Activo a, BigDecimal obtenido) {
        var resultado = BajaActivoService.resultado(a.libros(), obtenido);
        return seccion("INFORMACIÓN ECONÓMICA").field("Costo original", importe(a.moneda(), a.costo()))
            .field("Depreciación acumulada", importe(a.moneda(), a.depreciacion())).field("Ajustes netos", importe(a.moneda(), a.ajustes()))
            .field("Valor en libros", importe(a.moneda(), a.libros())).field("Valor obtenido", importe(a.moneda(), obtenido))
            .field("Ganancia", importe(a.moneda(), resultado.ganancia())).field("Pérdida", importe(a.moneda(), resultado.perdida()));
    }

    private static AppStatusBadge badge(Estado estado) {
        return new AppStatusBadge(estado.etiqueta(), estado == Estado.CONFIRMADA
            ? AppStatusBadge.Variant.SUCCESS : AppStatusBadge.Variant.NEUTRAL);
    }

    private static AppDetailSection textoCompleto(String titulo, String etiqueta, String texto) {
        var seccion = new AppDetailSection(titulo).field(etiqueta, texto);
        seccion.addClassName("cc-detail-full");
        return seccion;
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

    private static String vacio(String texto) { return texto==null?"":texto; }
    private static String formato(LocalDate fecha) { return fecha==null?null:FECHA.format(fecha); }
    private static String importe(String moneda,BigDecimal valor) { return valor==null?null:moneda+" "+PosNumberFormatter.money(valor); }
    private static boolean puede(String permiso) { return TenantContext.principalActual().permisos().contains("bajas_activos." + permiso); }
    private static AppActionButton accion(ActionType tipo,String etiqueta,Runnable accion) {
        return new AppActionButton(tipo,ButtonSize.MAIN,etiqueta,e->accion.run());
    }
    private static void ejecutar(String mensaje,Runnable operacion) {
        try { operacion.run(); }
        catch(RuntimeException ex) { UiErrorHandler.show(ex,mensaje); }
    }
}
