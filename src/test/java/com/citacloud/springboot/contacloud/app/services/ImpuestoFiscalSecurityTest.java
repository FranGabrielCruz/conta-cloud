package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ImpuestoFiscalInput;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.lang.reflect.Method;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ImpuestoFiscalSecurityTest {
    @Test void protectsEveryBackendOperationWithItsPermission() throws Exception {assertPermission("buscar","impuestos.ver",String.class,Boolean.class,int.class,int.class);assertPermission("obtener","impuestos.ver",UUID.class);assertPermission("crear","impuestos.crear",ImpuestoFiscalInput.class);assertPermission("actualizar","impuestos.editar",UUID.class,ImpuestoFiscalInput.class);assertPermission("desactivar","impuestos.desactivar",UUID.class,long.class);assertPermission("reactivar","impuestos.reactivar",UUID.class,long.class);}
    private static void assertPermission(String method,String permission,Class<?>...parameters) throws Exception {Method target=ImpuestoFiscalService.class.getMethod(method,parameters);PreAuthorize auth=target.getAnnotation(PreAuthorize.class);assertThat(auth).isNotNull();assertThat(auth.value()).contains("habilitado('IMPUESTOS')").contains("hasAuthority('"+permission+"')");}
}
