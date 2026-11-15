package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.Filtro;
import com.citacloud.springboot.contacloud.app.services.CriticalFailureAuditService;
import com.citacloud.springboot.contacloud.app.services.LibroMayorPdfService;
import com.citacloud.springboot.contacloud.app.services.LibroMayorService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LibroMayorControllerTest {
    @Test void filtrosIsoSeCompartenYElPdfIgnoraLaPaginacion() throws Exception {
        var servicio=mock(LibroMayorService.class);
        var pdf=mock(LibroMayorPdfService.class);
        var cuenta=UUID.randomUUID();
        var filtro=new Filtro(cuenta,LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),Tipo.DIARIO,Origen.MANUAL,"FAC-01");
        byte[] contenido={37,80,68,70};
        when(pdf.generar(filtro)).thenReturn(new ArchivoPdfDto("Libro_Mayor.pdf",contenido));
        var mvc=MockMvcBuilders.standaloneSetup(new LibroMayorController(servicio,pdf))
            .setControllerAdvice(new ApiExceptionHandler(mock(CriticalFailureAuditService.class))).build();
        String parametros="?accountId="+cuenta+"&fromDate=2026-11-01&toDate=2026-11-30&type=DIARIO&origin=MANUAL"
            +"&search=FAC-01&page=2&pageSize=25&tenantId="+UUID.randomUUID()+"&companyId="+UUID.randomUUID();
        mvc.perform(get("/api/accounting/general-ledger"+parametros)).andExpect(status().isOk());
        verify(servicio).consultar(filtro,2,25);
        mvc.perform(get("/api/accounting/general-ledger/pdf"+parametros)).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition","inline; filename=\"Libro_Mayor.pdf\""))
            .andExpect(content().bytes(contenido));
        verify(pdf).generar(filtro);
        mvc.perform(get("/api/accounting/general-ledger").param("accountId","UUID inválido"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("UUID inválido"))));
        mvc.perform(get("/api/accounting/general-ledger").param("page","invalid"))
            .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(servicio,pdf);
    }
}
