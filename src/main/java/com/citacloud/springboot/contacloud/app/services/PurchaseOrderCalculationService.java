package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.LineaOrdenCompraInput;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.List;

@Service
public class PurchaseOrderCalculationService {
    public static final int SCALE=4;
    private static final RoundingMode ROUNDING=RoundingMode.HALF_UP;

    public CalculatedLine calculate(LineaOrdenCompraInput input,String taxName,BigDecimal taxRate){
        if(input==null)throw new ReglaNegocioException("La línea de la orden es obligatoria.");
        String descripcion=input.descripcion()==null?"":input.descripcion().trim();
        if(descripcion.isEmpty())throw new ReglaNegocioException("La descripción de la línea es obligatoria.");
        if(descripcion.length()>500)throw new ReglaNegocioException("La descripción de la línea excede 500 caracteres.");
        BigDecimal cantidad=value(input.cantidad());BigDecimal precio=value(input.precioUnitario());BigDecimal descuento=value(input.descuento());
        if(cantidad.signum()<=0)throw new ReglaNegocioException("La cantidad debe ser mayor que cero.");
        if(precio.signum()<0)throw new ReglaNegocioException("El precio unitario no puede ser negativo.");
        if(descuento.signum()<0)throw new ReglaNegocioException("El descuento no puede ser negativo.");
        BigDecimal bruto=money(cantidad.multiply(precio));
        if(descuento.compareTo(bruto)>0)throw new ReglaNegocioException("El descuento no puede superar el subtotal de la línea.");
        BigDecimal base=money(bruto.subtract(descuento));BigDecimal tasa=rate(taxRate);
        if(tasa.signum()<0)throw new ReglaNegocioException("La tasa del impuesto no puede ser negativa.");
        BigDecimal impuesto=money(base.multiply(tasa).divide(BigDecimal.valueOf(100),10,ROUNDING));
        return new CalculatedLine(descripcion,value(cantidad),money(precio),money(descuento),taxName,tasa,bruto,base,impuesto,money(base.add(impuesto)));
    }
    public Totals totals(List<CalculatedLine> lines){if(lines==null||lines.isEmpty())throw new ReglaNegocioException("Agrega al menos una línea a la orden de compra.");
        BigDecimal subtotal=BigDecimal.ZERO,descuento=BigDecimal.ZERO,impuesto=BigDecimal.ZERO,total=BigDecimal.ZERO;
        for(var l:lines){subtotal=subtotal.add(l.grossSubtotal());descuento=descuento.add(l.discount());impuesto=impuesto.add(l.taxAmount());total=total.add(l.total());}
        return new Totals(money(subtotal),money(descuento),money(impuesto),money(total));}
    public static BigDecimal money(BigDecimal value){return value(value).setScale(SCALE,ROUNDING);}
    private static BigDecimal value(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private static BigDecimal rate(BigDecimal value){return value(value).setScale(SCALE,ROUNDING);}
    public record CalculatedLine(String description,BigDecimal quantity,BigDecimal unitPrice,BigDecimal discount,
        String taxName,BigDecimal taxRate,BigDecimal grossSubtotal,BigDecimal taxableBase,BigDecimal taxAmount,BigDecimal total) {}
    public record Totals(BigDecimal subtotal,BigDecimal discount,BigDecimal tax,BigDecimal total) {}
}
