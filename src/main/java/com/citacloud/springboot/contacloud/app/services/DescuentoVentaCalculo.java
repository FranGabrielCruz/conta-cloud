package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import java.math.*;
import java.util.*;

public final class DescuentoVentaCalculo {
    public static final int SCALE=4;private static final RoundingMode ROUNDING=RoundingMode.HALF_UP;
    private DescuentoVentaCalculo(){}

    public static Resultado calcular(AlcanceDescuentoVenta alcance,TipoDescuentoVenta tipo,BigDecimal valor,Collection<UUID> lineasSeleccionadas,List<Linea> lineas){
        if(alcance==null||tipo==null)throw new ReglaNegocioException("Selecciona el alcance y tipo de descuento.");
        if(valor==null||valor.signum()<=0)throw new ReglaNegocioException("El valor del descuento debe ser mayor que cero.");
        if(lineas==null||lineas.isEmpty())throw new ReglaNegocioException("Agregue al menos un producto antes de aplicar un descuento.");
        if(tipo==TipoDescuentoVenta.PERCENTAGE&&valor.compareTo(new BigDecimal("100"))>0)throw new ReglaNegocioException("El porcentaje del descuento no puede superar 100%.");
        Set<UUID> seleccion=lineasSeleccionadas==null?Set.of():new LinkedHashSet<>(lineasSeleccionadas);
        if(alcance==AlcanceDescuentoVenta.LINE&&seleccion.isEmpty())throw new ReglaNegocioException("Seleccione al menos un producto.");
        Set<UUID> existentes=lineas.stream().map(Linea::lineaId).collect(java.util.stream.Collectors.toSet());
        if(alcance==AlcanceDescuentoVenta.LINE&&!existentes.containsAll(seleccion))throw new ReglaNegocioException("La selección de productos ya no corresponde a la venta actual.");
        List<Linea> elegibles=lineas.stream().filter(l->l.elegible()&&(alcance==AlcanceDescuentoVenta.SALE||seleccion.contains(l.lineaId()))).toList();
        if(elegibles.isEmpty())throw new ReglaNegocioException(alcance==AlcanceDescuentoVenta.LINE?"Los productos seleccionados no admiten descuentos.":"No existen productos elegibles para aplicar el descuento.");
        if(alcance==AlcanceDescuentoVenta.LINE&&elegibles.size()!=seleccion.size())throw new ReglaNegocioException("Uno de los productos seleccionados no admite descuentos.");
        BigDecimal baseElegible=elegibles.stream().map(Linea::bruto).reduce(zero(),BigDecimal::add).setScale(SCALE,ROUNDING);
        BigDecimal monto=tipo==TipoDescuentoVenta.PERCENTAGE
                ? baseElegible.multiply(valor).divide(new BigDecimal("100"),SCALE,ROUNDING)
                : valor.setScale(SCALE,ROUNDING);
        if(monto.compareTo(baseElegible)>0)throw new ReglaNegocioException(alcance==AlcanceDescuentoVenta.LINE
                ?"El descuento no puede superar el importe elegible de los productos seleccionados."
                :"El descuento no puede superar la base elegible.");
        Map<UUID,BigDecimal> distribucion=distribuir(tipo,valor,monto,baseElegible,elegibles);
        List<Asignacion> asignaciones=new ArrayList<>();
        for(Linea linea:lineas){
            BigDecimal descuento=distribucion.getOrDefault(linea.lineaId(),zero());
            BigDecimal base=linea.bruto().subtract(descuento).setScale(SCALE,ROUNDING);
            if(base.signum()<0)throw new ReglaNegocioException("El descuento no puede generar una línea negativa.");
            BigDecimal impuesto=base.multiply(linea.tasaImpuesto()).divide(new BigDecimal("100"),SCALE,ROUNDING);
            asignaciones.add(new Asignacion(linea.lineaId(),linea.productoId(),linea.bruto(),descuento,base,impuesto,base.add(impuesto).setScale(SCALE,ROUNDING)));
        }
        BigDecimal porcentaje=baseElegible.signum()==0?zero():monto.multiply(new BigDecimal("100")).divide(baseElegible,SCALE,ROUNDING);
        return new Resultado(baseElegible,monto,baseElegible.subtract(monto).setScale(SCALE,ROUNDING),porcentaje,List.copyOf(asignaciones));
    }

    private static Map<UUID,BigDecimal> distribuir(TipoDescuentoVenta tipo,BigDecimal valor,BigDecimal monto,BigDecimal base,List<Linea> lineas){
        Map<UUID,BigDecimal> resultado=new LinkedHashMap<>();BigDecimal acumulado=zero();
        for(int i=0;i<lineas.size();i++){
            Linea linea=lineas.get(i);BigDecimal asignado;
            if(i==lineas.size()-1)asignado=monto.subtract(acumulado).setScale(SCALE,ROUNDING);
            else if(tipo==TipoDescuentoVenta.PERCENTAGE)asignado=linea.bruto().multiply(valor).divide(new BigDecimal("100"),SCALE,ROUNDING);
            else asignado=monto.multiply(linea.bruto()).divide(base,SCALE,ROUNDING);
            acumulado=acumulado.add(asignado);resultado.put(linea.lineaId(),asignado);
        }
        return resultado;
    }
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(SCALE,ROUNDING);}
    public record Linea(UUID lineaId,UUID productoId,BigDecimal bruto,BigDecimal tasaImpuesto,boolean elegible){public Linea{bruto=(bruto==null?zero():bruto.setScale(SCALE,ROUNDING));tasaImpuesto=tasaImpuesto==null?zero():tasaImpuesto;}public Linea(UUID productoId,BigDecimal bruto,BigDecimal tasaImpuesto,boolean elegible){this(productoId,productoId,bruto,tasaImpuesto,elegible);}}
    public record Asignacion(UUID lineaId,UUID productoId,BigDecimal subtotalBruto,BigDecimal descuento,BigDecimal baseImponible,BigDecimal impuesto,BigDecimal total){}
    public record Resultado(BigDecimal baseElegible,BigDecimal montoDescuento,BigDecimal importePosterior,BigDecimal porcentajeEfectivo,List<Asignacion> asignaciones){}
}
