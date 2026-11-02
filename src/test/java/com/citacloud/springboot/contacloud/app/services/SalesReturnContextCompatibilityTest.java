package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfirmarDevolucionVentaInput;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SalesReturnContextCompatibilityTest {
    @Test void terminalContextDoesNotChangeExistingBusinessIdempotencyFingerprint(){
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),key=UUID.randomUUID();var p=mock(TenantPrincipal.class);when(p.tenantId()).thenReturn(tenant);when(p.empresaId()).thenReturn(company);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,List.of()));
        var idempotency=mock(IdempotentOperationService.class);
        var service=new DevolucionVentaService(mock(FacturaVentaRepository.class),mock(LineaFacturaVentaRepository.class),mock(DevolucionVentaRepository.class),mock(LineaDevolucionVentaRepository.class),mock(MotivoDevolucionVentaRepository.class),mock(AlmacenRepository.class),mock(NotaCreditoVentaRepository.class),mock(CreditoClienteRepository.class),mock(InventoryMovementService.class),idempotency,mock(AuditoriaService.class),mock(JdbcTemplate.class),mock(io.micrometer.core.instrument.MeterRegistry.class));
        var input=new ConfirmarDevolucionVentaInput(key,UUID.randomUUID(),UUID.randomUUID(),null,false,null,List.of());
        when(idempotency.begin(eq(tenant),eq(company),eq("SALES_RETURN_CONFIRM"),eq(key),anyString())).thenThrow(new ReglaNegocioException("Fin de la comprobación de huella"));
        try{
            assertThrows(ReglaNegocioException.class,()->service.confirmar(input));
            assertThrows(ReglaNegocioException.class,()->service.confirmar(input,"terminal-secret-context"));
            var hashes=ArgumentCaptor.forClass(String.class);verify(idempotency,times(2)).begin(eq(tenant),eq(company),eq("SALES_RETURN_CONFIRM"),eq(key),hashes.capture());
            assertEquals(List.of(CommandFingerprint.sha256(input),CommandFingerprint.sha256(input)),hashes.getAllValues());assertFalse(input.toString().contains("terminal-secret-context"));
        }finally{SecurityContextHolder.clearContext();}
    }
}
