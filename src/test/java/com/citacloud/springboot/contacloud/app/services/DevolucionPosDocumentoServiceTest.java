package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.dto.ConfirmarDevolucionVentaInput;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DevolucionPosDocumentoServiceTest {
    final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),id=UUID.randomUUID(),invoice=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID();
    final DevolucionVentaRepository returns=mock(DevolucionVentaRepository.class);final FacturaVentaRepository invoices=mock(FacturaVentaRepository.class);
    final LineaDevolucionVentaRepository lines=mock(LineaDevolucionVentaRepository.class);final NotaCreditoVentaRepository notes=mock(NotaCreditoVentaRepository.class);
    final EmpresaRepository companies=mock(EmpresaRepository.class);final DatosEmpresaRepository companyData=mock(DatosEmpresaRepository.class);
    final CashRegisterRepository registers=mock(CashRegisterRepository.class);final SucursalRepository branches=mock(SucursalRepository.class);final TerminalPosRepository terminals=mock(TerminalPosRepository.class);
    final DevolucionPosDocumentoService service=new DevolucionPosDocumentoService(returns,invoices,lines,notes,companies,companyData,registers,branches,terminals);
    final DevolucionVenta d=mock(DevolucionVenta.class);final NotaCreditoVenta note=mock(NotaCreditoVenta.class);final TenantPrincipal principal=mock(TenantPrincipal.class);
    static BigDecimal amount(String value){return new BigDecimal(value);}
    @BeforeEach void setup(){
        when(principal.tenantId()).thenReturn(tenant);when(principal.empresaId()).thenReturn(company);when(principal.nombre()).thenReturn("Operador actual");when(principal.accesoTodasSucursales()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        when(d.getId()).thenReturn(id);when(d.getTenantId()).thenReturn(tenant);when(d.getEmpresaId()).thenReturn(company);when(d.getStatus()).thenReturn("CONFIRMED");
        when(d.getFacturaId()).thenReturn(invoice);when(d.getNumero()).thenReturn("DEV-9");when(d.getFacturaNumero()).thenReturn("FAC-17");
        when(d.getSucursalPosId()).thenReturn(branch);when(d.getCajaPosId()).thenReturn(cash);when(d.getOperadorPosNombre()).thenReturn("Operador histórico");
        when(d.getEmisorPos()).thenReturn(new EmisorFacturaPos("Emisor histórico","124543678",null,null,"DO","Caja histórica","America/Santo_Domingo"));
        when(d.getClienteNombre()).thenReturn("Cliente histórico");when(d.getConfirmadaEn()).thenReturn(OffsetDateTime.parse("2026-11-01T19:08:00Z"));
        when(d.getMonedaCodigo()).thenReturn("DOP");when(d.getMotivoNombre()).thenReturn("Producto defectuoso");when(d.getObservacion()).thenReturn("Observación histórica");
        when(d.getSubtotalBruto()).thenReturn(amount("12"));when(d.getDescuento()).thenReturn(BigDecimal.ZERO);when(d.getRecargo()).thenReturn(BigDecimal.ZERO);when(d.getImpuesto()).thenReturn(amount("2.16"));when(d.getTotal()).thenReturn(amount("14.16"));
        when(returns.findByIdAndTenantIdAndEmpresaId(id,tenant,company)).thenReturn(Optional.of(d));
        var line=mock(LineaDevolucionVenta.class);when(line.getCantidad()).thenReturn(BigDecimal.ONE);when(line.getDescripcion()).thenReturn("naranja devuelta");when(line.getTotal()).thenReturn(amount("14.16"));
        when(line.getPrecio()).thenReturn(amount("12"));when(line.getDescuento()).thenReturn(BigDecimal.ZERO);when(line.getImpuesto()).thenReturn(amount("2.16"));when(line.getImpuestoNombre()).thenReturn("ITBIS");
        when(lines.findAllByDevolucionIdAndTenantIdAndEmpresaIdOrderByOrden(id,tenant,company)).thenReturn(List.of(line));
        when(note.getMonto()).thenReturn(amount("14.16"));when(note.getCreditoCliente()).thenReturn(amount("14.16"));when(note.getAplicadoCxC()).thenReturn(BigDecimal.ZERO);
        when(notes.findByDevolucionIdAndTenantIdAndEmpresaId(id,tenant,company)).thenReturn(Optional.of(note));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void partialReturnUsesOnlyReturnedLinesAndHistoricalValues(){
        var document=service.documento(service.obtener(id));
        assertEquals(1,document.items().size());assertEquals(BigDecimal.ONE,document.items().getFirst().returnedQuantity());assertEquals("naranja devuelta",document.items().getFirst().description());
        assertEquals("FAC-17",document.originalInvoiceNumber());assertEquals("Emisor histórico",document.company().name());assertEquals("Cliente histórico",document.customer().name());
        assertEquals("Operador histórico",document.operator());assertEquals("01/11/2026 15:08",com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats.date(document.returnDateTime()));
        assertEquals("Producto defectuoso\nObservación histórica",document.reason());assertEquals(amount("14.16"),document.totalReturned());
        verifyNoInteractions(invoices,companies,companyData,registers,branches,terminals);
    }
    @Test void storedCreditAndReceivableAmountsAreNotMisreportedAsCash(){
        when(note.getCreditoCliente()).thenReturn(amount("9.16"));when(note.getAplicadoCxC()).thenReturn(amount("5"));
        var document=service.documento(d);assertEquals(2,document.refundMethods().size());assertEquals("Aplicado a cuenta por cobrar",document.refundMethods().getFirst().label());
        assertEquals("Saldo a favor",document.refundMethods().get(1).label());assertEquals(amount("14.16"),document.totalRefunded());
        when(note.getCreditoCliente()).thenReturn(BigDecimal.ZERO);when(note.getAplicadoCxC()).thenReturn(BigDecimal.ZERO);
        assertEquals("Nota de crédito",service.documento(d).refundMethods().getFirst().label());
    }
    @Test void manipulatedTenantAndCompanyAreRejectedBeforeRelatedReads(){
        when(d.getTenantId()).thenReturn(UUID.randomUUID());assertThrows(RecursoNoEncontradoException.class,()->service.generar(id));
        when(d.getTenantId()).thenReturn(tenant);when(d.getEmpresaId()).thenReturn(UUID.randomUUID());assertThrows(RecursoNoEncontradoException.class,()->service.generar(id));
        verifyNoInteractions(lines,notes,companies,companyData,invoices);
    }
    @Test void unauthorizedBranchAndUnconfirmedStateAreRejected(){
        when(principal.accesoTodasSucursales()).thenReturn(false);when(principal.sucursalIds()).thenReturn(Set.of(UUID.randomUUID()));
        assertThrows(RecursoNoEncontradoException.class,()->service.generar(id));verifyNoInteractions(lines,notes);
        when(principal.accesoTodasSucursales()).thenReturn(true);when(d.getStatus()).thenReturn("DRAFT");assertThrows(ReglaNegocioException.class,()->service.obtener(id));
    }
    @Test void terminalSnapshotUsesCurrentOriginRegisterRatherThanOriginalSaleRegister(){
        var original=mock(FacturaVenta.class);UUID originalCash=UUID.randomUUID();when(original.getTenantId()).thenReturn(tenant);when(original.getEmpresaId()).thenReturn(company);when(original.getSucursalId()).thenReturn(branch);when(original.getCajaId()).thenReturn(originalCash);
        var terminal=mock(TerminalPos.class);when(terminal.getCajaId()).thenReturn(cash);when(terminals.findByTokenHashAndTenantIdAndEmpresaIdAndActivoTrue(anyString(),eq(tenant),eq(company))).thenReturn(Optional.of(terminal));
        var register=mock(Caja.class);when(register.isActivo()).thenReturn(false);when(register.getId()).thenReturn(cash);when(register.getSucursalId()).thenReturn(branch);when(register.getNombre()).thenReturn("Caja actual");
        when(registers.findByIdAndTenantIdAndEmpresaId(cash,tenant,company)).thenReturn(Optional.of(register));when(branches.findByIdAndEmpresaId(branch,company)).thenReturn(Optional.empty());
        var companyRecord=mock(Empresa.class);when(companyRecord.getNombre()).thenReturn("Emisor al confirmar");when(companyRecord.getPaisCodigo()).thenReturn("DO");
        when(companies.findByIdAndTenantId(company,tenant)).thenReturn(Optional.of(companyRecord));when(companyData.findByEmpresaId(company)).thenReturn(Optional.empty());
        service.conservarIdentidad(d,original,"terminal-private-test");var snapshot=ArgumentCaptor.forClass(EmisorFacturaPos.class);
        verify(d).conservarIdentidadPos(snapshot.capture(),eq(branch),eq(cash),isNull(),eq("Operador actual"));assertEquals("Emisor al confirmar",snapshot.getValue().getNombre());
        verify(registers,never()).findByIdAndTenantIdAndEmpresaId(originalCash,tenant,company);
        var input=new ConfirmarDevolucionVentaInput(UUID.randomUUID(),invoice,UUID.randomUUID(),null,false,null,List.of());assertFalse(input.toString().contains("terminal-private-test"));
    }
    @Test void invalidTerminalStopsBeforeCapturingAnyIssuer(){
        var original=mock(FacturaVenta.class);when(original.getTenantId()).thenReturn(tenant);when(original.getEmpresaId()).thenReturn(company);when(original.getSucursalId()).thenReturn(branch);
        assertThrows(ReglaNegocioException.class,()->service.conservarIdentidad(d,original,"unknown-terminal"));verifyNoInteractions(companies,companyData,registers);
    }
    @Test void legacyReturnPrefersOriginalInvoiceSnapshotOverCurrentCompany(){
        when(d.getSucursalPosId()).thenReturn(null);when(d.getCajaPosId()).thenReturn(null);when(d.getEmisorPos()).thenReturn(null);
        var original=mock(FacturaVenta.class);when(original.getSucursalId()).thenReturn(branch);when(original.getCajaId()).thenReturn(cash);
        when(original.getEmisorPos()).thenReturn(new EmisorFacturaPos("Emisor original de factura","124543678",null,null,"DO","Caja original",null));
        when(invoices.findByIdAndTenantIdAndEmpresaId(invoice,tenant,company)).thenReturn(Optional.of(original));
        var document=service.documento(d);assertEquals("Emisor original de factura",document.company().name());assertTrue(document.footer().contains("Emisor histórico de la factura origen."));
        verifyNoInteractions(companies,companyData);
    }
}
