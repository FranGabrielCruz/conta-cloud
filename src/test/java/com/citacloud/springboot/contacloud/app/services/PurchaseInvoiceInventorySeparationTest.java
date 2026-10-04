package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.repositories.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInvoiceInventorySeparationTest {
    @Test void facturaProveedorNoDependeDelInventario(){
        assertThat(Arrays.stream(PurchaseInvoiceService.class.getDeclaredFields()).map(f->f.getType().getName()))
            .doesNotContain(InventoryMovementRepository.class.getName(),InventoryMovementService.class.getName(),PurchaseReceiptService.class.getName());
    }
}
