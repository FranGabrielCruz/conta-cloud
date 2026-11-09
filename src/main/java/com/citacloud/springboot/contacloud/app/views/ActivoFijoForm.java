package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;
import com.citacloud.springboot.contacloud.app.services.ActivoFijoService;
import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

final class ActivoFijoForm extends Dialog {
    private final TextField nombre=texto("Nombre o descripción",180);
    private final ComboBox<Categoria> categoria=combo("Categoría",Categoria::nombre);
    private final TextField marca=texto("Marca",100);
    private final TextField modelo=texto("Modelo",100);
    private final TextField serie=texto("Número de serie",120);
    private final DatePicker fecha=fecha("Fecha de adquisición");
    private final BigDecimalField costo=dinero("Costo de adquisición");
    private final ComboBox<Moneda> moneda=combo("Moneda",Moneda::codigo);
    private final ComboBox<Opcion> proveedor=combo("Proveedor",Opcion::nombre);
    private final ComboBox<Factura> factura=combo("Factura registrada",Factura::numero);
    private final TextField referencia=texto("Referencia de compra",100);
    private final ComboBox<Opcion> sucursal=combo("Sucursal",Opcion::nombre);
    private final TextField ubicacion=texto("Ubicación",150);
    private final ComboBox<Opcion> responsable=combo("Responsable",Opcion::nombre);
    private final Checkbox depreciable=new Checkbox("¿Depreciable?");
    private final ComboBox<Metodo> metodo=combo("Método",Metodo::etiqueta);
    private final IntegerField vida=new IntegerField("Vida útil (meses)");
    private final BigDecimalField residual=dinero("Valor residual");
    private final DatePicker inicio=fecha("Inicio de depreciación");
    private final TextArea observacion=new TextArea("Observación");
    private final List<HasValue<?,?>> obligatorios=List.of(nombre,categoria,fecha,costo,moneda,sucursal);
    private final List<HasValue<?,?>> parametros=List.of(metodo,vida,residual,inicio);

    ActivoFijoForm(ActivoFijoService servicio, Registro actual, Consumer<Registro> alGuardar, Runnable alCancelar) {
        setHeaderTitle(actual==null?"NUEVO ACTIVO":"EDITAR ACTIVO "+actual.resumen().codigo());
        setWidth("min(920px, 96vw)");
        setMaxHeight("92vh");
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        var propietarioSerie=formularioAdministrativo();
        var idPropietario="asset-serial-form-"+UUID.randomUUID();
        propietarioSerie.setId(idPropietario);
        configurarSerie(idPropietario);
        var catalogos=servicio.formulario();
        categoria.setItems(catalogos.categorias());
        moneda.setItems(catalogos.monedas());
        proveedor.setItems(catalogos.proveedores());
        sucursal.setItems(catalogos.sucursales());
        responsable.setItems(catalogos.responsables());
        metodo.setItems(Metodo.values());
        factura.setPageSize(25);
        factura.setItems(q->proveedor.isEmpty()?java.util.stream.Stream.empty():servicio.facturas(proveedor.getValue().id(),
            q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
        proveedor.addValueChangeListener(e->{
            factura.clear();
            factura.setEnabled(e.getValue()!=null);
            factura.getDataProvider().refreshAll();
        });
        factura.setEnabled(false);
        vida.setMin(1);
        vida.setMax(1200);
        observacion.setMaxLength(1000);
        observacion.setWidthFull();
        observacion.setMinHeight("100px");
        var tipo=new TextField("Tipo","Tangible","");
        tipo.setReadOnly(true);
        tipo.setWidthFull();

        obligatorios.forEach(f->f.setRequiredIndicatorVisible(true));
        if (actual==null) {
            catalogos.monedas().stream().filter(Moneda::base).findFirst().ifPresent(moneda::setValue);
            if (catalogos.sucursales().size()==1) sucursal.setValue(catalogos.sucursales().getFirst());
        } else {
            cargar(actual,catalogos);
        }
        boolean bloqueado=actual!=null && actual.finanzasBloqueadas();
        if (bloqueado) {
            List.<HasValue<?,?>>of(fecha,costo,moneda,proveedor,factura,referencia,depreciable,metodo,vida,residual,inicio)
                .forEach(f->f.setReadOnly(true));
            costo.setHelperText("Protegido por movimientos financieros registrados.");
        }
        depreciable.addValueChangeListener(e->mostrarParametros());
        mostrarParametros();
        add(propietarioSerie,formularioAdministrativo(
            seccion("INFORMACIÓN GENERAL",nombre,categoria,tipo,marca,modelo,serie),
            seccion("ADQUISICIÓN",fecha,costo,moneda,proveedor,factura,referencia),
            seccion("UBICACIÓN Y ASIGNACIÓN",sucursal,ubicacion,responsable),
            seccion("DEPRECIACIÓN",depreciable,metodo,vida,residual,inicio),
            seccion("INFORMACIÓN ADICIONAL",observacion)));

        var guardar=new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",e->{
            Registro guardado;
            try {
                var datos=entrada();
                guardado=actual==null?servicio.crear(datos):servicio.editar(actual.resumen().id(),actual.version(),datos);
            } catch (RuntimeException ex) {
                UiErrorHandler.show(ex,"No fue posible guardar el activo.");
                return;
            }
            close();
            alGuardar.accept(guardado);
        });
        getFooter().add(guardar,new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",e->{
            close();
            alCancelar.run();
        }));
    }

    private void configurarSerie(String idPropietario) {
        serie.setId("fixed-asset-serial-number");
        serie.setAutocomplete(Autocomplete.OFF);
        serie.getElement().setAttribute("name","assetSerialNumber");
        // El input pertenece a un formulario propio desde que entra al DOM.
        // Así el navegador no lo clasifica junto con fechas e importes.
        serie.getElement().appendChild(new Element("input")
            .setAttribute("slot","input")
            .setAttribute("type","text")
            .setAttribute("inputmode","text")
            .setAttribute("name","assetSerialNumber")
            .setAttribute("autocomplete","off")
            .setAttribute("form",idPropietario));
    }

    private static HtmlContainer formularioAdministrativo(Component... campos) {
        var formulario=new HtmlContainer("form",campos);
        formulario.setWidthFull();
        formulario.getElement().setAttribute("autocomplete","off");
        formulario.getElement().addEventListener("submit",event->{}).preventDefault();
        return formulario;
    }

    private void cargar(Registro registro, Catalogos catalogos) {
        var d=registro.datos(); var a=d.adquisicion(); var s=d.asignacion(); var dep=d.depreciacion();
        nombre.setValue(d.nombre());
        catalogos.categorias().stream().filter(c->c.codigo().equals(d.categoria())).findFirst().ifPresent(categoria::setValue);
        marca.setValue(vacio(d.marca()));
        modelo.setValue(vacio(d.modelo()));
        serie.setValue(vacio(d.serie()));
        fecha.setValue(a.fecha());
        costo.setValue(a.costo());
        seleccionar(moneda,catalogos.monedas(),Moneda::id,new Moneda(a.moneda(),registro.resumen().moneda(),false));
        if (a.proveedor()!=null) seleccionar(proveedor,catalogos.proveedores(),Opcion::id,new Opcion(a.proveedor(),registro.proveedor()));
        if (a.factura()!=null) factura.setValue(new Factura(a.factura(),a.proveedor(),
            Objects.toString(registro.factura(),"Factura vinculada")));
        referencia.setValue(vacio(a.referencia()));
        seleccionar(sucursal,catalogos.sucursales(),Opcion::id,new Opcion(s.sucursal(),registro.destino().sucursal()));
        ubicacion.setValue(vacio(s.ubicacion()));
        if (s.responsable()!=null) seleccionar(responsable,catalogos.responsables(),Opcion::id,new Opcion(s.responsable(),registro.destino().responsable()));
        depreciable.setValue(dep.depreciable());
        if (dep.depreciable()) {
            metodo.setValue(dep.metodo());
            vida.setValue(dep.vidaMeses());
            residual.setValue(dep.residual());
            inicio.setValue(dep.inicio());
        }
        observacion.setValue(vacio(d.observacion()));
    }

    private void mostrarParametros() {
        boolean mostrar=depreciable.getValue();
        for (var campo:List.<Component>of(metodo,vida,residual,inicio)) campo.setVisible(mostrar);
        parametros.forEach(f->f.setRequiredIndicatorVisible(mostrar));
    }

    private Datos entrada() {
        boolean deprecia=depreciable.getValue();
        if (obligatorios.stream().anyMatch(HasValue::isEmpty) || nombre.getValue().isBlank()
                || (deprecia && parametros.stream().anyMatch(HasValue::isEmpty)))
            throw new ReglaNegocioException("Revisa los datos requeridos del activo.");
        var compra=new Adquisicion(fecha.getValue(),costo.getValue(),moneda.getValue().id(),
            id(proveedor),factura.isEmpty()?null:factura.getValue().id(),referencia.getValue());
        var asignacion=new Asignacion(sucursal.getValue().id(),ubicacion.getValue(),id(responsable));
        var depreciacion=deprecia?new Depreciacion(true,metodo.getValue(),vida.getValue(),residual.getValue(),inicio.getValue())
            :new Depreciacion(false,null,null,null,null);
        return new Datos(nombre.getValue(),categoria.getValue().codigo(),marca.getValue(),modelo.getValue(),
            serie.getValue(),compra,asignacion,depreciacion,observacion.getValue());
    }

    private static UUID id(ComboBox<Opcion> c) { return c.isEmpty()?null:c.getValue().id(); }
    private static String vacio(String texto) { return texto==null?"":texto; }

    private static <T> void seleccionar(ComboBox<T> combo, List<T> items, Function<T,UUID> id, T previo) {
        var valor=items.stream().filter(i->id.apply(i).equals(id.apply(previo))).findFirst().orElse(null);
        if (valor==null) {
            var opciones=new ArrayList<>(items);
            opciones.add(previo);
            combo.setItems(opciones);
            valor=previo;
        }
        combo.setValue(valor);
    }

    private static <T> ComboBox<T> combo(String etiqueta, com.vaadin.flow.component.ItemLabelGenerator<T> label) {
        var combo=new ComboBox<T>(etiqueta);
        combo.setWidthFull();
        combo.setClearButtonVisible(true);
        combo.setItemLabelGenerator(label);
        return combo;
    }

    private static TextField texto(String etiqueta,int max) {
        var campo=new TextField(etiqueta);
        campo.setWidthFull();
        campo.setMaxLength(max);
        return campo;
    }

    private static DatePicker fecha(String etiqueta) {
        var campo=new DatePicker(etiqueta);
        campo.setWidthFull();
        campo.setLocale(Locale.forLanguageTag("es-DO"));
        campo.setI18n(new DatePicker.DatePickerI18n().setDateFormat("dd/MM/yyyy"));
        return campo;
    }

    private static BigDecimalField dinero(String etiqueta) {
        var campo=new BigDecimalField(etiqueta);
        campo.setWidthFull();
        campo.addAttachListener(e->{if(campo.getValue()!=null) PosNumberFormatter.showMoney(campo);});
        campo.addFocusListener(e->{
            if (campo.getValue()!=null)
                campo.getElement().executeJs("if(this.inputElement)this.inputElement.value=$0",campo.getValue().toPlainString());
        });
        campo.addBlurListener(e->{if(campo.getValue()!=null) PosNumberFormatter.showMoney(campo);});
        return campo;
    }

    private static Div seccion(String titulo,Component... campos) {
        var cabecera=new H3(titulo);
        cabecera.addClassName("cc-section-heading");
        var formulario=new Div(campos);
        formulario.addClassName("cc-dialog-form");
        var seccion=new Div(cabecera,formulario);
        seccion.addClassName("cc-form-section");
        return seccion;
    }
}
