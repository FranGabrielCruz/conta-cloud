package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FacturaPosDocumentoServiceTest {
    private final UUID tenant=UUID.randomUUID(), company=UUID.randomUUID(), invoice=UUID.randomUUID();
    private final EmpresaRepository empresas=mock(EmpresaRepository.class);
    private final DatosEmpresaRepository datos=mock(DatosEmpresaRepository.class);
    private final LineaFacturaVentaRepository lineas=mock(LineaFacturaVentaRepository.class);
    private final PagoVentaRepository pagos=mock(PagoVentaRepository.class);
    private final AplicacionPagoVentaRepository aplicaciones=mock(AplicacionPagoVentaRepository.class);
    private final FacturaPosDocumentoService service=new FacturaPosDocumentoService(empresas,datos,lineas,pagos,aplicaciones);
    private final FacturaVenta f=mock(FacturaVenta.class);
    @BeforeEach void setup() {
        var principal=mock(TenantPrincipal.class);when(principal.tenantId()).thenReturn(tenant);when(principal.empresaId()).thenReturn(company);when(principal.accesoTodasSucursales()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,"",List.of()));
        when(f.getTenantId()).thenReturn(tenant);when(f.getEmpresaId()).thenReturn(company);when(f.getId()).thenReturn(invoice);
        when(f.getEmisorPos()).thenReturn(new EmisorFacturaPos("Empresa histórica","124543678",null,null,"DO","Caja histórica","America/Santo_Domingo"));
        when(f.getNumero()).thenReturn("FAC-17");when(f.getClienteNombre()).thenReturn("Cliente histórico");
        when(f.getVendedorNombre()).thenReturn("Cajero histórico");when(f.getCondicionPago()).thenReturn("Crédito");when(f.getMonedaCodigo()).thenReturn("EUR");
        when(f.getConfirmadaEn()).thenReturn(OffsetDateTime.parse("2026-11-01T04:29:00Z"));
        when(f.getSubtotal()).thenReturn(new BigDecimal("12012"));when(f.getDescuentoTotal()).thenReturn(BigDecimal.ZERO);
        when(f.getRecargoTotal()).thenReturn(new BigDecimal("9.99"));when(f.getImpuestoTotal()).thenReturn(new BigDecimal("2162.16"));
        when(f.getTotal()).thenReturn(new BigDecimal("14174.16"));when(f.getRecibido()).thenReturn(BigDecimal.ZERO);when(f.getCambio()).thenReturn(BigDecimal.ZERO);
        var line=mock(LineaFacturaVenta.class);when(line.getCantidad()).thenReturn(BigDecimal.ONE);when(line.getDescripcion()).thenReturn("Reparación Café Piña");
        when(line.getTotal()).thenReturn(new BigDecimal("14160"));when(line.getImpuesto()).thenReturn(new BigDecimal("2162.16"));when(line.getImpuestoNombre()).thenReturn("ITBIS");
        when(lineas.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(invoice,tenant,company)).thenReturn(List.of(line));
        when(pagos.findByFacturaIdAndTenantIdAndEmpresaId(invoice,tenant,company)).thenReturn(Optional.empty());
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void historicalSnapshotNeverQueriesCurrentIssuerAndPreservesConfirmedAmounts() {
        var document=service.documento(f);
        assertEquals("Empresa histórica",document.issuer().name());assertEquals("Caja histórica",document.cashRegister());
        assertEquals("01/11/2026 00:29",com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats.date(document.dateTime()));
        assertEquals(new BigDecimal("14174.16"),document.total());assertEquals(new BigDecimal("9.99"),document.surcharge());
        assertEquals("ITBIS",document.taxes().getFirst().label());assertEquals("Crédito",document.paymentCondition());
        assertNull(document.ncf());assertEquals("FACTURA",document.documentType());assertFalse(document.testDocument());
        verifyNoInteractions(empresas,datos,aplicaciones);
    }
    @Test void multipaymentUsesStoredNamesAndAmounts() {
        var payment=mock(PagoVenta.class);UUID id=UUID.randomUUID();when(payment.getId()).thenReturn(id);
        when(pagos.findByFacturaIdAndTenantIdAndEmpresaId(invoice,tenant,company)).thenReturn(Optional.of(payment));
        var cash=mock(AplicacionPagoVenta.class);when(cash.getNombreMetodo()).thenReturn("Efectivo histórico");when(cash.getMonto()).thenReturn(new BigDecimal("5000"));
        var card=mock(AplicacionPagoVenta.class);when(card.getNombreMetodo()).thenReturn("Tarjeta histórica");when(card.getMonto()).thenReturn(new BigDecimal("9174.16"));
        when(aplicaciones.findAllByPagoIdAndTenantIdAndEmpresaIdOrderByLineOrder(id,tenant,company)).thenReturn(List.of(cash,card));
        var document=service.documento(f);assertEquals(2,document.payments().size());assertEquals("Tarjeta histórica",document.payments().get(1).label());
        assertEquals(new BigDecimal("9174.16"),document.payments().get(1).amount());
    }
    @Test void otherTenantCannotRenderOrReadAnyRelatedData() {
        when(f.getTenantId()).thenReturn(UUID.randomUUID());assertThrows(RecursoNoEncontradoException.class,()->service.generar(f));
        verifyNoInteractions(empresas,datos,lineas,pagos,aplicaciones);
    }
    @Test void otherCompanyCannotRenderOrCaptureIssuer() {
        when(f.getEmpresaId()).thenReturn(UUID.randomUUID());assertThrows(RecursoNoEncontradoException.class,()->service.conservarEmisor(f,"Caja"));
        verifyNoInteractions(empresas,datos,lineas,pagos,aplicaciones);
    }
    @Test void captureReadsIssuerOnlyAtConfirmation() {
        var e=mock(Empresa.class);when(e.getNombre()).thenReturn("Emisor al confirmar");when(e.getPaisCodigo()).thenReturn("DO");
        when(empresas.findByIdAndTenantId(company,tenant)).thenReturn(Optional.of(e));when(datos.findByEmpresaId(company)).thenReturn(Optional.empty());
        service.conservarEmisor(f,"Caja 02");var snapshot=ArgumentCaptor.forClass(EmisorFacturaPos.class);verify(f).conservarEmisorPos(snapshot.capture());
        assertEquals("Emisor al confirmar",snapshot.getValue().getNombre());assertEquals("Caja 02",snapshot.getValue().getCaja());
    }
    @Test void branchOutsideAllowedSetCannotRender() { var p=TenantContext.principalActual();when(p.accesoTodasSucursales()).thenReturn(false);when(p.sucursalIds()).thenReturn(Set.of());when(f.getSucursalId()).thenReturn(UUID.randomUUID());assertThrows(RecursoNoEncontradoException.class,()->service.generar(f));verifyNoInteractions(empresas,datos,lineas,pagos,aplicaciones); }
    @Test void generatedInvoiceIsExact80mmAndHasNoTestLabel() throws Exception {
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(service.generar(f).contenido())) {
            assertEquals(80,pdf.getPage(0).getMediaBox().getWidth()*25.4/72,0.001);
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(text.contains("TOTAL EUR:"));assertFalse(text.contains("DOCUMENTO DE PRUEBA"));
        }
    }
}
