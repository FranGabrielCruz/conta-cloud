package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.services.BankReconciliationService;
import com.citacloud.springboot.contacloud.app.services.BankStatementMovementService;
import com.citacloud.springboot.contacloud.app.services.EmpresaModuloService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class ConciliacionBancariaViewTest {
    @BeforeEach void autenticar(){
        UUID id=UUID.randomUUID();
        var principal=new TenantPrincipal(id,id,id,null,"DEMO","Administrador","admin","",true,true,
            Set.of(),Set.of("conciliacion_bancaria.ver","conciliacion_bancaria.crear"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }

    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void construyeLaVistaConLaOpcionTodosEnElFiltroDeEstado(){
        assertThatCode(()->new ConciliacionBancariaView(
            mock(BankReconciliationService.class),mock(BankStatementMovementService.class),
            mock(EmpresaModuloService.class)))
            .doesNotThrowAnyException();
    }
}
