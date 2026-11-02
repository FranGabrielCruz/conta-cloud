package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class CashOperationPrintDocumentServiceTest {
 final ValeCajaRepository vouchers=mock(ValeCajaRepository.class);final CierreCajaRepository closings=mock(CierreCajaRepository.class);
 final ValeCajaService vs=mock(ValeCajaService.class);final CierreCajaService cs=mock(CierreCajaService.class);
 final CashOperationPrintDocumentService service=new CashOperationPrintDocumentService(vouchers,closings,vs,cs,mock(ValeCajaPdfService.class),mock(CierreCajaPdfService.class));
 final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID();
 @BeforeEach void setup(){var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of());SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void origenAjenoNoSeResuelveNiEjecutaNegocio(){assertThatThrownBy(()->service.vale(UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);assertThatThrownBy(()->service.cierre(UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(vs,cs);}
 @Test void valeTieneTicketNativo80MmConValoresGuardados()throws Exception{
  var v=mock(ValeCajaDto.class);when(v.empresaNombre()).thenReturn("Empresa histórica");when(v.numero()).thenReturn("VC-001");when(v.fechaHora()).thenReturn(OffsetDateTime.parse("2026-11-01T12:00:00Z"));when(v.estado()).thenReturn(EstadoValeCaja.CONFIRMED);when(v.beneficiario()).thenReturn("Beneficiario");when(v.concepto()).thenReturn("Gastos menores");when(v.monedaCodigo()).thenReturn("DOP");when(v.importe()).thenReturn(new BigDecimal("1234.56"));
  try(var pdf=Loader.loadPDF(service.voucherTicket(v).contenido())){assertThat(pdf.getPage(0).getMediaBox().getWidth()*25.4/72).isCloseTo(80,within(.01));assertThat(new PDFTextStripper().getText(pdf)).contains("VALE DE CAJA","VC-001","1,234.56","Gastos menores");}
 }
 @Test void cierreTieneTicketNativo80MmSinRecalcularDiferencia()throws Exception{
  var c=mock(CierreCaja.class);when(c.getEmpresaNombre()).thenReturn("Empresa histórica");var r=mock(CierreCajaDto.class);when(r.numero()).thenReturn("CC-001");when(r.cierre()).thenReturn(OffsetDateTime.parse("2026-11-01T12:00:00Z"));when(r.apertura()).thenReturn(OffsetDateTime.parse("2026-11-01T04:00:00Z"));when(r.resultado()).thenReturn(ResultadoRecuentoCaja.SHORTAGE);when(r.monedaCodigo()).thenReturn("DOP");when(r.diferencia()).thenReturn(new BigDecimal("-10.25"));when(r.detalles()).thenReturn(List.of());when(r.mediosPago()).thenReturn(List.of());
  try(var pdf=Loader.loadPDF(service.closingTicket(c,r).contenido())){assertThat(pdf.getPage(0).getMediaBox().getWidth()*25.4/72).isCloseTo(80,within(.01));assertThat(new PDFTextStripper().getText(pdf)).contains("CIERRE DE CAJA","CC-001","-10.25","FALTANTE");}
 }
}
