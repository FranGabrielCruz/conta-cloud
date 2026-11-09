package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.ActivoFijoPdfService;
import com.citacloud.springboot.contacloud.app.services.ActivoFijoService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.citacloud.springboot.contacloud.app.services.ActivoFijoService.*;

@Route(value="activos-fijos",layout=MainLayout.class)
@PageTitle("Activos fijos | ContaCloud")
@PermitAll
public class ActivosFijosView extends VerticalLayout implements BeforeEnterObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ActivoFijoService servicio;
    private final ModuleAuthorization autorizacion;
    private final AppActionButton nuevo;
    private final AppActionButton reporte;
    private final ComboBox<Categoria> categoria=new ComboBox<>("Categoría");
    private final ComboBox<Opcion> sucursal=new ComboBox<>("Sucursal");
    private final ComboBox<String> ubicacion=new ComboBox<>("Ubicación");
    private final ComboBox<Estado> estado=new ComboBox<>("Estado");
    private final TextField buscar=new TextField("Buscar");
    private final Div indicadores=new Div();
    private final AppGrid<Resumen> grid=new AppGrid<>(Resumen.class);
    private final AppPagination paginacion=new AppPagination(p->cargar());

    public ActivosFijosView(ActivoFijoService servicio, ActivoFijoPdfService pdf, ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.autorizacion=autorizacion;
        setPadding(false);
        setSpacing(false);
        addClassName("cc-page");
        nuevo=new AppActionButton(ActionType.NEW,ButtonSize.MAIN,"Nuevo activo",e->formulario(null,null));
        reporte=new AppActionButton(ActionType.PDF,ButtonSize.MAIN,"Reporte de activos",e->{
            try {
                PdfEnPestana.abrir(pdf.generar(filtro()));
            } catch (RuntimeException ex) {
                UiErrorHandler.show(ex,"No fue posible abrir el reporte de activos.");
            }
        });
        nuevo.setVisible(false);
        reporte.setVisible(false);
        indicadores.addClassName("cc-cartera-indicadores");
        categoria.setItemLabelGenerator(Categoria::nombre);
        sucursal.setItemLabelGenerator(Opcion::nombre);
        estado.setItemLabelGenerator(Estado::etiqueta);
        estado.setItems(Estado.values());
        for (var filtro:List.of(categoria,sucursal,ubicacion,estado)) {
            filtro.setClearButtonVisible(true);
            filtro.setPlaceholder("Todos");
            filtro.addValueChangeListener(e->{if (e.isFromClient()) reiniciar();});
        }
        buscar.setPlaceholder("Código, nombre, serie o responsable");
        buscar.setMaxLength(150);
        buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.addValueChangeListener(e->{if (e.isFromClient()) reiniciar();});
        var filtros=new HorizontalLayout(categoria,sucursal,ubicacion,estado,buscar);
        filtros.setWidthFull();
        filtros.addClassName("cc-filter-bar");
        configurarGrid();
        add(new AppPageHeader("ACTIVOS","Consulta el registro maestro de activos fijos.",nuevo,reporte),
            indicadores,filtros,grid,paginacion);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!autorizacion.canAccess(MODULO,VER)) {
            Notification.show("No tienes permiso para consultar activos fijos.");
            event.rerouteTo(DashboardView.class);
            return;
        }
        nuevo.setVisible(puede(CREAR));
        reporte.setVisible(puede(REPORTAR));
        try {
            cargarFiltros();
            reiniciar();
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible cargar el registro de activos.");
        }
    }

    private void configurarGrid() {
        columna(grid,"Código",Resumen::codigo);
        grid.addColumn(Resumen::nombre).setHeader("Activo").setAutoWidth(true).setFlexGrow(2);
        columna(grid,"Categoría",Resumen::categoria);
        grid.addColumn(a->Objects.toString(a.ubicacion(),a.sucursal())).setHeader("Ubicación").setAutoWidth(true);
        columna(grid,"Valor en libros",a->dinero(a.moneda(),a.valorLibros())).setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(a->etiqueta(a.estado())).setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(a->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver activo",e->ver(a.id())))
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        grid.setEmptyStateText("No se encontraron activos fijos.");
    }

    private Filtro filtro() {
        return new Filtro(categoria.getValue()==null?null:categoria.getValue().codigo(),
            sucursal.getValue()==null?null:sucursal.getValue().id(),ubicacion.getValue(),estado.getValue(),buscar.getValue());
    }

    private void cargarFiltros() {
        var catalogos=servicio.filtros();
        var categoriaActual=categoria.getValue();
        var sucursalActual=sucursal.getValue();
        var ubicacionActual=ubicacion.getValue();
        categoria.setItems(catalogos.categorias());
        sucursal.setItems(catalogos.sucursales());
        ubicacion.setItems(catalogos.ubicaciones());
        if (categoriaActual!=null && catalogos.categorias().contains(categoriaActual)) categoria.setValue(categoriaActual);
        if (sucursalActual!=null && catalogos.sucursales().contains(sucursalActual)) sucursal.setValue(sucursalActual);
        if (ubicacionActual!=null && catalogos.ubicaciones().contains(ubicacionActual)) ubicacion.setValue(ubicacionActual);
    }

    private void reiniciar() {
        paginacion.reset();
        cargar();
    }

    private void cargar() {
        try {
            var p=paginacion.currentRequest();
            var listado=servicio.consultar(filtro(),p.page(),p.size());
            grid.setItems(listado.activos().getContent());
            mostrarTotales(listado.totales());
            paginacion.setTotal(listado.activos().getTotalElements());
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible cargar los activos.");
        }
    }

    private void mostrarTotales(List<Totales> totales) {
        indicadores.removeAll();
        var libros=totales.stream().map(t->dinero(t.moneda(),t.valorLibros())).collect(Collectors.joining(" · "));
        indicadores.add(
            tarjeta("TOTAL ACTIVOS",Long.toString(totales.stream().mapToLong(Totales::cantidad).sum())),
            tarjeta("ACTIVOS VIGENTES",Long.toString(totales.stream().mapToLong(Totales::vigentes).sum())),
            tarjeta("EN MANTENIMIENTO",Long.toString(totales.stream().mapToLong(Totales::mantenimiento).sum())),
            tarjeta("VALOR EN LIBROS",libros.isEmpty()?"—":libros));
    }

    private static Div tarjeta(String titulo, String valor) {
        var etiqueta=new Span(titulo);
        etiqueta.addClassName("cc-detail-label");
        var importe=new Span(valor);
        importe.addClassName("cc-detail-value");
        var tarjeta=new Div(etiqueta,importe);
        tarjeta.addClassName("cc-card");
        return tarjeta;
    }

    private void ver(UUID id) {
        try {
            var detalle=servicio.detalle(id);
            var dialogo=detalle(detalle);
            if (puede(EDITAR) && detalle.activo().resumen().estado()!=Estado.BAJA)
                dialogo.getFooter().add(new AppActionButton(ActionType.EDIT,ButtonSize.MAIN,"Editar activo",
                    e->formulario(detalle.activo(),dialogo)));
            dialogo.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->dialogo.close()));
            dialogo.open();
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible consultar el activo.");
        }
    }

    private void formulario(Registro activo, Dialog origen) {
        try {
            var editor=new ActivoFijoForm(servicio,activo,guardado->{
                try {
                    cargarFiltros();
                } catch (RuntimeException ex) {
                    UiErrorHandler.show(ex,"No fue posible actualizar los filtros.");
                }
                reiniciar();
                Notification.show("Activo "+guardado.resumen().codigo()+" guardado.");
                if (origen!=null) ver(guardado.resumen().id());
            },()->{if (origen!=null) origen.open();});
            if (origen!=null) origen.close();
            editor.open();
        } catch (RuntimeException ex) {
            UiErrorHandler.show(ex,"No fue posible abrir el formulario del activo.");
        }
    }

    static Dialog detalle(Detalle detalle) {
        var activo=detalle.activo();
        var a=activo.resumen();
        var datos=activo.datos();
        var dep=datos.depreciacion();
        var d=new Dialog();
        d.setHeaderTitle("ACTIVO "+a.codigo());
        d.setWidth("min(960px, 96vw)");
        var observacion=new AppDetailSection("INFORMACIÓN ADICIONAL").field("Observación",datos.observacion());
        observacion.addClassName("cc-detail-full");
        d.add(seccionTresColumnas("INFORMACIÓN GENERAL")
                .field("Código",a.codigo()).field("Activo",a.nombre()).badge("Estado",etiqueta(a.estado()))
                .field("Categoría",a.categoria()).field("Tipo","Tangible").field("Marca",datos.marca())
                .field("Modelo",datos.modelo()).field("Número de serie",datos.serie()).badge("Condición operativa",
                    new AppStatusBadge(a.condicion(),"En mantenimiento".equals(a.condicion())
                        ?AppStatusBadge.Variant.WARNING:AppStatusBadge.Variant.NEUTRAL)),
            seccionTresColumnas("ADQUISICIÓN").field("Fecha de adquisición",fecha(a.fecha()))
                .field("Costo de adquisición",dinero(a.moneda(),a.costo())).field("Moneda",a.moneda())
                .field("Proveedor",activo.proveedor()).field("Documento de compra",documentoCompra(activo)),
            seccionTresColumnas("UBICACIÓN Y ASIGNACIÓN").field("Sucursal",a.sucursal()).field("Ubicación",a.ubicacion())
                .field("Responsable",activo.destino().responsable()),
            seccionTresColumnas("DEPRECIACIÓN").field("Depreciable",dep.depreciable()?"Sí":"No")
                .field("Método",dep.metodo()==null?null:dep.metodo().etiqueta())
                .field("Vida útil",dep.vidaMeses()==null?null:dep.vidaMeses()+" meses")
                .field("Inicio de depreciación",fecha(dep.inicio()))
                .field("Valor residual",dep.residual()==null?null:dinero(a.moneda(),dep.residual()))
                .field("Depreciación acumulada",dinero(a.moneda(),a.depreciacion()))
                .field("Valor en libros",dinero(a.moneda(),a.valorLibros())),
            observacion);
        agregarHistorial(d,detalle,a.moneda());
        return d;
    }

    private static AppDetailSection seccionTresColumnas(String titulo) {
        var seccion=new AppDetailSection(titulo);
        seccion.addClassName("cc-detail-three");
        return seccion;
    }

    private static String documentoCompra(Registro activo) {
        return java.util.stream.Stream.of(activo.factura(),activo.datos().adquisicion().referencia())
            .filter(valor->valor!=null&&!valor.isBlank()).distinct().collect(Collectors.joining(" · "));
    }

    private static void agregarHistorial(Dialog d, Detalle detalle, String moneda) {
        var asignaciones=new AppGrid<>(HistorialAsignacion.class);
        columna(asignaciones,"Fecha",h->FECHA_HORA.format(h.fecha()));
        columna(asignaciones,"Usuario",HistorialAsignacion::usuario);
        asignaciones.addColumn(h->destino(h.anterior())).setHeader("Asignación anterior").setAutoWidth(true);
        asignaciones.addColumn(h->destino(h.nueva())).setHeader("Asignación nueva").setAutoWidth(true);
        asignaciones.setItems(detalle.asignaciones());
        asignaciones.setEmptyStateText("Sin asignaciones visibles para tus sucursales.");
        d.add(new com.vaadin.flow.component.details.Details("Historial de ubicación y asignación",asignaciones));
        var movimientos=new AppGrid<>(Movimiento.class);
        columna(movimientos,"Fecha",m->fecha(m.fecha()));
        columna(movimientos,"Tipo",m->m.tipo().etiqueta());
        movimientos.addColumn(Movimiento::descripcion).setHeader("Descripción").setAutoWidth(true);
        columna(movimientos,"Importe",m->dinero(moneda,m.importe())).setTextAlign(ColumnTextAlign.END);
        movimientos.setItems(detalle.movimientos());
        movimientos.setEmptyStateText("Sin movimientos registrados para tus sucursales.");
        d.add(new com.vaadin.flow.component.details.Details("Depreciación, mantenimiento, ajustes y bajas",movimientos));
    }

    private static String destino(Destino d) {
        if (d==null) return "Registro inicial";
        return java.util.stream.Stream.of(d.sucursal(),d.ubicacion(),d.responsable())
            .filter(Objects::nonNull).collect(Collectors.joining(" · "));
    }

    private static <T> com.vaadin.flow.component.grid.Grid.Column<T> columna(AppGrid<T> grid,String titulo,Function<T,String> valor) {
        return grid.addColumn(valor::apply).setHeader(titulo).setAutoWidth(true).setFlexGrow(0);
    }

    private static AppStatusBadge etiqueta(Estado estado) {
        return new AppStatusBadge(estado.etiqueta(),switch (estado) {
            case ACTIVO -> AppStatusBadge.Variant.SUCCESS;
            case INACTIVO -> AppStatusBadge.Variant.NEUTRAL;
            case BAJA -> AppStatusBadge.Variant.DANGER;
        });
    }

    private static boolean puede(String permiso) { return TenantContext.principalActual().permisos().contains(permiso); }
    private static String fecha(LocalDate fecha) { return fecha==null?null:FECHA.format(fecha); }
    private static String dinero(String moneda,BigDecimal valor) { return moneda+" "+PosNumberFormatter.money(valor); }
}
