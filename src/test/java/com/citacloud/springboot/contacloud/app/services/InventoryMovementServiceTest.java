package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;

class InventoryMovementServiceTest {
    @Test void confirmarRecepcionCreaUnaSolaEntradaAunqueSeReintente(){
        InventoryMovementRepository repository=mock(InventoryMovementRepository.class);InventoryMovementService service=new InventoryMovementService(repository);
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),receiptId=UUID.randomUUID(),product=UUID.randomUUID(),warehouse=UUID.randomUUID(),user=UUID.randomUUID();
        RecepcionCompra receipt=new RecepcionCompra(tenant,company,"REC-000001",UUID.randomUUID(),null,warehouse,LocalDate.now(),null,null,user);ReflectionTestUtils.setField(receipt,"id",receiptId);
        LineaRecepcionCompra line=new LineaRecepcionCompra(tenant,company,null,product,"P-1","Producto","Unidad",BigDecimal.TEN,1);
        Producto tracked=mock(Producto.class);when(tracked.isControlaExistencia()).thenReturn(true);
        when(repository.existsByTenantIdAndEmpresaIdAndTipoReferenciaAndReferenciaIdAndProductoIdAndDirection(tenant,company,InventoryMovementService.RECEIPT,receiptId,product,"IN")).thenReturn(false,true);
        service.receive(receipt,line,tracked,user);service.receive(receipt,line,tracked,user);
        verify(repository,times(1)).save(any(MovimientoInventario.class));
    }
    @Test void productoSinControlDeExistenciaNoGeneraMovimiento(){
        InventoryMovementRepository repository=mock(InventoryMovementRepository.class);InventoryMovementService service=new InventoryMovementService(repository);
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),productId=UUID.randomUUID(),user=UUID.randomUUID();
        RecepcionCompra receipt=new RecepcionCompra(tenant,company,"REC-000002",UUID.randomUUID(),null,UUID.randomUUID(),LocalDate.now(),null,null,user);
        LineaRecepcionCompra line=new LineaRecepcionCompra(tenant,company,null,productId,"P-2","Producto","Unidad",BigDecimal.ONE,1);
        Producto product=mock(Producto.class);when(product.isControlaExistencia()).thenReturn(false);
        service.receive(receipt,line,product,user);
        verifyNoInteractions(repository);
    }
}
