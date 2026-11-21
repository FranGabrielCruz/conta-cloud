package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.ProductRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CompraEventoContableService {
 private final ProductRepository productos;
 private final ConfiguracionMotorService configuracion;
 private final AccountingPostingService posting;
 public CompraEventoContableService(ProductRepository productos,ConfiguracionMotorService configuracion,AccountingPostingService posting) {
  this.productos=productos;this.configuracion=configuracion;this.posting=posting;
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID registrada(FacturaProveedor factura,PagoFacturaProveedorInput pago) {
  validarAmbito(factura);
  if(factura.getEstado()!=EstadoFacturaProveedor.REGISTERED)throw new ReglaNegocioException("Solo se contabilizan facturas registradas.");
  if(!configuracion.efectivo(ConfiguracionMotorDto.Modulo.COMPRAS))return null;
  var p=TenantContext.principalActual();
  var ids=factura.getLineas().stream().map(LineaFacturaProveedor::getProductoId).collect(Collectors.toSet());
  var clasificacion=productos.findAllByTenantIdAndEmpresaIdAndIdIn(p.tenantId(),p.empresaId(),ids).stream()
   .collect(Collectors.toMap(Producto::getId,Producto::isControlaExistencia));
  if(clasificacion.size()!=ids.size())throw new ReglaNegocioException("Los productos de la compra no están disponibles en esta empresa.");
  var lineas=factura.getLineas().stream().map(l->new EventoContable.RenglonCompra(clasificacion.get(l.getProductoId()),
   l.getSubtotal().subtract(l.getDescuento()))).toList();
  boolean contado=factura.getCondicionPagoTipoSnapshot()==TipoCondicionPago.CASH;
  if(contado&&(pago==null||pago.tipoFuente()==null||pago.fuenteId()==null))throw new ReglaNegocioException("La compra al contado requiere su recurso de pago confirmado.");
  return posting.contabilizar(new EventoContable(fuente(factura),factura.getVersion(),factura.getFecha(),factura.getMonedaId(),
   "Registro de factura de proveedor "+factura.getNumeroInterno(),TraceContext.currentOrCreate(),
   new EventoContable.Compra(lineas,factura.getImpuesto(),factura.getTotal(),contado?pago.tipoFuente():null,contado?pago.fuenteId():null)));
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public UUID anulada(FacturaProveedor factura,String motivo) {
  validarAmbito(factura);
  // Se usa la fecha del documento. Si su período ya cerró, se rechaza la anulación completa;
  // nunca se elige silenciosamente otra fecha ni se modifica un ejercicio cerrado.
  return posting.reversar(fuente(factura),factura.getFecha(),motivo);
 }
 private static Fuente fuente(FacturaProveedor f) {
  return new Fuente(Origen.COMPRAS,"FACTURA_PROVEEDOR",f.getId(),f.getNumeroInterno(),"REGISTRO");
 }
 @Transactional(readOnly=true)
 public Optional<UUID> asiento(FacturaProveedor f) { validarAmbito(f);return posting.consultar(fuente(f)); }
 private static void validarAmbito(FacturaProveedor f) {
  var p=TenantContext.principalActual();
  if(f==null||!p.tenantId().equals(f.getTenantId())||!p.empresaId().equals(f.getEmpresaId()))
   throw new org.springframework.security.access.AccessDeniedException("La factura no está disponible.");
 }
}

