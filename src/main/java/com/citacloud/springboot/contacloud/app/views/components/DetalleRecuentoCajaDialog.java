package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.RecuentoCajaDto;
import com.citacloud.springboot.contacloud.app.models.ResultadoRecuentoCaja;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class DetalleRecuentoCajaDialog extends Dialog {
    private static final DateTimeFormatter FECHA_HORA=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public DetalleRecuentoCajaDialog(RecuentoCajaDto recuento,boolean puedeImprimir,Consumer<java.util.UUID> imprimir){
        setHeaderTitle("VER RECUENTO");setWidth("min(800px,96vw)");setMaxHeight("92vh");addClassName("cc-cash-count-dialog");
        Div generales=new Div(info("Número",recuento.numero()),info("Caja",recuento.caja()+" · "+recuento.sucursal()),
            infoLargo("Apertura",recuento.apertura()),info("Fecha/Hora",FECHA_HORA.format(recuento.contadoEn())),
            info("Usuario",recuento.usuario()),info("Moneda",recuento.monedaCodigo()));
        generales.addClassName("cc-cash-count-result-grid");
        Div cuerpo=new Div(generales);cuerpo.addClassName("cc-cash-count-detail");
        if(!recuento.detalles().isEmpty()){
            AppGrid<RecuentoCajaDto.Detalle> tabla=new AppGrid<>(RecuentoCajaDto.Detalle.class);
            tabla.addColumn(x->recuento.monedaSimbolo()+" "+money(x.denominacion())).setHeader("Denominación");
            tabla.addColumn(RecuentoCajaDto.Detalle::cantidad).setHeader("Cantidad").setTextAlign(ColumnTextAlign.END);
            tabla.addColumn(x->money(x.subtotal())).setHeader("Subtotal").setTextAlign(ColumnTextAlign.END);
            tabla.setItems(recuento.detalles().stream().filter(x->x.cantidad()>0).toList());
            H4 titulo=new H4("DETALLE DEL CONTEO");titulo.addClassName("cc-cash-count-section-title");cuerpo.add(titulo,tabla);
        }
        Div estado=new Div(new H3(label(recuento.resultado())));
        estado.addClassNames("cc-cash-count-result","cc-result-"+recuento.resultado().name().toLowerCase());
        Div resumen=new Div(new H4("RESULTADO"),fila("Total esperado",importe(recuento,recuento.esperado())),
            fila("Total contado",importe(recuento,recuento.contado())),
            fila("Diferencia",diferencia(recuento,recuento.diferencia())),estado);
        resumen.addClassName("cc-cash-count-result-summary");cuerpo.add(resumen);
        if(recuento.observacion()!=null&&!recuento.observacion().isBlank()){
            H4 titulo=new H4("OBSERVACIÓN");titulo.addClassName("cc-cash-count-section-title");
            Paragraph texto=new Paragraph(recuento.observacion());texto.addClassName("cc-cash-count-observation");cuerpo.add(titulo,texto);
        }
        add(cuerpo);
        if(puedeImprimir)getFooter().add(new AppActionButton(ActionType.PRINT,ButtonSize.MAIN,"Imprimir recuento",e->imprimir.accept(recuento.id())));
        getFooter().add(new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",e->close()));
    }

    private static Div info(String etiqueta,String valor){Div d=new Div(new Span(etiqueta),new Span(valor==null?"—":valor));d.addClassName("cc-cash-count-info");return d;}
    private static Div infoLargo(String etiqueta,String valor){Div d=info(etiqueta,valor);Span texto=(Span)d.getComponentAt(1);texto.addClassName("cc-cash-count-long-value");if(valor!=null)texto.getElement().setAttribute("title",valor);return d;}
    private static Div fila(String etiqueta,String valor){Div d=new Div(new Span(etiqueta),new Span(valor));d.addClassName("cc-cash-count-result-row");return d;}
    private static String money(BigDecimal valor){return PosNumberFormatter.money(valor);}
    private static String importe(RecuentoCajaDto r,BigDecimal valor){return r.monedaCodigo()+" "+money(valor);}
    private static String diferencia(RecuentoCajaDto r,BigDecimal valor){return r.monedaCodigo()+" "+(valor.signum()>0?"+":"")+money(valor);}
    public static String label(ResultadoRecuentoCaja resultado){return switch(resultado){case BALANCED->"CUADRE CORRECTO";case SHORTAGE->"FALTANTE DE CAJA";case OVERAGE->"SOBRANTE DE CAJA";};}
}
