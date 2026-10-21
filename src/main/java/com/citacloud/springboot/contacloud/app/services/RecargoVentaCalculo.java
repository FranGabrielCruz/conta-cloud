package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import java.math.*;
import java.util.*;

public final class RecargoVentaCalculo {
    public static final int SCALE=4; private static final RoundingMode ROUNDING=RoundingMode.HALF_UP;
    private RecargoVentaCalculo(){}

    public static Resultado calcular(AlcanceRecargoVenta alcance,TipoRecargoVenta tipo,BigDecimal valor,
            Collection<UUID> lineasSeleccionadas,List<Linea> lineas){
        if(alcance==null||tipo==null)throw new ReglaNegocioException("Selecciona el alcance y tipo de recargo.");
        if(valor==null||valor.signum()<=0)throw new ReglaNegocioException("El valor del recargo debe ser mayor que cero.");
        if(lineas==null||lineas.isEmpty())throw new ReglaNegocioException("No hay productos en la venta para aplicar un recargo.");
        Set<UUID> seleccion=lineasSeleccionadas==null?Set.of():new LinkedHashSet<>(lineasSeleccionadas);
        if(alcance==AlcanceRecargoVenta.LINE&&seleccion.isEmpty())throw new ReglaNegocioException("Seleccione al menos un producto.");
        Set<UUID> existentes=new HashSet<>(); for(Linea l:lineas)existentes.add(l.lineaId());
        if(alcance==AlcanceRecargoVenta.LINE&&!existentes.containsAll(seleccion))throw new ReglaNegocioException("La selección de productos ya no corresponde a la venta actual.");
        List<Linea> elegibles=lineas.stream().filter(l->l.elegible()&&(alcance==AlcanceRecargoVenta.SALE||seleccion.contains(l.lineaId()))).toList();
        if(elegibles.isEmpty())throw new ReglaNegocioException(alcance==AlcanceRecargoVenta.LINE?"Los productos seleccionados no admiten recargos.":"No existen productos elegibles para aplicar el recargo.");
        if(alcance==AlcanceRecargoVenta.LINE&&elegibles.size()!=seleccion.size())throw new ReglaNegocioException("Uno de los productos seleccionados no admite recargos.");
        BigDecimal base=elegibles.stream().map(Linea::baseComercial).reduce(zero(),BigDecimal::add).setScale(SCALE,ROUNDING);
        BigDecimal monto=tipo==TipoRecargoVenta.PERCENTAGE?base.multiply(valor).divide(new BigDecimal("100"),SCALE,ROUNDING):valor.setScale(SCALE,ROUNDING);
        Map<UUID,BigDecimal> distribucion=distribuir(tipo,valor,monto,base,elegibles); List<Asignacion> asignaciones=new ArrayList<>();
        for(Linea l:lineas){BigDecimal recargo=distribucion.getOrDefault(l.lineaId(),zero());BigDecimal baseFiscal=l.baseComercial().add(recargo).setScale(SCALE,ROUNDING);
            BigDecimal impuestoBase=l.baseComercial().multiply(l.tasaImpuestoBase()).divide(new BigDecimal("100"),SCALE,ROUNDING);
            BigDecimal impuestoRecargo=recargo.multiply(l.tasaImpuestoRecargo()).divide(new BigDecimal("100"),SCALE,ROUNDING);
            BigDecimal impuesto=impuestoBase.add(impuestoRecargo).setScale(SCALE,ROUNDING);
            asignaciones.add(new Asignacion(l.lineaId(),l.productoId(),l.baseComercial(),recargo,baseFiscal,impuesto,baseFiscal.add(impuesto).setScale(SCALE,ROUNDING)));}
        BigDecimal porcentaje=base.signum()==0?zero():monto.multiply(new BigDecimal("100")).divide(base,SCALE,ROUNDING);
        return new Resultado(base,monto,base.add(monto).setScale(SCALE,ROUNDING),porcentaje,List.copyOf(asignaciones));
    }
    private static Map<UUID,BigDecimal> distribuir(TipoRecargoVenta tipo,BigDecimal valor,BigDecimal monto,BigDecimal base,List<Linea> lineas){Map<UUID,BigDecimal> r=new LinkedHashMap<>();BigDecimal acumulado=zero();for(int i=0;i<lineas.size();i++){Linea l=lineas.get(i);BigDecimal x=i==lineas.size()-1?monto.subtract(acumulado).setScale(SCALE,ROUNDING):tipo==TipoRecargoVenta.PERCENTAGE?l.baseComercial().multiply(valor).divide(new BigDecimal("100"),SCALE,ROUNDING):monto.multiply(l.baseComercial()).divide(base,SCALE,ROUNDING);r.put(l.lineaId(),x);acumulado=acumulado.add(x);}return r;}
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(SCALE,ROUNDING);}
    public record Linea(UUID lineaId,UUID productoId,BigDecimal baseComercial,BigDecimal tasaImpuestoBase,
            BigDecimal tasaImpuestoRecargo,boolean elegible){
        public Linea{
            baseComercial=(baseComercial==null?zero():baseComercial.setScale(SCALE,ROUNDING));
            tasaImpuestoBase=tasaImpuestoBase==null?zero():tasaImpuestoBase;
            tasaImpuestoRecargo=tasaImpuestoRecargo==null?zero():tasaImpuestoRecargo;
        }
    }
    public record Asignacion(UUID lineaId,UUID productoId,BigDecimal baseComercial,BigDecimal recargo,BigDecimal baseImponible,BigDecimal impuesto,BigDecimal total){}
    public record Resultado(BigDecimal baseElegible,BigDecimal montoRecargo,BigDecimal importePosterior,BigDecimal porcentajeEfectivo,List<Asignacion> asignaciones){}
}
