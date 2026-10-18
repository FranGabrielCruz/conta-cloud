package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.InventoryMovementRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class InventoryMovementService {
    public static final String RECEIPT="PURCHASE_RECEIPT";
    public static final String RECEIPT_VOID="PURCHASE_RECEIPT_VOID";
    private final InventoryMovementRepository movements;private final InventoryMovementNumberService numbers;
    public InventoryMovementService(InventoryMovementRepository movements,InventoryMovementNumberService numbers){this.movements=movements;this.numbers=numbers;}
    public void receive(RecepcionCompra receipt,LineaRecepcionCompra line,Producto product,UUID user){
        if(product==null||!product.isControlaExistencia())return;
        String key=MovimientoInventario.clave(RECEIPT,receipt.getId(),receipt.getAlmacenId(),line.getProductoId(),"IN",line.getId());
        if(movements.existsByTenantIdAndEmpresaIdAndClaveOrigen(receipt.getTenantId(),receipt.getEmpresaId(),key))return;
        movements.save(new MovimientoInventario(receipt.getTenantId(),receipt.getEmpresaId(),numbers.siguiente(receipt.getTenantId(),receipt.getEmpresaId()),receipt.getAlmacenId(),line.getProductoId(),receipt.getFecha(),line.getCantidad(),"IN","PURCHASE_RECEIPT",RECEIPT,receipt.getId(),receipt.getNumero(),receipt.getReferencia(),line.getId(),product.getUnidadMedidaId(),null,user));
    }
    public void reverse(RecepcionCompra receipt,Producto product,MovimientoInventario original,UUID user){
        String key=MovimientoInventario.clave(RECEIPT_VOID,receipt.getId(),receipt.getAlmacenId(),original.getProductoId(),"OUT",original.getLineaOrigenId());
        if(movements.existsByTenantIdAndEmpresaIdAndClaveOrigen(receipt.getTenantId(),receipt.getEmpresaId(),key))return;
        BigDecimal balance=movements.saldo(receipt.getTenantId(),receipt.getEmpresaId(),receipt.getAlmacenId(),original.getProductoId());
        if(!product.isPermiteExistenciaNegativa()&&balance.subtract(original.getCantidad()).signum()<0)
            throw new ReglaNegocioException("No se puede anular la recepción porque dejaría existencias negativas para "+product.getNombre()+".");
        movements.save(new MovimientoInventario(receipt.getTenantId(),receipt.getEmpresaId(),numbers.siguiente(receipt.getTenantId(),receipt.getEmpresaId()),receipt.getAlmacenId(),original.getProductoId(),LocalDate.now(),original.getCantidad(),"OUT","PURCHASE_RECEIPT_VOID",RECEIPT_VOID,receipt.getId(),receipt.getNumero(),receipt.getReferencia(),original.getLineaOrigenId(),product.getUnidadMedidaId(),original.getId(),user));
    }
    public List<MovimientoInventario> receiptEntries(RecepcionCompra receipt){return movements.findAllByTipoReferenciaAndReferenciaIdAndTenantIdAndEmpresaId(RECEIPT,receipt.getId(),receipt.getTenantId(),receipt.getEmpresaId());}
    public void transferir(TransferenciaInventario transferencia,LineaTransferenciaInventario linea,UUID almacen,
            String direccion,String tipoMovimiento,UUID usuario){
        String origen="INVENTORY_TRANSFER";
        String key=MovimientoInventario.clave(origen,transferencia.getId(),almacen,linea.getProductoId(),direccion,linea.getId());
        if(movements.existsByTenantIdAndEmpresaIdAndClaveOrigen(transferencia.getTenantId(),transferencia.getEmpresaId(),key))return;
        movements.save(new MovimientoInventario(transferencia.getTenantId(),transferencia.getEmpresaId(),
            numbers.siguiente(transferencia.getTenantId(),transferencia.getEmpresaId()),almacen,linea.getProductoId(),
            transferencia.getFecha(),linea.getCantidadBase(),direccion,tipoMovimiento,origen,transferencia.getId(),
            transferencia.getNumero(),transferencia.getReferencia(),linea.getId(),linea.getUnidadId(),null,usuario));
    }
    public MovimientoInventario ajustar(AjusteInventario ajuste,LineaAjusteInventario linea,String direccion,
            String tipoMovimiento,UUID usuario,UUID reversoDeMovimiento){
        String origen="INVENTORY_ADJUSTMENT";
        String key=MovimientoInventario.clave(origen,ajuste.getId(),ajuste.getAlmacenId(),linea.getProductoId(),direccion,linea.getId());
        return movements.findByTenantIdAndEmpresaIdAndClaveOrigen(ajuste.getTenantId(),ajuste.getEmpresaId(),key)
            .orElseGet(()->movements.saveAndFlush(new MovimientoInventario(ajuste.getTenantId(),ajuste.getEmpresaId(),numbers.siguiente(ajuste.getTenantId(),ajuste.getEmpresaId()),ajuste.getAlmacenId(),linea.getProductoId(),ajuste.getFecha(),linea.getCantidadBase().abs(),direccion,tipoMovimiento,origen,ajuste.getId(),ajuste.getNumero(),ajuste.getReferencia(),linea.getId(),linea.getUnidadId(),reversoDeMovimiento,usuario)));
    }
    public MovimientoInventario conteo(ConteoFisico conteo,LineaConteoFisico linea,String direccion,UUID usuario){
        String origen="INVENTORY_PHYSICAL_COUNT";String key=MovimientoInventario.clave(origen,conteo.getId(),conteo.getAlmacenId(),linea.getProductoId(),direccion,linea.getId());
        return movements.findByTenantIdAndEmpresaIdAndClaveOrigen(conteo.getTenantId(),conteo.getEmpresaId(),key).orElseGet(()->movements.saveAndFlush(new MovimientoInventario(conteo.getTenantId(),conteo.getEmpresaId(),numbers.siguiente(conteo.getTenantId(),conteo.getEmpresaId()),conteo.getAlmacenId(),linea.getProductoId(),conteo.getFecha(),linea.getDiferencia().abs(),direccion,direccion.equals("IN")?"PHYSICAL_COUNT_IN":"PHYSICAL_COUNT_OUT",origen,conteo.getId(),conteo.getNumero(),conteo.getReferencia(),linea.getId(),linea.getUnidadId(),null,usuario)));
    }
    public void vender(FacturaVenta factura,LineaFacturaVenta linea,Producto producto,UUID usuario){
        if(!linea.isControlaExistencia())return;
        String origen="SALES_INVOICE";
        String key=MovimientoInventario.clave(origen,factura.getId(),factura.getAlmacenId(),linea.getProductoId(),"OUT",linea.getId());
        if(movements.existsByTenantIdAndEmpresaIdAndClaveOrigen(factura.getTenantId(),factura.getEmpresaId(),key))return;
        movements.save(new MovimientoInventario(factura.getTenantId(),factura.getEmpresaId(),
            numbers.siguiente(factura.getTenantId(),factura.getEmpresaId()),factura.getAlmacenId(),linea.getProductoId(),
            factura.getFecha(),linea.getCantidad(),"OUT","SALE",origen,factura.getId(),factura.getNumero(),null,
            linea.getId(),linea.getUnidadId(),null,usuario));
    }
}
