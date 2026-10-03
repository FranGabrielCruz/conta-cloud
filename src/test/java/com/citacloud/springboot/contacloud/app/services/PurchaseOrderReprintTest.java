package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.PurchaseOrderReportData;
import com.citacloud.springboot.contacloud.app.mappers.OrdenCompraMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PurchaseOrderReprintTest {
    @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}

    @Test void reimprimeConLaPlantillaVigenteSinCargarElPdfHistorico(){
        UUID tenant=UUID.randomUUID(),companyId=UUID.randomUUID(),userId=UUID.randomUUID(),orderId=UUID.randomUUID(),supplierId=UUID.randomUUID();
        TenantPrincipal principal=new TenantPrincipal(userId,tenant,companyId,null,"DEMO","Administrador","admin","",true,true,Set.of(),Set.of("ordenes_compra.ver"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities()));

        PurchaseOrderRepository orders=mock(PurchaseOrderRepository.class);SupplierRepository suppliers=mock(SupplierRepository.class);
        EmpresaRepository companies=mock(EmpresaRepository.class);DatosEmpresaRepository companyData=mock(DatosEmpresaRepository.class);
        UsuarioRepository users=mock(UsuarioRepository.class);LocalFileStorageService storage=mock(LocalFileStorageService.class);
        PurchaseOrderPdfService pdf=mock(PurchaseOrderPdfService.class);
        PurchaseOrderService service=new PurchaseOrderService(orders,suppliers,mock(SucursalRepository.class),mock(MonedaRepository.class),
            mock(CondicionPagoRepository.class),mock(ImpuestoRepository.class),mock(ProductRepository.class),mock(PurchaseOrderCalculationService.class),
            mock(PurchaseOrderNumberService.class),mock(OrdenCompraMapper.class),mock(AuditoriaService.class),companies,companyData,users,storage,pdf);

        OffsetDateTime issuedAt=OffsetDateTime.parse("2026-10-03T12:00:00-04:00");
        OrdenCompra order=mock(OrdenCompra.class);when(order.getId()).thenReturn(orderId);when(order.getTenantId()).thenReturn(tenant);
        when(order.getEmpresaId()).thenReturn(companyId);when(order.getProveedorId()).thenReturn(supplierId);when(order.getEstado()).thenReturn(EstadoOrdenCompra.ISSUED);
        when(order.getPdfObjectKey()).thenReturn("tenants/old-template.pdf");when(order.getEmitidaPor()).thenReturn(userId);when(order.getEmitidaEn()).thenReturn(issuedAt);
        when(order.getNumero()).thenReturn("OC-000001");when(order.getProveedorNombre()).thenReturn("Proveedor histórico");
        when(order.getProveedorIdentificacion()).thenReturn("130000001");when(order.getFecha()).thenReturn(LocalDate.of(2026,10,3));
        when(order.getLineas()).thenReturn(List.of());when(orders.findByIdAndTenantIdAndEmpresaId(orderId,tenant,companyId)).thenReturn(Optional.of(order));

        Proveedor supplier=mock(Proveedor.class);when(supplier.getContacto()).thenReturn("Juan Pérez");when(supplier.getTelefono()).thenReturn("809-111-1111");
        when(suppliers.findByIdAndTenantIdAndEmpresaId(supplierId,tenant,companyId)).thenReturn(Optional.of(supplier));
        Empresa company=mock(Empresa.class);when(company.getNombre()).thenReturn("Empresa Demo");when(companies.findByIdAndTenantId(companyId,tenant)).thenReturn(Optional.of(company));
        when(companyData.findByEmpresaId(companyId)).thenReturn(Optional.empty());
        Usuario issuer=new Usuario(tenant,companyId,"admin","Ana","Pérez","hash");when(users.findByIdAndTenantId(userId,tenant)).thenReturn(Optional.of(issuer));
        byte[] currentPdf="%PDF-current".getBytes();when(pdf.generate(any(PurchaseOrderReportData.class))).thenReturn(currentPdf);

        assertThat(service.officialPdf(orderId)).isSameAs(currentPdf);
        ArgumentCaptor<PurchaseOrderReportData> report=ArgumentCaptor.forClass(PurchaseOrderReportData.class);verify(pdf).generate(report.capture());
        assertThat(report.getValue().supplierName()).isEqualTo("Proveedor histórico");
        assertThat(report.getValue().issuedByDisplayName()).isEqualTo("Ana Pérez");
        assertThat(report.getValue().issuedAt()).isEqualTo(issuedAt);
        verify(pdf,never()).getOfficialPdf(any());
        verify(storage,never()).load(eq("tenants/old-template.pdf"),anyLong());
    }
}
