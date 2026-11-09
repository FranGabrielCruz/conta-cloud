package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.CarteraCobrarDto.*;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.services.CuentasCobrarService;
import com.citacloud.springboot.contacloud.app.views.components.AppActionButton;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.BeforeEnterEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CuentasCobrarViewTest {
    @Test void entraConCarteraActivaYPresentaIndicadoresPorMoneda() {
        var principal=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),"TEST","Prueba","test","",true,
            Set.of("cuentas_cobrar.ver"));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        try{
            var servicio=mock(CuentasCobrarService.class);
            var permiso=mock(ModuleAuthorization.class);
            var filtro=new Filtro(null,Estado.ACTIVA,null,null,"");
            when(permiso.canAccess("VENTAS","cuentas_cobrar.ver")).thenReturn(true);
            var indicadores=new Indicadores(List.of(moneda("DOP","100"),moneda("USD","20")),2);
            when(servicio.consultar(filtro,false,0,10)).thenReturn(new Pantalla(Page.empty(),Page.empty(),indicadores));
            var vista=new CuentasCobrarView(servicio,null,permiso);
            vista.beforeEnter(mock(BeforeEnterEvent.class));
            assertThat(hijos(vista).map(c->c.getElement().getText()))
                .contains("TOTAL CARTERA","POR VENCER","VENCIDO","CLIENTES","DOP 100.00","USD 20.00");
            assertThat(hijos(vista).filter(AppActionButton.class::isInstance).filter(Component::isVisible)).isEmpty();
            verify(servicio).consultar(filtro,false,0,10);
        }finally{SecurityContextHolder.clearContext();}
    }

    private static Stream<Component> hijos(Component raiz){
        return Stream.concat(Stream.of(raiz),raiz.getChildren().flatMap(CuentasCobrarViewTest::hijos));
    }
    private static Moneda moneda(String codigo,String monto){
        var valor=new BigDecimal(monto);
        return new Moneda(codigo,valor,valor,BigDecimal.ZERO,valor);
    }
}
