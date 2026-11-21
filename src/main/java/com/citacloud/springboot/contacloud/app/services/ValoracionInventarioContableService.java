package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.EventoContable.Valoracion;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Usa el costo de compra configurado al confirmar; el evento conserva costo, moneda y tasa.
 * No aplica FIFO/promedio ni revaloriza movimientos anteriores. */
@Service
public class ValoracionInventarioContableService {
 private final ProductRepository productos;
 private final AsientoContableRepository libro;
 public ValoracionInventarioContableService(ProductRepository productos,AsientoContableRepository libro){this.productos=productos;this.libro=libro;}
 @Transactional(propagation=Propagation.MANDATORY)
 public List<Valoracion> valorar(Map<UUID,BigDecimal> cantidades,LocalDate fecha) {
  var p=TenantContext.principalActual();if(cantidades.isEmpty())return List.of();
  var moneda=libro.monedaBase(p).orElseThrow(()->new ReglaNegocioException("Configura una moneda base activa."));
  var encontrados=productos.findAllByTenantIdAndEmpresaIdAndIdIn(p.tenantId(),p.empresaId(),cantidades.keySet()).stream().collect(Collectors.toMap(x->x.getId(),x->x));
  List<Valoracion> resultado=new ArrayList<>();Map<UUID,BigDecimal> tasas=new HashMap<>();
  for(var x:cantidades.entrySet()) {
   var producto=encontrados.get(x.getKey());
   if(producto==null||!producto.isControlaExistencia())throw new ReglaNegocioException("El producto inventariable no está disponible en esta empresa.");
   var costo=producto.getCostoCompra();var divisa=producto.getMonedaId();
   if(costo==null||costo.signum()<0||divisa==null)throw new ReglaNegocioException("Configura un costo de compra y su moneda para valorar «"+producto.getNombre()+"».");
   if(!libro.monedaActiva(p,divisa))throw new ReglaNegocioException("La moneda del costo no está activa en esta empresa.");
   var tasa=tasas.computeIfAbsent(divisa,id->id.equals(moneda.id())?BigDecimal.ONE:libro.tasaExacta(p,id,moneda.id(),fecha)
    .orElseThrow(()->new ReglaNegocioException("No existe una tasa de cambio para valorar el inventario en la fecha del documento.")));
   resultado.add(new Valoracion(producto.getId(),x.getValue(),costo,divisa,tasa,x.getValue().multiply(costo).multiply(tasa).setScale(2,RoundingMode.HALF_UP)));
  }
  return List.copyOf(resultado);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID monedaBase() {return libro.monedaBase(TenantContext.principalActual()).orElseThrow(()->new ReglaNegocioException("Configura una moneda base activa.")).id();}
}

