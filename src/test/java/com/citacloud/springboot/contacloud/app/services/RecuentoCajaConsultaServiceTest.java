package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecuentoCajaConsultaServiceTest {
    private final RecuentoCajaRepository recuentos=mock(RecuentoCajaRepository.class);
    private final RecuentoCajaService detalle=mock(RecuentoCajaService.class);
    private final CashRegisterRepository cajas=mock(CashRegisterRepository.class);
    private final SucursalRepository sucursales=mock(SucursalRepository.class);
    private final UsuarioRepository usuarios=mock(UsuarioRepository.class);
    private final EmpresaRepository empresas=mock(EmpresaRepository.class);
    private final RecuentoCajaConsultaService service=new RecuentoCajaConsultaService(recuentos,detalle,cajas,sucursales,usuarios,empresas);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),actor=UUID.randomUUID();

    @BeforeEach void preparar(){autenticar(true,Set.of());Empresa e=mock(Empresa.class);when(e.getZonaHoraria()).thenReturn("America/Santo_Domingo");when(empresas.findByIdAndTenantId(empresa,tenant)).thenReturn(Optional.of(e));}
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void combinaFiltrosFechaCajaSucursalUsuarioResultadoYPagina(){
        UUID sucursal=UUID.randomUUID(),caja=UUID.randomUUID(),usuario=UUID.randomUUID();
        Caja c=mock(Caja.class);when(c.getSucursalId()).thenReturn(sucursal);when(cajas.findByIdAndTenantIdAndEmpresaId(caja,tenant,empresa)).thenReturn(Optional.of(c));
        Usuario u=mock(Usuario.class);when(u.getEmpresaId()).thenReturn(empresa);when(usuarios.findByIdAndTenantId(usuario,tenant)).thenReturn(Optional.of(u));
        when(recuentos.buscarAdministrativo(any(),any(),anyBoolean(),anyCollection(),anyString(),
            anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),any())).thenReturn(Page.empty());

        service.buscar(" RCJ-0002 ",sucursal,caja,usuario,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),ResultadoRecuentoCaja.SHORTAGE,2,20);

        verify(recuentos).buscarAdministrativo(eq(tenant),eq(empresa),eq(true),anyCollection(),eq("RCJ-0002"),
            eq(true),eq(sucursal),eq(true),eq(caja),eq(true),eq(usuario),
            eq(true),eq(OffsetDateTime.parse("2026-10-01T00:00-04:00")),
            eq(true),eq(OffsetDateTime.parse("2026-11-01T00:00-04:00")),
            eq(true),eq(ResultadoRecuentoCaja.SHORTAGE),
            argThat(p->p.getPageNumber()==2&&p.getPageSize()==20&&p.getSort().getOrderFor("contadoEn").getDirection()==Sort.Direction.DESC&&p.getSort().getOrderFor("id").getDirection()==Sort.Direction.DESC));
    }

    @Test void conservaImportesYResultadoHistoricosDelSnapshot(){
        RecuentoCaja r=mock(RecuentoCaja.class);Caja c=mock(Caja.class);Sucursal s=mock(Sucursal.class);Moneda m=mock(Moneda.class);Usuario u=mock(Usuario.class);SesionCaja sesion=mock(SesionCaja.class);
        when(r.getId()).thenReturn(UUID.randomUUID());when(r.getNumero()).thenReturn("RCJ-000001");when(r.getSesionId()).thenReturn(UUID.randomUUID());when(r.getSesion()).thenReturn(sesion);when(sesion.getCodigoVisible()).thenReturn("TURNO-1");when(r.getCaja()).thenReturn(c);when(c.getNombre()).thenReturn("caja1");when(r.getSucursal()).thenReturn(s);when(s.getNombre()).thenReturn("Principal");when(r.getMoneda()).thenReturn(m);when(m.getCodigoIso()).thenReturn("DOP");when(m.getSimbolo()).thenReturn("RD$");when(r.getUsuario()).thenReturn(u);when(u.getUsuario()).thenReturn("admin");when(r.getContadoEn()).thenReturn(OffsetDateTime.now());when(r.getEsperado()).thenReturn(new BigDecimal("10000"));when(r.getContado()).thenReturn(new BigDecimal("9900"));when(r.getDiferencia()).thenReturn(new BigDecimal("-100"));when(r.getResult()).thenReturn(ResultadoRecuentoCaja.SHORTAGE);
        when(recuentos.buscarAdministrativo(any(),any(),anyBoolean(),anyCollection(),anyString(),
            anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),anyBoolean(),any(),any())).thenReturn(new PageImpl<>(List.of(r)));

        var fila=service.buscar("",null,null,null,null,null,null,0,10).getContent().getFirst();
        assertThat(fila.esperado()).isEqualByComparingTo("10000");assertThat(fila.contado()).isEqualByComparingTo("9900");assertThat(fila.diferencia()).isEqualByComparingTo("-100");assertThat(fila.resultado()).isEqualTo(ResultadoRecuentoCaja.SHORTAGE);
    }

    @Test void rechazaSucursalNoAutorizadaYFechasInvalidas(){UUID permitida=UUID.randomUUID(),otra=UUID.randomUUID();autenticar(false,Set.of(permitida));assertThatThrownBy(()->service.buscar("",otra,null,null,null,null,null,0,10)).isInstanceOf(RecursoNoEncontradoException.class);assertThatThrownBy(()->service.buscar("",null,null,null,LocalDate.of(2026,10,2),LocalDate.of(2026,10,1),null,0,10)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("fecha desde");}

    @Test void aceptaSoloTamanosAdministrativos(){assertThatThrownBy(()->service.buscar("",null,null,null,null,null,null,0,5)).isInstanceOf(ReglaNegocioException.class).hasMessage("Paginación inválida.");}

    private void autenticar(boolean todas,Set<UUID> ramas){var p=new TenantPrincipal(actor,tenant,empresa,null,"DEMO","Admin","admin","",true,todas,ramas,Set.of("recuentos_caja.ver"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
}
