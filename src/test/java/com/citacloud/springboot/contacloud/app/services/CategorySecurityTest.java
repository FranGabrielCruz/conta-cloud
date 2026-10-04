package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CategorySecurityTest {
    @Test void protectsEveryBackendOperationWithItsPermission() throws Exception {
        assertPermission("searchCategories","categorias.ver",String.class,Boolean.class,int.class,int.class);
        assertPermission("getCategory","categorias.ver",java.util.UUID.class);
        assertPermission("createCategory","categorias.crear",com.citacloud.springboot.contacloud.app.dto.CategoriaInput.class);
        assertPermission("updateCategory","categorias.editar",java.util.UUID.class,com.citacloud.springboot.contacloud.app.dto.CategoriaInput.class);
        assertPermission("deactivateCategory","categorias.desactivar",java.util.UUID.class,long.class);
        assertPermission("reactivateCategory","categorias.reactivar",java.util.UUID.class,long.class);
    }

    private static void assertPermission(String method,String permission,Class<?>...parameters) throws Exception {Method target=CategoryService.class.getMethod(method,parameters);PreAuthorize authorization=target.getAnnotation(PreAuthorize.class);assertThat(authorization).isNotNull();assertThat(authorization.value()).contains("habilitado('INVENTARIO')").contains("hasAuthority('"+permission+"')");}
}
