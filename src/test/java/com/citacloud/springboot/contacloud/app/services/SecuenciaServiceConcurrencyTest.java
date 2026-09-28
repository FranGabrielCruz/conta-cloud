package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:secuencia-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
class SecuenciaServiceConcurrencyTest {
    @Autowired SecuenciaService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AuditoriaService auditoria;

    @Test void cienSolicitudesConcurrentesGeneranCienNumerosUnicos() throws Exception {
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),comprobante=UUID.randomUUID(),secuencia=UUID.randomUUID();
        jdbc.update("insert into module_catalog(module_key,name,active,implemented,core,display_order) values ('SECUENCIAS','Secuencias',true,true,true,1)");
        jdbc.update("insert into tipos_comprobantes_fiscales(id,tenant_id,empresa_id,codigo,nombre,prefijo,tipo,reglas_secuencia,activo,creado_en,actualizado_en) values (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            comprobante,tenant,empresa,"CF","Crédito fiscal","B01","FISCAL","{}",true);
        jdbc.update("insert into secuencias(id,tenant_id,empresa_id,tipo_comprobante_id,codigo,numero_inicial,valor_actual,numero_final,longitud,activo,creado_en,actualizado_en) values (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            secuencia,tenant,empresa,comprobante,"SEC-TEST",1,0,99999999L,8,true);

        var executor=Executors.newFixedThreadPool(20);
        var inicio=new CountDownLatch(1);
        List<Future<String>> resultados=new ArrayList<>();
        for(int i=0;i<100;i++)resultados.add(executor.submit(()->{
            var principal=new TenantPrincipal(UUID.randomUUID(),tenant,empresa,null,"EMPRESA01","Prueba","prueba","",true,true,Set.of(),Set.of("secuencias.editar"));
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
            try{inicio.await();return service.siguiente(secuencia);}finally{SecurityContextHolder.clearContext();}
        }));
        inicio.countDown();
        Set<String> numeros=new HashSet<>();
        for(Future<String> resultado:resultados)numeros.add(resultado.get(20,TimeUnit.SECONDS));
        executor.shutdownNow();

        assertThat(numeros).hasSize(100);
        assertThat(numeros).allMatch(numero->numero.startsWith("B01")).noneMatch(numero->numero.startsWith("CF"));
        assertThat(jdbc.queryForObject("select valor_actual from secuencias where id=?",Long.class,secuencia)).isEqualTo(100L);
    }
}
