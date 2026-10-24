package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.CotizacionDto;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.models.EstadoCotizacion;
import com.citacloud.springboot.contacloud.app.services.CotizacionService;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Selector paginado de cotizaciones disponible desde Más acciones del POS. */
public class CargarCotizacionDialog extends Dialog {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final CotizacionService service;
    private final Consumer<UUID> seleccionar;
    private final TextField buscar = new TextField();
    private final DatePicker desde = new DatePicker("Desde");
    private final DatePicker hasta = new DatePicker("Hasta");
    private final ComboBox<EstadoCotizacion> estado = new ComboBox<>("Estado");
    private final Grid<CotizacionDto> grid = new Grid<>();
    private final PosDialogPagination paginacion = new PosDialogPagination(r -> cargar());
    private final ProgressBar cargando = new ProgressBar();
    private boolean consultando;

    public CargarCotizacionDialog(CotizacionService service,Consumer<UUID> seleccionar) {
        this.service=Objects.requireNonNull(service);this.seleccionar=Objects.requireNonNull(seleccionar);
        setHeaderTitle("CARGAR COTIZACIÓN");setWidth("min(1060px,96vw)");setMaxHeight("94vh");
        setCloseOnOutsideClick(false);addClassName("cc-load-quote-dialog");
        buscar.setPlaceholder("Buscar por cotización, cliente o RNC/Cédula...");buscar.setClearButtonVisible(true);
        buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(300);buscar.setWidthFull();
        buscar.addValueChangeListener(e->{if(e.isFromClient()){paginacion.reset();cargar();}});
        estado.setItems(EstadoCotizacion.ENVIADA,EstadoCotizacion.ACEPTADA);estado.setClearButtonVisible(true);
        estado.setItemLabelGenerator(CargarCotizacionDialog::estado);
        desde.addValueChangeListener(e->{if(e.isFromClient()){paginacion.reset();cargar();}});
        hasta.addValueChangeListener(e->{if(e.isFromClient()){paginacion.reset();cargar();}});
        estado.addValueChangeListener(e->{if(e.isFromClient()){paginacion.reset();cargar();}});
        configurarGrid();cargando.setIndeterminate(true);cargando.setVisible(false);
        HorizontalLayout filtros=new HorizontalLayout(desde,hasta,estado);filtros.setWidthFull();filtros.setFlexGrow(1,desde,hasta,estado);
        Div cuerpo=new Div(buscar,filtros,cargando,grid,paginacion);cuerpo.addClassName("cc-load-quote-body");add(cuerpo);
        getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->close()));
        addOpenedChangeListener(e->{if(e.isOpened()){paginacion.reset();cargar();buscar.focus();}});
    }

    private void configurarGrid() {
        grid.addColumn(CotizacionDto::numero).setHeader("Cotización").setWidth("145px").setFlexGrow(0);
        grid.addColumn(c->FECHA.format(c.fecha())).setHeader("Fecha").setWidth("120px").setFlexGrow(0);
        grid.addColumn(CotizacionDto::clienteNombre).setHeader("Cliente").setFlexGrow(1);
        grid.addColumn(c->dinero(c.total())).setHeader("Total").setWidth("145px").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(c->estado(c.estado())).setHeader("Estado").setWidth("125px").setFlexGrow(0);
        grid.addComponentColumn(c->new AppActionButton(ActionType.RECOVER,ButtonSize.GRID_ACTION,
                "Cargar cotización",e->seleccionar.accept(c.id()))).setHeader("Acc.").setWidth("44px").setFlexGrow(0).setResizable(false);
        grid.setAllRowsVisible(true);grid.setEmptyStateText("No se encontraron cotizaciones.");
        grid.addClassName("cc-load-quote-grid");
    }

    private void cargar() {
        if(consultando)return;consultando=true;cargando.setVisible(true);grid.setEnabled(false);
        try{var pagina=service.buscarParaPos(buscar.getValue(),desde.getValue(),hasta.getValue(),estado.getValue(),paginacion.currentRequest().page());
            grid.setItems(pagina.getContent());paginacion.setTotal(pagina.getTotalElements());}
        catch(RuntimeException ex){grid.setItems(List.of());paginacion.setTotal(0);UiErrorHandler.show(ex,"No fue posible consultar las cotizaciones.");}
        finally{grid.setEnabled(true);cargando.setVisible(false);consultando=false;}
    }

    private static String estado(EstadoCotizacion estado){return estado==EstadoCotizacion.ACEPTADA?"ACEPTADA":"ENVIADA";}
    private static String dinero(BigDecimal valor){return PosNumberFormatter.money(valor);}
}
