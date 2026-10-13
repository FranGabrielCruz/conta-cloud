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
        InventoryMovementRepository repository=mock(InventoryMovementRepository.class);InventoryMovementNumberService numbers=mock(InventoryMovementNumberService.class);InventoryMovementService service=new InventoryMovementService(repository,numbers);
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),receiptId=UUID.randomUUID(),product=UUID.randomUUID(),warehouse=UUID.randomUUID(),user=UUID.randomUUID();
        RecepcionCompra receipt=new RecepcionCompra(tenant,company,"REC-000001",UUID.randomUUID(),null,warehouse,LocalDate.now(),null,null,user);ReflectionTestUtils.setField(receipt,"id",receiptId);
        LineaRecepcionCompra line=new LineaRecepcionCompra(tenant,company,null,product,"P-1","Producto","Unidad",BigDecimal.TEN,1);
        Producto tracked=mock(Producto.class);when(tracked.isControlaExistencia()).thenReturn(true);
        when(numbers.siguiente(tenant,company)).thenReturn("MOV-000001");when(tracked.getUnidadMedidaId()).thenReturn(UUID.randomUUID());
        String key=MovimientoInventario.clave(InventoryMovementService.RECEIPT,receiptId,warehouse,product,"IN",null);
        when(repository.existsByTenantIdAndEmpresaIdAndClaveOrigen(tenant,company,key)).thenReturn(false,true);
        service.receive(receipt,line,tracked,user);service.receive(receipt,line,tracked,user);
        verify(repository,times(1)).save(any(MovimientoInventario.class));
    }
    @Test void productoSinControlDeExistenciaNoGeneraMovimiento(){
        InventoryMovementRepository repository=mock(InventoryMovementRepository.class);InventoryMovementNumberService numbers=mock(InventoryMovementNumberService.class);InventoryMovementService service=new InventoryMovementService(repository,numbers);
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),productId=UUID.randomUUID(),user=UUID.randomUUID();
        RecepcionCompra receipt=new RecepcionCompra(tenant,company,"REC-000002",UUID.randomUUID(),null,UUID.randomUUID(),LocalDate.now(),null,null,user);
        LineaRecepcionCompra line=new LineaRecepcionCompra(tenant,company,null,productId,"P-2","Producto","Unidad",BigDecimal.ONE,1);
        Producto product=mock(Producto.class);when(product.isControlaExistencia()).thenReturn(false);
        service.receive(receipt,line,product,user);
        verifyNoInteractions(repository);
        verifyNoInteractions(numbers);
    }
    @Test void movimientoDeTransferenciaEsIdempotente(){
        InventoryMovementRepository repository=mock(InventoryMovementRepository.class);InventoryMovementNumberService numbers=mock(InventoryMovementNumberService.class);InventoryMovementService service=new InventoryMovementService(repository,numbers);
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),warehouse=UUID.randomUUID(),product=UUID.randomUUID(),lineId=UUID.randomUUID(),user=UUID.randomUUID();
        TransferenciaInventario transfer=mock(TransferenciaInventario.class);LineaTransferenciaInventario line=mock(LineaTransferenciaInventario.class);
        when(transfer.getTenantId()).thenReturn(tenant);when(transfer.getEmpresaId()).thenReturn(company);when(transfer.getId()).thenReturn(UUID.randomUUID());when(transfer.getFecha()).thenReturn(LocalDate.now());when(transfer.getNumero()).thenReturn("TRF-000001");
        when(line.getId()).thenReturn(lineId);when(line.getProductoId()).thenReturn(product);when(line.getUnidadId()).thenReturn(UUID.randomUUID());when(line.getCantidadBase()).thenReturn(BigDecimal.TEN);
        String key=MovimientoInventario.clave("INVENTORY_TRANSFER",transfer.getId(),warehouse,product,"OUT",lineId);when(repository.existsByTenantIdAndEmpresaIdAndClaveOrigen(tenant,company,key)).thenReturn(false,true);when(numbers.siguiente(tenant,company)).thenReturn("MOV-000001");
        service.transferir(transfer,line,warehouse,"OUT","TRANSFER_DISPATCH_OUT",user);service.transferir(transfer,line,warehouse,"OUT","TRANSFER_DISPATCH_OUT",user);
        verify(repository,times(1)).save(any(MovimientoInventario.class));
    }
}
