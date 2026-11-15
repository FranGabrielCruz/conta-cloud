package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.AsientoContablePdfService;
import com.citacloud.springboot.contacloud.app.services.AsientoContableService;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
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
import java.util.*;

@Route(value="asientos",layout=MainLayout.class)
@PageTitle("Asientos contables | ContaCloud")
@PermitAll
public class AsientosContablesView extends VerticalLayout implements BeforeEnterObserver, AfterNavigationObserver {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm xxx");
    private final AsientoContableService servicio;
    private final AsientoContablePdfService pdf;
    private final ModuleAuthorization autorizacion;
    private final AppGrid<Fila> tabla=new AppGrid<>(Fila.class);
    private final AppPagination pagina=new AppPagination(p->cargar());
    private final DatePicker desde=fecha("Desde");
    private final DatePicker hasta=fecha("Hasta");
    private final ComboBox<Tipo> tipo=combo("Tipo",Tipo::etiqueta,List.of(Tipo.values()));
    private final ComboBox<Estado> estado=combo("Estado",Estado::etiqueta,List.of(Estado.values()));
    private final ComboBox<Origen> origen=combo("Origen",Origen::etiqueta,List.of(Origen.values()));
    private final TextField buscar=texto("Buscar",150);
    private final AppActionButton nuevo;
    private UUID asientoSolicitado;

    public AsientosContablesView(AsientoContableService servicio,AsientoContablePdfService pdf,ModuleAuthorization autorizacion) {
        this.servicio=servicio;
        this.pdf=pdf;
        this.autorizacion=autorizacion;
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        nuevo=accion(ActionType.NEW,"Nuevo asiento",()->ejecutar("No fue posible abrir el formulario.",()->formulario(null,null)));
        nuevo.setVisible(false);
        buscar.setPlaceholder("Número, concepto, referencia o documento origen");
        buscar.setValueChangeMode(ValueChangeMode.LAZY);
        buscar.setClearButtonVisible(true);
        buscar.addClassName("cc-maintenance-search");
        var filtros=new Div(desde,hasta,tipo,estado,origen,buscar);
        filtros.addClassNames("cc-maintenance-filters","cc-journal-filters");
        for(var campo:List.<HasValue<?,?>>of(desde,hasta,tipo,estado,origen,buscar))
            campo.addValueChangeListener(e->{if(e.isFromClient()) reiniciar();});
        tabla.addColumn(Fila::numero).setHeader("Número").setAutoWidth(true);
        tabla.addColumn(f->FECHA.format(f.fecha())).setHeader("Fecha").setAutoWidth(true);
        tabla.addColumn(Fila::concepto).setHeader("Concepto").setFlexGrow(3);
        tabla.addColumn(f->f.origen().etiqueta()).setHeader("Origen").setAutoWidth(true);
        tabla.addColumn(f->f.moneda()+" "+dinero(f.total().debito())).setHeader("Débitos").setTextAlign(ColumnTextAlign.END);
        tabla.addColumn(f->f.moneda()+" "+dinero(f.total().credito())).setHeader("Créditos").setTextAlign(ColumnTextAlign.END);
        tabla.addComponentColumn(f->badge(f.estado())).setHeader("Estado").setAutoWidth(true);
        tabla.addComponentColumn(f->new AppActionButton(ActionType.VIEW,ButtonSize.GRID_ACTION,"Ver asiento",e->ver(f.id())))
            .setHeader("Acc.").setAutoWidth(true).setFlexGrow(0);
        tabla.setEmptyStateText("No se encontraron asientos.");
        add(new AppPageHeader("ASIENTOS CONTABLES","Consulta y registra movimientos contables.",nuevo),filtros,tabla,pagina);
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if(!autorizacion.canAccess("CONTABILIDAD","asientos.ver")) {
            event.rerouteTo(DashboardView.class);
            return;
        }
        nuevo.setVisible(puede("crear"));
        cargar();
        asientoSolicitado=event.getLocation().getQueryParameters().getSingleParameter("id").map(valor->{
            try { return UUID.fromString(valor); }
            catch(IllegalArgumentException ex) { return null; }
        }).orElse(null);
    }

    @Override public void afterNavigation(AfterNavigationEvent event) {
        var id=asientoSolicitado;
        asientoSolicitado=null;
        if(id!=null) ver(id);
    }

    private void reiniciar() { pagina.reset(); cargar(); }

    private void cargar() {
        ejecutar("No fue posible consultar los asientos.",()->{
            var p=pagina.currentRequest();
            var f=new Filtro(desde.getValue(),hasta.getValue(),tipo.getValue(),estado.getValue(),origen.getValue(),buscar.getValue());
            var r=servicio.listar(f,p.page(),p.size());
            tabla.setItems(r.getContent());
            pagina.setTotal(r.getTotalElements());
        });
    }

    private void ver(UUID id) {
        ejecutar("No fue posible consultar el asiento.",()->{
            var d=servicio.detalle(id);
            var f=d.fila();
            var modal=dialogo("ASIENTO CONTABLE — "+f.numero());
            var general=seccion("INFORMACIÓN GENERAL").field("Número",f.numero()).field("Fecha",FECHA.format(f.fecha()))
                .badge("Estado",badge(f.estado())).field("Tipo",f.tipo().etiqueta()).field("Origen",f.origen().etiqueta())
                .field("Moneda",f.moneda()).field("Referencia",d.referencia()).field("Documento origen",d.fuente()==null?null:d.fuente().numero())
                .field("Creado por",d.creador());
            if(d.numeroOriginal()!=null) general.field("Asiento original",d.numeroOriginal());
            if(d.numeroReversion()!=null) general.field("Asiento de reversión",d.numeroReversion());
            var lineas=new AppGrid<Linea>(Linea.class);
            lineas.addColumn(l->l.cuenta().etiqueta()).setHeader("Cuenta").setFlexGrow(3);
            lineas.addColumn(Linea::descripcion).setHeader("Descripción").setFlexGrow(2);
            lineas.addColumn(l->dinero(l.debito())).setHeader("Débito").setTextAlign(ColumnTextAlign.END);
            lineas.addColumn(l->dinero(l.credito())).setHeader("Crédito").setTextAlign(ColumnTextAlign.END);
            lineas.setItems(d.lineas());
            lineas.setEmptyStateText("Sin movimientos.");
            modal.add(general,textoCompleto("Concepto",f.concepto()));
            if(d.observacion()!=null) modal.add(textoCompleto("Observación",d.observacion()));
            if(d.motivo()!=null) modal.add(textoCompleto("Motivo de reversión",d.motivo()));
            modal.add(new H3("MOVIMIENTOS"),lineas,resumen(f.total(),f.moneda()),
                seccion("AUDITORÍA").field("Creado",HORA.format(d.creado()))
                    .field("Contabilizado",d.contabilizado()==null?null:HORA.format(d.contabilizado()))
                    .field("Contabilizado por",d.contabilizador()));
            if(f.estado()==Estado.BORRADOR && f.origen()==Origen.MANUAL) {
                if(puede("editar")) modal.getFooter().add(accion(ActionType.EDIT,"Editar borrador",()->
                    ejecutar("No fue posible editar el asiento.",()->formulario(d,modal))));
                if(puede("contabilizar")) modal.getFooter().add(accion(ActionType.ACCEPT,"Contabilizar",()->{
                    var clave=UUID.randomUUID();
                    confirmar("CONTABILIZAR ASIENTO","Después de contabilizar, el asiento queda protegido.",
                        ()->servicio.contabilizar(id,d.version(),clave),()->actualizar(modal,id,"Asiento contabilizado."));
                }));
            }
            if(puede("pdf")) modal.getFooter().add(AppActionButton.verPdf(e->
                ejecutar("No fue posible abrir el PDF del asiento.",()->PdfEnPestana.abrir(pdf.generar(id)))));
            if(d.reversible() && puede("reversar")) modal.getFooter().add(accion(ActionType.REVERSE,"Reversar",()->
                ejecutar("No fue posible preparar la reversión.",()->reversar(d,modal))));
            modal.getFooter().add(accion(ActionType.CLOSE,"Cerrar",modal::close));
            modal.open();
        });
    }

    private void formulario(Detalle previo,Dialog origen) {
        var modal=construirFormulario(previo,origen);
        if(origen!=null) origen.close();
        modal.open();
    }

    Dialog construirFormulario(Detalle previo,Dialog origen) {
        var modal=dialogo(previo==null?"NUEVO ASIENTO":"EDITAR BORRADOR — "+previo.fila().numero());
        modal.addClassName("cc-journal-editor");
        modal.setWidth("min(95vw, 1440px)");
        var fecha=fecha("Fecha");
        fecha.setValue(previo==null?servicio.hoy():previo.fila().fecha());
        fecha.setRequiredIndicatorVisible(true);
        var tipo=combo("Tipo",Tipo::etiqueta,List.of(Tipo.DIARIO,Tipo.APERTURA,Tipo.AJUSTE));
        tipo.setValue(previo==null?Tipo.DIARIO:previo.fila().tipo());
        tipo.setRequiredIndicatorVisible(true);
        tipo.setClearButtonVisible(false);
        var moneda=servicio.monedaBase();
        var monedaVista=combo("Moneda",Moneda::codigo,List.of(moneda));
        monedaVista.setValue(moneda);
        monedaVista.setClearButtonVisible(false);
        monedaVista.setRequiredIndicatorVisible(true);
        var concepto=texto("Concepto",500);
        concepto.setRequiredIndicatorVisible(true);
        concepto.setValueChangeMode(ValueChangeMode.EAGER);
        var referencia=texto("Referencia",150);
        var notas=area("Observación",2000);
        notas.addClassName("cc-journal-notes");
        var debitos=new Span();
        var creditos=new Span();
        var diferencia=new Span();
        var resumen=new Div(datoCompacto("Débitos",debitos),datoCompacto("Créditos",creditos),datoCompacto("Diferencia",diferencia));
        resumen.addClassName("cc-journal-totals");
        resumen.getElement().setAttribute("aria-live","polite");
        var movimientos=new Div();
        movimientos.addClassName("cc-journal-movements");
        movimientos.getElement().setAttribute("role","region");
        movimientos.getElement().setAttribute("aria-label","Movimientos contables");
        movimientos.getElement().setAttribute("tabindex","0");
        var columnas=new Div(new Span("Cuenta"),new Span("Descripción"),new Span("Débito"),new Span("Crédito"),new Span("Acc."));
        columnas.addClassNames("cc-journal-line","cc-journal-columns");
        movimientos.add(columnas);
        var filas=new ArrayList<EditorLinea>();
        var botonPost=new AppActionButton(ActionType.ACCEPT,ButtonSize.MAIN,"Contabilizar",null);
        java.util.function.BooleanSupplier cabeceraCompleta=()->fecha.getValue()!=null && tipo.getValue()!=null
            && monedaVista.getValue()!=null && !concepto.getValue().isBlank();
        Runnable totales=()->{
            var lineas=filas.stream().filter(l->!l.vacia()).toList();
            var total=AsientoContableService.totales(lineas.stream().map(EditorLinea::entrada).toList());
            boolean cuadrado=total.diferencia().signum()==0;
            debitos.setText(dinero(total.debito()));
            creditos.setText(dinero(total.credito()));
            diferencia.setText(dinero(total.diferencia())+(cuadrado?" ✓":" · Descuadrado"));
            diferencia.getElement().getThemeList().clear();
            diferencia.getElement().getThemeList().add("badge");
            diferencia.getElement().getThemeList().add(cuadrado?"success":"error");
            botonPost.setEnabled(cabeceraCompleta.getAsBoolean() && cuadrado && lineas.size()>=2
                && lineas.stream().allMatch(l->l.cuenta.getValue()!=null && l.errorImporte()==null
                    && (positivo(l.debito.getValue()) || positivo(l.credito.getValue()))));
        };
        if(previo!=null) {
            concepto.setValue(previo.fila().concepto());
            referencia.setValue(Objects.toString(previo.referencia(),""));
            notas.setValue(Objects.toString(previo.observacion(),""));
            previo.lineas().forEach(l->agregarLinea(movimientos,filas,l,totales,false));
        } else {
            agregarLinea(movimientos,filas,null,totales,false);
            agregarLinea(movimientos,filas,null,totales,false);
        }
        fecha.addValueChangeListener(e->{if(!fecha.isEmpty()) fecha.setInvalid(false);totales.run();});
        tipo.addValueChangeListener(e->{if(!tipo.isEmpty()) tipo.setInvalid(false);totales.run();});
        monedaVista.addValueChangeListener(e->{if(!monedaVista.isEmpty()) monedaVista.setInvalid(false);totales.run();});
        concepto.addValueChangeListener(e->{if(!concepto.getValue().isBlank()) concepto.setInvalid(false);totales.run();});
        totales.run();
        var encabezado=new Div(new H3("MOVIMIENTOS"),accion(ActionType.NEW,"Agregar línea",()->
            agregarLinea(movimientos,filas,null,totales,true)));
        encabezado.addClassName("cc-detail-heading");
        var generales=new Div(fecha,tipo,monedaVista,datoCompacto("Origen",new Span("Manual")));
        generales.addClassName("cc-journal-general");
        var referencias=new Div(concepto,referencia);
        referencias.addClassName("cc-journal-reference");
        modal.add(generales,referencias,notas,encabezado,movimientos,resumen);
        var solicitud=UUID.randomUUID();
        var contabilizacion=UUID.randomUUID();
        java.util.function.Supplier<Entrada> entrada=()->new Entrada(fecha.getValue(),tipo.getValue(),monedaVista.getValue().id(),
            concepto.getValue(),referencia.getValue(),notas.getValue(),filas.stream().filter(l->!l.vacia()).map(EditorLinea::entrada).toList());
        UUID id=previo==null?null:previo.fila().id();
        long version=previo==null?0:previo.version();
        modal.getFooter().add(accion(ActionType.SAVE,"Guardar",()->ejecutar("No fue posible guardar el borrador.",()->{
            if(!validarFormulario(fecha,tipo,monedaVista,concepto,filas)) return;
            UUID guardado=id;
            if(id==null) guardado=servicio.crear(entrada.get(),solicitud);
            else servicio.editar(id,version,entrada.get());
            actualizar(modal,guardado,"Borrador guardado.");
        })));
        botonPost.addClickListener(e->{
            if(!validarFormulario(fecha,tipo,monedaVista,concepto,filas)) return;
            var valor=entrada.get();
            var guardado=new UUID[1];
            confirmar("CONTABILIZAR ASIENTO","Confirma que los débitos y créditos son correctos.",
                ()->guardado[0]=servicio.guardarYContabilizar(id,version,valor,contabilizacion),
                ()->actualizar(modal,guardado[0],"Asiento contabilizado."));
        });
        if(puede("contabilizar")) modal.getFooter().add(botonPost);
        modal.getFooter().add(accion(ActionType.CANCEL,"Cancelar",()->{modal.close();if(origen!=null) origen.open();}));
        return modal;
    }

    private static Div datoCompacto(String etiqueta,Span valor) {
        var rotulo=new Span(etiqueta);
        rotulo.addClassName("cc-detail-label");
        valor.addClassName("cc-detail-value");
        var d=new Div(rotulo,valor);
        d.addClassName("cc-journal-datum");
        return d;
    }

    private boolean validarFormulario(DatePicker fecha,ComboBox<Tipo> tipo,ComboBox<Moneda> moneda,TextField concepto,
                                      List<EditorLinea> filas) {
        fecha.setInvalid(fecha.isEmpty());
        tipo.setInvalid(tipo.isEmpty());
        moneda.setInvalid(moneda.isEmpty());
        concepto.setInvalid(concepto.getValue().isBlank());
        fecha.setErrorMessage("Indica la fecha.");
        tipo.setErrorMessage("Selecciona el tipo.");
        moneda.setErrorMessage("Selecciona la moneda.");
        concepto.setErrorMessage("Indica el concepto.");
        if(fecha.isInvalid()) { fecha.focus(); return false; }
        if(tipo.isInvalid()) { tipo.focus(); return false; }
        if(moneda.isInvalid()) { moneda.focus(); return false; }
        if(concepto.isInvalid()) { concepto.focus(); return false; }
        for(var fila:filas) {
            if(fila.vacia()) continue;
            if(fila.cuenta.isEmpty()) {
                fila.cuenta.setErrorMessage("Selecciona la cuenta.");
                fila.cuenta.setInvalid(true);
                fila.cuenta.focus();
                return false;
            }
            if(fila.errorImporte()!=null) {
                fila.validarImportes();
                (fila.credito.isInvalid()&&!fila.debito.isInvalid()?fila.credito:fila.debito).focus();
                return false;
            }
        }
        return true;
    }

    private void agregarLinea(Div movimientos,List<EditorLinea> filas,Linea previa,Runnable resumen,boolean enfocar) {
        if(filas.size()>=500) { Notification.show("El asiento admite hasta 500 líneas."); return; }
        var fila=new EditorLinea(previa,resumen);
        fila.add(new AppActionButton(ActionType.DELETE,ButtonSize.GRID_ACTION,"Eliminar línea",e->{
            movimientos.remove(fila);
            filas.remove(fila);
            resumen.run();
        }));
        filas.add(fila);
        movimientos.add(fila);
        if(enfocar) fila.cuenta.focus();
    }

    private final class EditorLinea extends Div {
        private final ComboBox<Cuenta> cuenta=combo("",Cuenta::etiqueta,List.of());
        private final TextField descripcion=texto("",500);
        private final BigDecimalField debito=importe("Débito");
        private final BigDecimalField credito=importe("Crédito");

        EditorLinea(Linea previa,Runnable resumen) {
            addClassName("cc-journal-line");
            cuenta.setAriaLabel("Cuenta");
            descripcion.setAriaLabel("Descripción");
            cuenta.setItems(q->servicio.cuentas(q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
            if(previa!=null) {
                cuenta.setValue(previa.cuenta());
                descripcion.setValue(Objects.toString(previa.descripcion(),""));
                debito.setValue(previa.debito());
                credito.setValue(previa.credito());
            }
            debito.addValueChangeListener(e->{validarImportes();resumen.run();});
            credito.addValueChangeListener(e->{validarImportes();resumen.run();});
            cuenta.addValueChangeListener(e->{cuenta.setInvalid(false);resumen.run();});
            descripcion.addValueChangeListener(e->resumen.run());
            add(cuenta,descripcion,debito,credito);
        }

        Movimiento entrada() {
            return new Movimiento(cuenta.isEmpty()?null:cuenta.getValue().id(),descripcion.getValue(),debito.getValue(),credito.getValue());
        }

        boolean vacia() {
            return cuenta.isEmpty()&&descripcion.isEmpty()&&cero(debito.getValue())&&cero(credito.getValue());
        }

        String errorImporte() {
            if(positivo(debito.getValue())&&positivo(credito.getValue())) return "Introduce débito o crédito en esta línea.";
            if(!importeValido(debito.getValue())||!importeValido(credito.getValue()))
                return "Usa un importe no negativo de hasta dos decimales.";
            return null;
        }

        void validarImportes() {
            String error=Objects.toString(errorImporte(),"");
            boolean ambos=positivo(debito.getValue())&&positivo(credito.getValue());
            debito.setErrorMessage(error);
            credito.setErrorMessage(error);
            debito.setInvalid(ambos||!importeValido(debito.getValue()));
            credito.setInvalid(!importeValido(credito.getValue()));
        }
    }

    private void reversar(Detalle d,Dialog origen) {
        var modal=dialogo("REVERSAR "+d.fila().numero());
        var fecha=fecha("Fecha de reversión");
        fecha.setValue(servicio.hoy().isBefore(d.fila().fecha())?d.fila().fecha():servicio.hoy());
        var motivo=area("Motivo",1000);
        motivo.setRequiredIndicatorVisible(true);
        modal.add(new Span("La reversión crea otro asiento con los débitos y créditos invertidos."),fecha,motivo);
        var clave=UUID.randomUUID();
        modal.getFooter().add(accion(ActionType.REVERSE,"Reversar",()->ejecutar("No fue posible reversar el asiento.",()->{
            var id=servicio.reversar(d.fila().id(),d.version(),fecha.getValue(),motivo.getValue(),clave);
            origen.close();
            actualizar(modal,id,"Reversión contabilizada.");
        })),accion(ActionType.CANCEL,"Cancelar",modal::close));
        modal.open();
    }

    private void confirmar(String titulo,String mensaje,Runnable operacion,Runnable exito) {
        var d=dialogo(titulo);
        d.add(new Span(mensaje));
        d.getFooter().add(accion(ActionType.ACCEPT,"Contabilizar",()->ejecutar("No fue posible contabilizar el asiento.",()->{
            operacion.run();
            d.close();
            exito.run();
        })),accion(ActionType.CANCEL,"Cancelar",d::close));
        d.open();
    }

    private void actualizar(Dialog modal,UUID id,String mensaje) {
        modal.close();
        Notification.show(mensaje);
        reiniciar();
        ver(id);
    }

    private static AppDetailSection resumen(Total total,String moneda) {
        return seccion("TOTALES").field("Débitos",moneda+" "+dinero(total.debito()))
            .field("Créditos",moneda+" "+dinero(total.credito()))
            .badge("Diferencia",new AppStatusBadge(moneda+" "+dinero(total.diferencia()),total.diferencia().signum()==0
                ?AppStatusBadge.Variant.SUCCESS:AppStatusBadge.Variant.DANGER));
    }

    private static AppStatusBadge badge(Estado estado) {
        return new AppStatusBadge(estado.etiqueta(),switch(estado){
            case BORRADOR->AppStatusBadge.Variant.NEUTRAL;
            case CONTABILIZADO->AppStatusBadge.Variant.SUCCESS;
            case REVERSADO->AppStatusBadge.Variant.WARNING;
        });
    }

    private static AppDetailSection textoCompleto(String etiqueta,String valor) {
        var d=new AppDetailSection(etiqueta.toUpperCase(Locale.ROOT)).field(etiqueta,valor);
        d.addClassName("cc-detail-full");
        return d;
    }

    private static AppDetailSection seccion(String titulo) {
        var d=new AppDetailSection(titulo);
        d.addClassName("cc-detail-three");
        return d;
    }

    private static Dialog dialogo(String titulo) {
        var d=new Dialog();
        d.setHeaderTitle(titulo);
        d.setWidth("min(1070px,96vw)");
        d.setMaxHeight("92vh");
        return d;
    }

    private static DatePicker fecha(String titulo) {
        var d=new DatePicker(titulo);
        d.setLocale(Locale.forLanguageTag("es-DO"));
        d.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        d.setWidthFull();
        return d;
    }

    private static TextField texto(String titulo,int max) {
        var d=new TextField(titulo);
        d.setMaxLength(max);
        d.setWidthFull();
        return d;
    }

    private static TextArea area(String titulo,int max) {
        var d=new TextArea(titulo);
        d.setMaxLength(max);
        d.setWidthFull();
        return d;
    }

    private static <T> ComboBox<T> combo(String titulo,SerializableFunction<T,String> etiqueta,List<T> items) {
        var c=new ComboBox<T>(titulo);
        c.setItems(items);
        c.setItemLabelGenerator(etiqueta::apply);
        c.setClearButtonVisible(true);
        c.setWidthFull();
        return c;
    }

    private static BigDecimalField importe(String titulo) {
        var campo=new BigDecimalField();
        campo.setAriaLabel(titulo);
        campo.setValueChangeMode(ValueChangeMode.EAGER);
        campo.setValue(BigDecimal.ZERO);
        campo.setWidthFull();
        campo.addAttachListener(e->{if(campo.getValue()!=null) PosNumberFormatter.showMoney(campo);});
        campo.addBlurListener(e->{if(campo.getValue()!=null) PosNumberFormatter.showMoney(campo);});
        campo.addFocusListener(e->{if(campo.getValue()!=null)
            campo.getElement().executeJs("if(this.inputElement)this.inputElement.value=$0",campo.getValue().toPlainString());});
        return campo;
    }

    private static boolean cero(BigDecimal importe) { return importe==null||importe.signum()==0; }
    private static boolean positivo(BigDecimal importe) { return importe!=null&&importe.signum()>0; }
    private static boolean importeValido(BigDecimal importe) {
        return importe==null || importe.signum()>=0 && importe.stripTrailingZeros().scale()<=2
            && importe.compareTo(new BigDecimal("100000000000000000"))<0;
    }
    private static String dinero(BigDecimal importe) { return PosNumberFormatter.money(importe); }
    private static boolean puede(String accion) { return TenantContext.principalActual().permisos().contains("asientos."+accion); }
    private static AppActionButton accion(ActionType tipo,String titulo,Runnable operacion) {
        return new AppActionButton(tipo,ButtonSize.MAIN,titulo,e->operacion.run());
    }
    private static void ejecutar(String mensaje,Runnable operacion) {
        try {operacion.run();} catch(RuntimeException ex) {UiErrorHandler.show(ex,mensaje);}
    }
}
