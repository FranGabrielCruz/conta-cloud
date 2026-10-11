package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value="saldos-cuentas",layout=MainLayout.class)
@PageTitle("Saldos de cuentas | ContaCloud")
@PermitAll
public class SaldosCuentasView extends VerticalLayout implements BeforeEnterObserver {
    private final SaldosCuentasService service;private final EmpresaModuloService modulos;
    private final TextField buscar=new TextField();private final DatePicker al=new DatePicker("Al");
    private final Select<String> tipo=new Select<>();private final Select<String> estado=new Select<>();
    private final ComboBox<SaldosCatalogosDto.Opcion> moneda=new ComboBox<>("Moneda de cuenta");
    private final ComboBox<SaldosCatalogosDto.Opcion> presentacion=new ComboBox<>("Moneda de visualización");
    private final ComboBox<SaldosCatalogosDto.Opcion> sucursal=new ComboBox<>("Sucursal");
    private final Div resumen=new Div();private final Div aviso=new Div();private final Div resumenError=new Div();
    private final Span actualizado=new Span();
    private final AppGrid<SaldoCuentaDto> gridBancos=new AppGrid<>(SaldoCuentaDto.class);
    private final AppGrid<SaldoCuentaDto> gridCajas=new AppGrid<>(SaldoCuentaDto.class);
    private final H2 tituloBancos=new H2("CUENTAS BANCARIAS");private final H2 tituloCajas=new H2("CAJAS");
    private AppPagination paginasBancos,paginasCajas;private Button refrescar;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public SaldosCuentasView(SaldosCuentasService service,EmpresaModuloService modulos){
        this.service=service;this.modulos=modulos;addClassName("cc-page");setPadding(false);setSpacing(false);setWidthFull();
        refrescar=new Button(VaadinIcon.REFRESH.create(),e->cargarTodo());refrescar.setTooltipText("Actualizar saldos");
        refrescar.getElement().setAttribute("aria-label","Actualizar saldos");refrescar.addClassName("cc-balance-refresh");
        add(new AppPageHeader("SALDOS DE CUENTAS","Consulta los saldos de cajas y cuentas bancarias a una fecha determinada."));
        configurarFiltros();configurarGrids();paginasBancos=new AppPagination(r->cargarBancos());paginasCajas=new AppPagination(r->cargarCajas());
        resumen.addClassName("cc-balance-summary");aviso.addClassName("cc-balance-warning");aviso.setVisible(false);
        actualizado.addClassName("cc-balance-updated");actualizado.setVisible(false);resumenError.addClassName("cc-balance-error");resumenError.setVisible(false);
        tituloBancos.addClassName("cc-section-title");tituloCajas.addClassName("cc-section-title");
        add(filtros(),resumen,resumenError,actualizado,aviso,tituloBancos,gridBancos,paginasBancos,tituloCajas,gridCajas,paginasCajas);
    }
    @Override public void beforeEnter(BeforeEnterEvent event){
        if(!modulos.habilitado("CAJA_BANCOS")||!puede("saldos_cuentas.ver")){Notification.show("No tienes permiso para acceder a esta opción.");event.rerouteTo(DashboardView.class);return;}
        try{var c=service.catalogos();moneda.setItems(c.monedas());presentacion.setItems(c.monedas());sucursal.setItems(c.sucursales());
            c.monedas().stream().filter(x->x.id().equals(c.monedaBaseId())).findFirst().ifPresent(presentacion::setValue);cargarTodo();}
        catch(RuntimeException ex){error("No fue posible cargar los saldos de cuentas.");}
    }
    private void configurarFiltros(){
        buscar.setLabel("Buscar");buscar.setPlaceholder("Cuenta, banco o caja...");buscar.setClearButtonVisible(true);buscar.setValueChangeMode(ValueChangeMode.LAZY);buscar.setValueChangeTimeout(400);
        al.setLocale(new Locale("es","DO"));al.setValue(LocalDate.now());al.setRequired(true);
        tipo.setLabel("Tipo de cuenta");tipo.setItems("Todas","Bancos","Cajas");tipo.setValue("Todas");
        estado.setLabel("Estado");estado.setItems("Activas","Inactivas","Todas");estado.setValue("Activas");
        for(var cb:List.of(moneda,presentacion,sucursal)){cb.setItemLabelGenerator(SaldosCatalogosDto.Opcion::nombre);cb.setClearButtonVisible(true);cb.setPlaceholder("Todas");}
        presentacion.setClearButtonVisible(false);presentacion.setPlaceholder("Moneda base");
        buscar.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});al.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        tipo.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});estado.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
        moneda.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});presentacion.addValueChangeListener(e->{if(e.isFromClient())cargarResumen();});
        sucursal.addValueChangeListener(e->{if(e.isFromClient())reiniciar();});
    }
    private Component filtros(){HorizontalLayout f=new HorizontalLayout(buscar,al,tipo,moneda,estado,sucursal,presentacion,refrescar);f.addClassName("cc-filter-bar");f.setAlignItems(Alignment.END);f.setWidthFull();return f;}
    private void configurarGrids(){
        configurarGrid(gridBancos,false);configurarGrid(gridCajas,true);
        gridBancos.setEmptyStateText("No existen cuentas bancarias registradas.");gridCajas.setEmptyStateText("No existen cajas registradas.");
    }
    private void configurarGrid(AppGrid<SaldoCuentaDto> grid,boolean branch){
        grid.addColumn(SaldoCuentaDto::cuenta).setHeader("Cuenta").setAutoWidth(true).setFlexGrow(1);
        if(branch)grid.addColumn(x->nvl(x.sucursal())).setHeader("Sucursal").setAutoWidth(true);
        grid.addColumn(SaldoCuentaDto::moneda).setHeader("Moneda").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(x->money(x.saldoInicial())).setHeader("Saldo inicial").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(x->money(x.entradas())).setHeader("Entradas").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(x->money(x.salidas())).setHeader("Salidas").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addColumn(x->moneyWithSymbol(x.saldoActual(),x.simbolo(),x.moneda())).setHeader("Saldo actual").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        grid.addComponentColumn(this::accion).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
    }
    private Component accion(SaldoCuentaDto row){HorizontalLayout l=new HorizontalLayout();l.addClassName("cc-grid-actions");l.setPadding(false);l.setSpacing(false);
        if(puede("saldos_cuentas.ver_movimientos"))l.add(new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver movimientos",e->detalle(row)));return l;}
    private void cargarTodo(){if(al.getValue()==null){error("Selecciona la fecha de corte.");return;}cargarResumen();cargarBancos();cargarCajas();aplicarVisibilidad();}
    private void cargarBancos(){boolean show=!"Cajas".equals(tipo.getValue())&&puede("saldos_cuentas.ver_bancaria");if(!show){gridBancos.setItems(List.of());paginasBancos.setTotal(0);return;}
        try{var r=paginasBancos.currentRequest();var p=service.bancos(al.getValue(),buscar.getValue(),id(moneda),activo(),r.page(),r.size());gridBancos.setItems(p.getContent());paginasBancos.setTotal(p.getTotalElements());}catch(RuntimeException ex){error("No fue posible cargar las cuentas bancarias.");}}
    private void cargarCajas(){if("Bancos".equals(tipo.getValue())){gridCajas.setItems(List.of());paginasCajas.setTotal(0);return;}
        try{var r=paginasCajas.currentRequest();var p=service.cajas(al.getValue(),buscar.getValue(),id(moneda),activo(),id(sucursal),r.page(),r.size());gridCajas.setItems(p.getContent());paginasCajas.setTotal(p.getTotalElements());}catch(RuntimeException ex){error("No fue posible cargar las cajas.");}}
    private void cargarResumen(){if(al.getValue()==null)return;mostrarCargaResumen();refrescar.setEnabled(false);try{boolean bank=!"Cajas".equals(tipo.getValue())&&puede("saldos_cuentas.ver_bancaria");boolean cash=!"Bancos".equals(tipo.getValue());
        var r=service.resumen(al.getValue(),buscar.getValue(),id(moneda),activo(),id(sucursal),id(presentacion),bank,cash);resumen.removeAll();
        String helper=r.conversionAplicada()&&r.equivalente()?"Equivalente en "+r.moneda():null;
        resumen.add(totalCard("TOTAL EN BANCOS",display(r.bancos(),r.simbolo(),r.moneda()),r.bancos(),helper,
                "Suma de los saldos de las cuentas bancarias incluidas en la consulta."),
            totalCard("TOTAL EN CAJAS",display(r.cajas(),r.simbolo(),r.moneda()),r.cajas(),helper,
                "Suma de los saldos de las cajas incluidas en la consulta."),
            totalCard("TOTAL GENERAL",display(r.general(),r.simbolo(),r.moneda()),r.general(),helper,
                "Total de bancos y cajas expresado en la moneda de visualización."));
        resumen.setVisible(true);resumenError.setVisible(false);actualizado.setText("Actualizado: "+r.actualizadoEn().format(TIME));actualizado.setVisible(true);
        if(r.equivalente()){aviso.setVisible(false);}else{String separated=r.totalesPorMoneda().entrySet().stream().map(e->e.getKey()+" "+money(e.getValue())).reduce((a,b)->a+" · "+b).orElse("—");
            aviso.setText(String.join(" ",r.advertencias())+" Totales sin conversión: "+separated);aviso.setVisible(true);}refrescar.setTooltipText("Actualizado: "+r.actualizadoEn().format(TIME));
    }catch(RuntimeException ex){mostrarErrorResumen();error("No fue posible calcular el resumen de saldos.");}finally{refrescar.setEnabled(true);}}
    private void mostrarCargaResumen(){resumenError.setVisible(false);actualizado.setVisible(false);aviso.setVisible(false);resumen.removeAll();
        for(int i=0;i<3;i++){Div skeleton=new Div();skeleton.addClassNames("cc-balance-card","cc-balance-card-loading");skeleton.getElement().setAttribute("aria-label","Cargando saldo");resumen.add(skeleton);}resumen.setVisible(true);}
    private void mostrarErrorResumen(){resumen.setVisible(false);actualizado.setVisible(false);aviso.setVisible(false);resumenError.removeAll();
        Paragraph message=new Paragraph("No fue posible obtener los saldos.");Button retry=new Button("Reintentar",VaadinIcon.REFRESH.create(),e->cargarTodo());retry.getElement().getThemeList().add("primary");resumenError.add(message,retry);resumenError.setVisible(true);}
    private Div totalCard(String label,String value,BigDecimal amount,String helper,String tooltip){
        Span title=new Span(label);title.addClassName("cc-balance-card-title");Span total=new Span(value);total.addClassName("cc-balance-value");
        if(amount!=null&&amount.signum()<0)total.addClassName("cc-balance-negative");Div card=new Div(title,total);card.addClassName("cc-balance-card");
        if("TOTAL GENERAL".equals(label))card.addClassName("cc-balance-card-general");if(helper!=null){Span note=new Span(helper);note.addClassName("cc-balance-equivalent");card.add(note);}
        card.getElement().setProperty("title",tooltip);return card;}
    private Div detailCard(String label,String value){Span amount=new Span(value);amount.addClassName("cc-balance-value");Div d=new Div(new Span(label),amount);d.addClassName("cc-balance-card");return d;}
    private void aplicarVisibilidad(){boolean bank=!"Cajas".equals(tipo.getValue())&&puede("saldos_cuentas.ver_bancaria");boolean cash=!"Bancos".equals(tipo.getValue());tituloBancos.setVisible(bank);gridBancos.setVisible(bank);paginasBancos.setVisible(bank);tituloCajas.setVisible(cash);gridCajas.setVisible(cash);paginasCajas.setVisible(cash);}
    private void reiniciar(){if(paginasBancos==null)return;paginasBancos.reset();paginasCajas.reset();cargarTodo();}
    private void detalle(SaldoCuentaDto cuenta){
        Dialog d=new Dialog();d.setHeaderTitle("MOVIMIENTOS DE "+cuenta.cuenta().toUpperCase(Locale.ROOT));d.setWidth("min(1100px, 95vw)");
        Div info=new Div(detailCard("Cuenta",cuenta.cuenta()),detailCard("Moneda",cuenta.moneda()),detailCard("Sucursal",nvl(cuenta.sucursal())));info.addClassName("cc-balance-summary");
        Div cards=new Div(detailCard("Saldo inicial",money(cuenta.saldoInicial())),detailCard("Entradas",money(cuenta.entradas())),detailCard("Salidas",money(cuenta.salidas())),detailCard("Saldo actual",money(cuenta.saldoActual())));cards.addClassName("cc-balance-summary");
        TextField q=new TextField("Buscar");q.setPlaceholder("Documento o concepto...");q.setClearButtonVisible(true);q.setValueChangeMode(ValueChangeMode.LAZY);
        DatePicker desde=new DatePicker("Desde");desde.setLocale(new Locale("es","DO"));DatePicker hasta=new DatePicker("Hasta");hasta.setLocale(new Locale("es","DO"));hasta.setValue(al.getValue());
        Select<String> direction=new Select<>();direction.setLabel("Movimiento");direction.setItems("Todos","Entradas","Salidas");direction.setValue("Todos");
        HorizontalLayout filters=new HorizontalLayout(q,desde,hasta,direction);filters.addClassName("cc-filter-bar");filters.setWidthFull();filters.setAlignItems(Alignment.END);
        AppGrid<MovimientoSaldoDto> g=new AppGrid<>(MovimientoSaldoDto.class);g.addColumn(x->x.fecha().format(DATE)).setHeader("Fecha").setAutoWidth(true);
        g.addColumn(MovimientoSaldoDto::documento).setHeader("Documento").setAutoWidth(true);g.addColumn(MovimientoSaldoDto::concepto).setHeader("Concepto").setFlexGrow(1);
        g.addColumn(x->money(x.entrada())).setHeader("Entrada").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        g.addColumn(x->money(x.salida())).setHeader("Salida").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
        g.addColumn(x->money(x.saldo())).setHeader("Saldo").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);g.setEmptyStateText("No hay movimientos para los filtros seleccionados.");
        final AppPagination[] pager=new AppPagination[1];Runnable load=()->{try{var p=pager[0].currentRequest();TipoMovimientoFinanciero dir="Entradas".equals(direction.getValue())?TipoMovimientoFinanciero.INCOME:"Salidas".equals(direction.getValue())?TipoMovimientoFinanciero.EXPENSE:null;
            var page=service.movimientos(cuenta.tipo(),cuenta.id(),desde.getValue(),hasta.getValue(),q.getValue(),dir,p.page(),p.size());g.setItems(page.getContent());pager[0].setTotal(page.getTotalElements());}catch(RuntimeException ex){error("No fue posible cargar los movimientos.");}};
        pager[0]=new AppPagination(r->load.run());q.addValueChangeListener(e->{if(e.isFromClient()){pager[0].reset();load.run();}});desde.addValueChangeListener(e->{if(e.isFromClient()){pager[0].reset();load.run();}});hasta.addValueChangeListener(e->{if(e.isFromClient()){pager[0].reset();load.run();}});direction.addValueChangeListener(e->{if(e.isFromClient()){pager[0].reset();load.run();}});
        d.add(info,cards,filters,g,pager[0]);d.getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->d.close()));load.run();d.open();
    }
    private Boolean activo(){return "Activas".equals(estado.getValue())?true:"Inactivas".equals(estado.getValue())?false:null;}
    private static UUID id(ComboBox<SaldosCatalogosDto.Opcion> c){return c.getValue()==null?null:c.getValue().id();}
    private static String money(BigDecimal n){return new DecimalFormat("#,##0.00;-#,##0.00").format(n==null?BigDecimal.ZERO:n);}
    private static String display(BigDecimal n,String symbol,String currency){return n==null?"No consolidado":moneyWithSymbol(n,symbol,currency);}
    static String moneyWithSymbol(BigDecimal n,String symbol,String currency){BigDecimal value=n==null?BigDecimal.ZERO:n;String sign=value.signum()<0?"-":"";String prefix=symbol==null||symbol.isBlank()?currency:symbol;return sign+prefix+" "+money(value.abs());}
    private static String nvl(String s){return s==null||s.isBlank()?"—":s;}
    private static boolean puede(String p){return TenantContext.principalActual().permisos().contains(p);}
    private static void error(String text){Notification.show(text,5000,Notification.Position.BOTTOM_START);}
}
