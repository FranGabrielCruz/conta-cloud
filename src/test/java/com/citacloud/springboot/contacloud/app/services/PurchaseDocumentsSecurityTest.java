package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.assertj.core.api.Assertions.assertThat;

class PurchaseDocumentsSecurityTest {
    @Test void operacionesCriticasExigenPermisosEspecificos() throws Exception {
        assertThat(PurchaseInvoiceService.class.getMethod("register",java.util.UUID.class,long.class).getAnnotation(PreAuthorize.class).value()).contains("facturas_proveedores.registrar");
        assertThat(PurchaseInvoiceService.class.getMethod("createAndRegister",com.citacloud.springboot.contacloud.app.dto.FacturaProveedorInput.class).getAnnotation(PreAuthorize.class).value()).contains("facturas_proveedores.crear","facturas_proveedores.registrar");
        assertThat(PurchaseInvoiceService.class.getMethod("updateAndRegister",java.util.UUID.class,com.citacloud.springboot.contacloud.app.dto.FacturaProveedorInput.class).getAnnotation(PreAuthorize.class).value()).contains("facturas_proveedores.editar","facturas_proveedores.registrar");
        assertThat(PurchaseReceiptService.class.getMethod("confirm",java.util.UUID.class,long.class).getAnnotation(PreAuthorize.class).value()).contains("recepciones.confirmar");
        assertThat(PurchaseReceiptService.class.getMethod("createAndConfirm",com.citacloud.springboot.contacloud.app.dto.RecepcionCompraInput.class).getAnnotation(PreAuthorize.class).value()).contains("recepciones.crear","recepciones.confirmar");
        assertThat(PurchaseReceiptService.class.getMethod("updateAndConfirm",java.util.UUID.class,com.citacloud.springboot.contacloud.app.dto.RecepcionCompraInput.class).getAnnotation(PreAuthorize.class).value()).contains("recepciones.editar","recepciones.confirmar");
        assertThat(PurchaseReceiptService.class.getMethod("voidReceipt",java.util.UUID.class,long.class,String.class).getAnnotation(PreAuthorize.class).value()).contains("recepciones.anular");
    }
}
