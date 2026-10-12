package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LineaCotizacionInput;
import com.citacloud.springboot.contacloud.app.models.TipoDescuentoCotizacion;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.List;

@Service
public class CotizacionCalculoService {
    public static final int SCALE=4;
    private static final RoundingMode ROUNDING=RoundingMode.HALF_UP;

    public LineaCalculada calcular(LineaCotizacionInput input,String nombreImpuesto,BigDecimal tasaImpuesto){
        if(input==null)throw new ReglaNegocioException("La línea de la cotización es obligatoria.");
        String descripcion=input.descripcion()==null?"":input.descripcion().trim();
        if(descripcion.isEmpty())throw new ReglaNegocioException("La descripción de la línea es obligatoria.");
        if(descripcion.length()>500)throw new ReglaNegocioException("La descripción de la línea excede 500 caracteres.");
        BigDecimal cantidad=valor(input.cantidad());BigDecimal precio=valor(input.precioUnitario());
        BigDecimal valorDescuento=valor(input.valorDescuento());TipoDescuentoCotizacion tipo=input.tipoDescuento()==null?TipoDescuentoCotizacion.MONTO:input.tipoDescuento();
        if(cantidad.signum()<=0)throw new ReglaNegocioException("La cantidad debe ser mayor que cero.");
        if(precio.signum()<0)throw new ReglaNegocioException("El precio unitario no puede ser negativo.");
        if(valorDescuento.signum()<0)throw new ReglaNegocioException("El descuento no puede ser negativo.");
        if(tipo==TipoDescuentoCotizacion.PORCENTAJE&&valorDescuento.compareTo(BigDecimal.valueOf(100))>0)
            throw new ReglaNegocioException("El porcentaje de descuento no puede superar 100%.");
        BigDecimal bruto=dinero(cantidad.multiply(precio));
        BigDecimal descuento=tipo==TipoDescuentoCotizacion.PORCENTAJE
            ?dinero(bruto.multiply(valorDescuento).divide(BigDecimal.valueOf(100),10,ROUNDING)):dinero(valorDescuento);
        if(descuento.compareTo(bruto)>0)throw new ReglaNegocioException("El descuento no puede superar el subtotal de la línea.");
        BigDecimal base=dinero(bruto.subtract(descuento));BigDecimal tasa=valor(tasaImpuesto).setScale(SCALE,ROUNDING);
        if(tasa.signum()<0)throw new ReglaNegocioException("La tasa del impuesto no puede ser negativa.");
        BigDecimal impuesto=dinero(base.multiply(tasa).divide(BigDecimal.valueOf(100),10,ROUNDING));
        return new LineaCalculada(descripcion,valor(cantidad),dinero(precio),tipo,valorDescuento.setScale(SCALE,ROUNDING),
            descuento,nombreImpuesto,tasa,bruto,base,impuesto,dinero(base.add(impuesto)));
    }
    public Totales totales(List<LineaCalculada> lineas){if(lineas==null||lineas.isEmpty())throw new ReglaNegocioException("Agrega al menos una línea a la cotización.");
        BigDecimal subtotal=BigDecimal.ZERO,descuento=BigDecimal.ZERO,impuesto=BigDecimal.ZERO,total=BigDecimal.ZERO;
        for(var l:lineas){subtotal=subtotal.add(l.subtotalBruto());descuento=descuento.add(l.descuento());impuesto=impuesto.add(l.impuesto());total=total.add(l.total());}
        return new Totales(dinero(subtotal),dinero(descuento),dinero(impuesto),dinero(total));}
    public static BigDecimal dinero(BigDecimal value){return valor(value).setScale(SCALE,ROUNDING);}
    private static BigDecimal valor(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    public record LineaCalculada(String descripcion,BigDecimal cantidad,BigDecimal precioUnitario,
        TipoDescuentoCotizacion tipoDescuento,BigDecimal valorDescuento,BigDecimal descuento,String impuestoNombre,
        BigDecimal tasaImpuesto,BigDecimal subtotalBruto,BigDecimal baseImponible,BigDecimal impuesto,BigDecimal total) {}
    public record Totales(BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,BigDecimal total) {}
}
