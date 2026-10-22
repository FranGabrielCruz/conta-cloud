package com.citacloud.springboot.contacloud.app.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotentOperationServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final MeterRegistry metrics=mock(MeterRegistry.class);
    private final IdempotentOperationService service=new IdempotentOperationService(jdbc,metrics,750);

    @BeforeEach void transaction(){
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(metrics.counter(anyString(),any(String[].class))).thenReturn(mock(Counter.class));
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.clear();}

    @Test void registraYCompletaUnaOperacionNueva(){
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(contains("insert into operaciones_idempotentes"),any(Object[].class))).thenReturn(1);
        when(jdbc.update(contains("update operaciones_idempotentes"),any(Object[].class))).thenReturn(1);
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),key=UUID.randomUUID(),result=UUID.randomUUID();
        var start=service.begin(tenant,empresa,"TEST",key,"abc");
        assertTrue(start.owner());
        service.complete(tenant,empresa,"TEST",key,"ENTITY",result);
        verify(jdbc).execute("set local lock_timeout = '750ms'");
        verify(jdbc).update(contains("insert into operaciones_idempotentes"),any(Object[].class));
        verify(jdbc).update(contains("update operaciones_idempotentes"),any(Object[].class));
    }

    @Test void exigeUnaTransaccionReal(){
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThrows(IllegalStateException.class,()->service.begin(UUID.randomUUID(),UUID.randomUUID(),"TEST",UUID.randomUUID(),"abc"));
    }
}
