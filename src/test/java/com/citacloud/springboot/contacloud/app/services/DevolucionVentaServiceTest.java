package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.LineaFacturaVenta;
import com.citacloud.springboot.contacloud.app.errors.ErrorCode;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DevolucionVentaServiceTest {
    @Test void consultaCincoFacturasPorPaginaEnBackend(){
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID();
        var principal=new TenantPrincipal(UUID.randomUUID(),tenant,empresa,null,"DEMO","Admin","admin","",true,true,Set.of(),Set.of("devoluciones_venta.ver"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        FacturaVentaRepository facturas=mock(FacturaVentaRepository.class);
        when(facturas.buscarParaDevolucion(any(),any(),anyBoolean(),anyCollection(),anyString(),anyString(),isNull(),isNull(),anyString(),any(Pageable.class)))
                .thenReturn(Page.empty());
        DevolucionVentaService service=nuevoServicio(facturas);

        try{
            service.buscar("",null,null,null,1);

            var pageable=org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(facturas).buscarParaDevolucion(eq(tenant),eq(empresa),eq(true),anyCollection(),eq(""),eq(""),isNull(),isNull(),eq("TODAS"),pageable.capture());
            assertEquals(1,pageable.getValue().getPageNumber());
            assertEquals(5,pageable.getValue().getPageSize());
        }finally{
            SecurityContextHolder.clearContext();
        }
    }

    @Test void noBuscaFacturasFueraDelTenantAutenticado(){
        UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),factura=UUID.randomUUID();
        var principal=new TenantPrincipal(UUID.randomUUID(),tenant,empresa,null,"DEMO","Admin","admin","",true,true,Set.of(),Set.of("devoluciones_venta.crear"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        FacturaVentaRepository facturas=mock(FacturaVentaRepository.class);
        DevolucionVentaService service=nuevoServicio(facturas);
        ReglaNegocioException error=assertThrows(ReglaNegocioException.class,()->service.preparar(factura));
        assertEquals(ErrorCode.RETURN_INVOICE_NOT_FOUND,error.getCode());verify(facturas).findByIdAndTenantIdAndEmpresaId(factura,tenant,empresa);
        SecurityContextHolder.clearContext();
    }

    @Test void calculaDevolucionParcialConSnapshotsHistoricos(){
        LineaFacturaVenta linea=linea("10","1000","100","50","171","1121");
        var resultado=DevolucionVentaService.calcular(linea,new BigDecimal("2.5"));
        assertEquals(new BigDecimal("250.0000"),resultado.bruto());
        assertEquals(new BigDecimal("25.0000"),resultado.descuento());
        assertEquals(new BigDecimal("12.5000"),resultado.recargo());
        assertEquals(new BigDecimal("42.7500"),resultado.impuesto());
        assertEquals(new BigDecimal("280.2500"),resultado.total());
    }

    @Test void calculaDevolucionTotalSinRecalcularLaReglaFiscalActual(){
        LineaFacturaVenta linea=linea("3","299.97","9.99","0","52.20","342.18");
        var resultado=DevolucionVentaService.calcular(linea,new BigDecimal("3"));
        assertEquals(new BigDecimal("299.9700"),resultado.bruto());
        assertEquals(new BigDecimal("9.9900"),resultado.descuento());
        assertEquals(new BigDecimal("52.2000"),resultado.impuesto());
        assertEquals(new BigDecimal("342.1800"),resultado.total());
    }

    private static LineaFacturaVenta linea(String cantidad,String bruto,String descuento,String recargo,String impuesto,String total){
        LineaFacturaVenta linea=nuevaLinea();
        ReflectionTestUtils.setField(linea,"cantidad",new BigDecimal(cantidad));ReflectionTestUtils.setField(linea,"subtotalBruto",new BigDecimal(bruto));
        ReflectionTestUtils.setField(linea,"descuento",new BigDecimal(descuento));ReflectionTestUtils.setField(linea,"recargo",new BigDecimal(recargo));
        ReflectionTestUtils.setField(linea,"impuesto",new BigDecimal(impuesto));ReflectionTestUtils.setField(linea,"total",new BigDecimal(total));
        return linea;
    }

    private static LineaFacturaVenta nuevaLinea(){
        try{var constructor=LineaFacturaVenta.class.getDeclaredConstructor();constructor.setAccessible(true);return constructor.newInstance();}
        catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }

    private static DevolucionVentaService nuevoServicio(FacturaVentaRepository facturas){
        return new DevolucionVentaService(facturas,mock(LineaFacturaVentaRepository.class),mock(DevolucionVentaRepository.class),
                mock(LineaDevolucionVentaRepository.class),mock(MotivoDevolucionVentaRepository.class),mock(AlmacenRepository.class),mock(NotaCreditoVentaRepository.class),
                mock(CreditoClienteRepository.class),mock(InventoryMovementService.class),mock(IdempotentOperationService.class),mock(AuditoriaService.class),mock(JdbcTemplate.class),new SimpleMeterRegistry());
    }
}
