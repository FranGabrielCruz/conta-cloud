package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UltimasFacturasServiceTest {
    private final FacturaVentaRepository facturas=mock(FacturaVentaRepository.class);
    private final LineaFacturaVentaRepository lineas=mock(LineaFacturaVentaRepository.class);
    private final PagoVentaRepository pagos=mock(PagoVentaRepository.class);
    private final AplicacionPagoVentaRepository aplicaciones=mock(AplicacionPagoVentaRepository.class);
    private final UltimasFacturasService service=new UltimasFacturasService(facturas,lineas,pagos,aplicaciones);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID(),sucursal=UUID.randomUUID();

    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void buscaHistoricoNormalizandoIdentificacionConPaginacionFija(){autenticar(false,Set.of(sucursal));
        when(facturas.buscarHistorico(eq(tenant),eq(empresa),eq(false),eq(Set.of(sucursal)),eq("031-0543974-3"),eq("03105439743"),
                isNull(),isNull(),isNull(),any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0,5)));
        service.buscar(" 031-0543974-3 ",null,null,null,0);
        verify(facturas).buscarHistorico(eq(tenant),eq(empresa),eq(false),eq(Set.of(sucursal)),eq("031-0543974-3"),eq("03105439743"),
                isNull(),isNull(),isNull(),argThat(p->p.getPageNumber()==0&&p.getPageSize()==5));}

    @Test void noRestringePorCajeroYPermiteAlcanceMultisucursal(){autenticar(true,Set.of());
        when(facturas.buscarHistorico(eq(tenant),eq(empresa),eq(true),anyCollection(),eq("fac-000125"),eq("000125"),isNull(),isNull(),
                eq(EstadoFacturaVenta.CONFIRMED),any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(2,5)));
        service.buscar("FAC-000125",null,null,EstadoFacturaVenta.CONFIRMED,2);
        verify(facturas).buscarHistorico(eq(tenant),eq(empresa),eq(true),anyCollection(),eq("fac-000125"),eq("000125"),isNull(),isNull(),
                eq(EstadoFacturaVenta.CONFIRMED),argThat(p->p.getPageNumber()==2&&p.getPageSize()==5));}

    @Test void rechazaRangoInvertidoAntesDeConsultar(){autenticar(true,Set.of());
        assertThatThrownBy(()->service.buscar("",LocalDate.of(2026,10,20),LocalDate.of(2026,10,19),null,0))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("La fecha inicial no puede ser posterior a la fecha final.");
        verifyNoInteractions(facturas);}

    @Test void detalleAisladoPorTenantEmpresaYSucursal(){autenticar(false,Set.of(sucursal));FacturaVenta factura=factura(sucursal);
        when(facturas.findByIdAndTenantIdAndEmpresaId(factura.getId(),tenant,empresa)).thenReturn(Optional.of(factura));
        when(lineas.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(factura.getId(),tenant,empresa)).thenReturn(List.of());
        when(pagos.findByFacturaIdAndTenantIdAndEmpresaId(factura.getId(),tenant,empresa)).thenReturn(Optional.empty());
        var detalle=service.detalle(factura.getId());
        assertThat(detalle.numero()).isEqualTo("FAC-000001");assertThat(detalle.lineas()).isEmpty();assertThat(detalle.pagos()).isEmpty();}

    @Test void ocultaFacturaDeSucursalNoAutorizadaComoNoEncontrada(){autenticar(false,Set.of(sucursal));FacturaVenta factura=factura(UUID.randomUUID());
        when(facturas.findByIdAndTenantIdAndEmpresaId(factura.getId(),tenant,empresa)).thenReturn(Optional.of(factura));
        assertThatThrownBy(()->service.detalle(factura.getId())).isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Factura no encontrada.");
        verifyNoInteractions(lineas,pagos,aplicaciones);}

    private void autenticar(boolean todas,Set<UUID> sucursales){var principal=new TenantPrincipal(usuario,tenant,empresa,null,"DEMO","Admin","admin","",true,todas,sucursales,Set.of("facturas.ver","facturas.pdf"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
    private FacturaVenta factura(UUID branch){FacturaVenta f=new FacturaVenta(tenant,empresa,"FAC-000001",LocalDate.of(2026,10,19),usuario,
            UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),branch,UUID.randomUUID(),null,"Consumidor final","03105439743","Admin",
            UUID.randomUUID(),"DOP","RD$",null,new BigDecimal("100"),new BigDecimal("18"),new BigDecimal("118"),new BigDecimal("120"),UUID.randomUUID());
        ReflectionTestUtils.setField(f,"id",UUID.randomUUID());return f;}
}
