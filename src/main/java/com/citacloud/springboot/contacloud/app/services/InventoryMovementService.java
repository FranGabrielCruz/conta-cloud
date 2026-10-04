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
    private final InventoryMovementRepository movements;
    public InventoryMovementService(InventoryMovementRepository movements){this.movements=movements;}
    public void receive(RecepcionCompra receipt,LineaRecepcionCompra line,Producto product,UUID user){
        if(product==null||!product.isControlaExistencia())return;
        if(movements.existsByTenantIdAndEmpresaIdAndTipoReferenciaAndReferenciaIdAndProductoIdAndDirection(receipt.getTenantId(),receipt.getEmpresaId(),RECEIPT,receipt.getId(),line.getProductoId(),"IN"))return;
        movements.save(new MovimientoInventario(receipt.getTenantId(),receipt.getEmpresaId(),receipt.getAlmacenId(),line.getProductoId(),receipt.getFecha(),line.getCantidad(),"IN","PURCHASE_RECEIPT",RECEIPT,receipt.getId(),null,user));
    }
    public void reverse(RecepcionCompra receipt,Producto product,MovimientoInventario original,UUID user){
        if(movements.existsByTenantIdAndEmpresaIdAndTipoReferenciaAndReferenciaIdAndProductoIdAndDirection(receipt.getTenantId(),receipt.getEmpresaId(),RECEIPT_VOID,receipt.getId(),original.getProductoId(),"OUT"))return;
        BigDecimal balance=movements.saldo(receipt.getTenantId(),receipt.getEmpresaId(),receipt.getAlmacenId(),original.getProductoId());
        if(!product.isPermiteExistenciaNegativa()&&balance.subtract(original.getCantidad()).signum()<0)
            throw new ReglaNegocioException("No se puede anular la recepción porque dejaría existencias negativas para "+product.getNombre()+".");
        movements.save(new MovimientoInventario(receipt.getTenantId(),receipt.getEmpresaId(),receipt.getAlmacenId(),original.getProductoId(),LocalDate.now(),original.getCantidad(),"OUT","PURCHASE_RECEIPT_VOID",RECEIPT_VOID,receipt.getId(),original.getId(),user));
    }
    public List<MovimientoInventario> receiptEntries(RecepcionCompra receipt){return movements.findAllByTipoReferenciaAndReferenciaIdAndTenantIdAndEmpresaId(RECEIPT,receipt.getId(),receipt.getTenantId(),receipt.getEmpresaId());}
}
