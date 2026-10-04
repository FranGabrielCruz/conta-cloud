package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class RecepcionCompraMapper {
    public RecepcionCompra toEntity(RecepcionCompraInput i,UUID tenant,UUID empresa,String numero,UUID usuario){
        return new RecepcionCompra(tenant,empresa,numero,i.proveedorId(),i.ordenCompraId(),i.almacenId(),i.fecha(),
            i.referencia(),i.notas(),i.claveIdempotencia(),usuario);
    }
    public RecepcionCompraDto toDto(RecepcionCompra r,Map<UUID,BigDecimal> ordenadas,Map<UUID,BigDecimal> previas){
        return base(r,r.getLineas().stream().map(l->linea(l,ordenadas,previas)).toList());
    }
    public RecepcionCompraDto toSummaryDto(RecepcionCompra r){return base(r,List.of());}
    private RecepcionCompraDto base(RecepcionCompra r,List<LineaRecepcionCompraDto> lineas){return new RecepcionCompraDto(r.getId(),
        r.getNumero(),r.getProveedorId(),r.getProveedor()==null?null:r.getProveedor().getNombreComercial(),r.getOrdenCompraId(),
        r.getOrdenCompra()==null?null:r.getOrdenCompra().getNumero(),r.getAlmacenId(),r.getAlmacen()==null?null:r.getAlmacen().getNombre(),
        r.getFecha(),r.getReferencia(),r.getNotas(),r.getMotivoDiferencia(),r.getEstado(),r.getVersion(),
        r.getMotivoAnulacion(),r.getConfirmadaEn(),r.getConfirmadaPor(),lineas);}
    private LineaRecepcionCompraDto linea(LineaRecepcionCompra l,Map<UUID,BigDecimal> ordenadas,Map<UUID,BigDecimal> previas){
        BigDecimal ordered=l.getLineaOrdenId()==null?null:ordenadas.get(l.getLineaOrdenId());
        BigDecimal prior=l.getLineaOrdenId()==null?BigDecimal.ZERO:previas.getOrDefault(l.getLineaOrdenId(),BigDecimal.ZERO);
        BigDecimal pending=ordered==null?null:ordered.subtract(prior).subtract(l.getCantidad()).max(BigDecimal.ZERO);
        BigDecimal excess=ordered==null?null:prior.add(l.getCantidad()).subtract(ordered).max(BigDecimal.ZERO);
        return new LineaRecepcionCompraDto(l.getLineaOrdenId(),l.getProductoId(),l.getProductoCodigo(),l.getDescripcion(),
            l.getUnidad(),ordered,prior,l.getCantidad(),pending,excess,l.getOrigen(),l.getDiferencia());
    }
}
