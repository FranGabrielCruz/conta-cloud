package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.AgenteImpresionRepository;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SalesReturnPostCommitTest {
    @Test void rendererFailureInSeparateTransactionCannotRollBackCommittedReturn(){
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:return-postcommit-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var realJdbc=new JdbcTemplate(dataSource);realJdbc.execute("create table confirmed_return(id uuid primary key,status varchar(20))");
        var transactions=new DataSourceTransactionManager(dataSource);UUID id=UUID.randomUUID();
        new TransactionTemplate(transactions).execute(status->{realJdbc.update("insert into confirmed_return values(?,?)",id,"CONFIRMED");return null;});
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),agentId=UUID.randomUUID();
        var p=mock(TenantPrincipal.class);when(p.tenantId()).thenReturn(tenant);when(p.empresaId()).thenReturn(company);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));
        var jobs=mock(JdbcTemplate.class);var documents=mock(DevolucionPosDocumentoService.class);var routing=mock(PrintAgentRoutingService.class);var agents=mock(AgenteImpresionRepository.class);
        var d=mock(DevolucionVenta.class);when(documents.obtener(id)).thenReturn(d);when(documents.origen(d)).thenReturn(new DevolucionPosDocumentoService.Origen(branch,cash));when(routing.resolver(cash)).thenReturn(agentId);
        var agent=mock(AgenteImpresion.class);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(agent.getSucursalId()).thenReturn(branch);var register=mock(Caja.class);when(register.getId()).thenReturn(cash);when(agent.getCajas()).thenReturn(Set.of(register));when(agents.bloquear(agentId,tenant,company)).thenReturn(Optional.of(agent));
        when(documents.generar(id)).thenAnswer(invocation->{assertEquals("CONFIRMED",realJdbc.queryForObject("select status from confirmed_return where id=?",String.class,id));throw new ReglaNegocioException("Renderer no disponible");});
        var target=new DevolucionVentaPrintService(jobs,documents,routing,agents,mock(AuditoriaService.class));
        var interceptor=new TransactionInterceptor();interceptor.setTransactionManager(transactions);interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxyFactory=new ProxyFactory(target);proxyFactory.setProxyTargetClass(true);proxyFactory.addAdvice(interceptor);var service=(DevolucionVentaPrintService)proxyFactory.getProxy();
        try{
            assertThrows(ReglaNegocioException.class,()->service.solicitar(id,UUID.randomUUID()));
            assertEquals(1,realJdbc.queryForObject("select count(*) from confirmed_return where status='CONFIRMED'",Integer.class));
            verify(jobs,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));
        }finally{SecurityContextHolder.clearContext();}
    }
}
