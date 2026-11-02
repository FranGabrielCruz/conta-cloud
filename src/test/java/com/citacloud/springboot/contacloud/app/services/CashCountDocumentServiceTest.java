package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
class CashCountDocumentServiceTest {
    UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),id=UUID.randomUUID(),userId=UUID.randomUUID();
    RecuentoCajaRepository counts=mock(RecuentoCajaRepository.class);EmpresaRepository companies=mock(EmpresaRepository.class);DatosEmpresaRepository data=mock(DatosEmpresaRepository.class);
    CashCountDocumentService service=new CashCountDocumentService(counts,companies,data);SesionCaja session;RecuentoCaja count;Empresa companyData;Caja cashData;Usuario user;
    List<DetalleRecuentoCaja> rows;
    @BeforeEach void fixture(){
        login(tenant,company,true,Set.of());session=mock(SesionCaja.class);when(session.getTenantId()).thenReturn(tenant);when(session.getEmpresaId()).thenReturn(company);when(session.getCajaId()).thenReturn(cash);when(session.getSucursalId()).thenReturn(branch);when(session.getId()).thenReturn(UUID.randomUUID());when(session.getMonedaId()).thenReturn(UUID.randomUUID());when(session.getAbiertoEn()).thenReturn(OffsetDateTime.parse("2026-11-01T12:00:00Z"));
        cashData=mock(Caja.class);when(cashData.getNombre()).thenReturn("Caja histórica");when(session.getCaja()).thenReturn(cashData);
        var b=mock(Sucursal.class);when(b.getNombre()).thenReturn("Principal histórica");when(session.getSucursal()).thenReturn(b);
        var m=mock(Moneda.class);when(m.getCodigoIso()).thenReturn("DOP");when(session.getMoneda()).thenReturn(m);
        user=mock(Usuario.class);when(user.getNombre()).thenReturn("Juan");when(user.getApellido()).thenReturn("Pérez");when(session.getUsuarioApertura()).thenReturn(user);
        count=new RecuentoCaja(tenant,company,"RCJ-000012",session,OffsetDateTime.parse("2026-11-02T00:15:00Z"),userId,new BigDecimal("10500"),new BigDecimal("10495"),"Observación histórica",UUID.randomUUID());ReflectionTestUtils.setField(count,"id",id);when(counts.findByIdAndTenantIdAndEmpresaId(id,tenant,company)).thenReturn(Optional.of(count));
        companyData=mock(Empresa.class);when(companyData.getNombre()).thenReturn("Empresa histórica");when(companyData.getIdentificacionFiscal()).thenReturn("124543678");when(companyData.getZonaHoraria()).thenReturn("America/Santo_Domingo");when(companyData.getPaisCodigo()).thenReturn("DO");when(companies.findByIdAndTenantId(company,tenant)).thenReturn(Optional.of(companyData));when(data.findByEmpresaId(company)).thenReturn(Optional.empty());
        var den=mock(DenominacionCaja.class);when(den.getId()).thenReturn(UUID.randomUUID());when(den.getValor()).thenReturn(new BigDecimal("2000"));when(den.getTipo()).thenReturn(TipoDenominacionCaja.BANKNOTE);rows=List.of(new DetalleRecuentoCaja(tenant,company,id,den,2,1));
    }
    void login(UUID t,UUID e,boolean all,Set<UUID> branches){var p=new TenantPrincipal(userId,t,e,null,"DEMO","Juan Pérez","juan","",true,all,branches,Set.of("recuentos_caja.imprimir"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void capturaZonaHoraImportesYEstadoConfirmadosSinCalcularlos(){service.conservar(count,session,rows);var d=service.documento(count);assertThat(d.createdDateTime()).isEqualTo(LocalDateTime.of(2026,11,1,20,15));assertThat(d.openingDateTime()).isEqualTo(LocalDateTime.of(2026,11,1,8,0));assertThat(d.denominations().getFirst().amount()).isEqualByComparingTo("4000");assertThat(d.countedCash()).isEqualByComparingTo("10495");assertThat(d.difference()).isEqualByComparingTo("-5");assertThat(d.status().name()).isEqualTo("FALTANTE");assertThat(d.performedBy()).isEqualTo("Juan Pérez");}
    @Test void cambiosPosterioresNoModificanElSnapshotNiElPdf(){service.conservar(count,session,rows);String json=count.getDocumentoPos();byte[] before=service.generar(id).contenido();when(companyData.getNombre()).thenReturn("Nombre nuevo");when(cashData.getNombre()).thenReturn("Caja nueva");when(user.getNombre()).thenReturn("Usuario nuevo");assertThat(service.generar(id).contenido()).isEqualTo(before);assertThat(count.getDocumentoPos()).isEqualTo(json);assertThatThrownBy(()->service.conservar(count,session,rows)).isInstanceOf(IllegalStateException.class);assertThat(count.getDocumentoPos()).isEqualTo(json);}
    @Test void tenantEmpresaYSucursalSeValidanAntesDeLeerDocumento(){service.conservar(count,session,rows);login(UUID.randomUUID(),company,true,Set.of());assertThatThrownBy(()->service.documento(count)).isInstanceOf(RecursoNoEncontradoException.class);login(tenant,UUID.randomUUID(),true,Set.of());assertThatThrownBy(()->service.documento(count)).isInstanceOf(RecursoNoEncontradoException.class);login(tenant,company,false,Set.of(UUID.randomUUID()));assertThatThrownBy(()->service.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class);}
    @Test void sesionDeOtraCajaNoSeCaptura(){when(session.getCajaId()).thenReturn(UUID.randomUUID());assertThatThrownBy(()->service.conservar(count,session,rows)).isInstanceOf(RecursoNoEncontradoException.class);assertThat(count.getDocumentoPos()).isNull();}
    @Test void legadoNoInventaIdentidadHistorica(){assertThatThrownBy(()->service.generar(id)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("anterior");verifyNoInteractions(companies,data);}
    @Test void errorDeRenderPosteriorNoAlteraRecuento(){when(companyData.getNombre()).thenReturn("Empresa \uD83D\uDE00");service.conservar(count,session,rows);String snapshot=count.getDocumentoPos();assertThatThrownBy(()->service.generar(id)).isInstanceOf(ReglaNegocioException.class);assertThat(count.getDocumentoPos()).isEqualTo(snapshot);assertThat(count.getDiferencia()).isEqualByComparingTo("-5");assertThat(count.getResult()).isEqualTo(ResultadoRecuentoCaja.SHORTAGE);}
}
