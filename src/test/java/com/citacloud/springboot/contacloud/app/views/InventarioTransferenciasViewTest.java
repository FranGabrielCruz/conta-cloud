package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;import org.junit.jupiter.api.*;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.context.SecurityContextHolder;import java.util.*;import static org.assertj.core.api.Assertions.*;

class InventarioTransferenciasViewTest{
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 @Test void construyeLaPantallaConFiltroTodosSinError(){var principal=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"DEMO","Administrador","admin","",true,Set.of("inventario_transferencias.ver","inventario_transferencias.crear"));SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities()));assertThatCode(()->new InventarioTransferenciasView(null,null)).doesNotThrowAnyException();}
}
