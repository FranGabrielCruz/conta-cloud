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
}
