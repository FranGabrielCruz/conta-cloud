package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Filtro;
import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Mostrar;
import com.citacloud.springboot.contacloud.app.services.BalanzaPdfService;
import com.citacloud.springboot.contacloud.app.services.BalanzaService;
import com.citacloud.springboot.contacloud.app.services.CriticalFailureAuditService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BalanzaControllerTest {
    @Test void aceptaFiltrosYRechazaParametrosInvalidosConUnMensajeSeguro() throws Exception {
        var servicio=mock(BalanzaService.class);
        var pdf=mock(BalanzaPdfService.class);
        var filtro=new Filtro(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),2,Mostrar.SALDO,"Caja + Bancos");
        byte[] contenido={37,80,68,70};
        when(pdf.generar(filtro)).thenReturn(new ArchivoPdfDto("Balanza.pdf",contenido));
        var mvc=MockMvcBuilders.standaloneSetup(new BalanzaController(servicio,pdf))
            .setControllerAdvice(new ApiExceptionHandler(mock(CriticalFailureAuditService.class))).build();
        String ruta="/api/accounting/trial-balance";
        String query="?fromDate=2026-11-01&toDate=2026-11-30&level=2&display=SALDO&page=2&pageSize=25";
        mvc.perform(get(ruta+query).param("search","Caja + Bancos").param("tenantId","otro")).andExpect(status().isOk());
        verify(servicio).consultar(filtro,2,25);
        mvc.perform(get(ruta+"/pdf"+query).param("search","Caja + Bancos").param("companyId","otra")).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition","inline; filename=\"Balanza.pdf\""))
            .andExpect(content().bytes(contenido));
        verify(pdf).generar(filtro);
        mvc.perform(get(ruta).param("display","filtro-no-válido")).andExpect(status().isBadRequest())
            .andExpect(content().string(not(containsString("filtro-no-válido"))));
        verifyNoMoreInteractions(servicio,pdf);
    }
}
