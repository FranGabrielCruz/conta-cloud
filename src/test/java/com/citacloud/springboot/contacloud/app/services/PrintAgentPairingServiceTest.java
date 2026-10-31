package com.citacloud.springboot.contacloud.app.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.PrintAgentPrincipal;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class PrintAgentPairingServiceTest {
    private final VinculacionAgenteImpresionRepository pairings=mock(VinculacionAgenteImpresionRepository.class);
    private final AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);
    private final CredencialAgenteImpresionRepository credentials=mock(CredencialAgenteImpresionRepository.class);
    private final EmpresaRepository companies=mock(EmpresaRepository.class);private final SucursalRepository branches=mock(SucursalRepository.class);
    private final PrintAgentTokenService tokens=new PrintAgentTokenService(new byte[32],new SecureRandom());private final AuditoriaService audit=mock(AuditoriaService.class);
    private final PrintAgentPairingService service=new PrintAgentPairingService(pairings,agents,credentials,companies,branches,tokens,audit,Duration.ofMinutes(15),Duration.ofDays(30),Duration.ofMinutes(1),5);
    private UUID tenant,company,branch,agentId,user;private AgenteImpresion agent;
    @BeforeEach void setup(){tenant=UUID.randomUUID();company=UUID.randomUUID();branch=UUID.randomUUID();agentId=UUID.randomUUID();user=UUID.randomUUID();agent=new AgenteImpresion(tenant,company,branch,"PA-000001","POS Caja Principal",user);set(agent,"id",agentId);when(agents.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));when(pairings.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));when(credentials.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));}
    @Test void pairingValidoActivaAgenteConsumeCodigoYCreaCredencial(){String code="847291";var pairing=new VinculacionAgenteImpresion(tenant,company,agentId,tokens.hashCodigo(code),OffsetDateTime.now().plusMinutes(10),user);when(pairings.bloquearPorHash(tokens.hashCodigo(code))).thenReturn(Optional.of(pairing));when(agents.bloquearAgente(agentId,tenant)).thenReturn(Optional.of(agent));TokenAgenteImpresionDto result=service.vincular(new PairingAgenteImpresionInput(code,new PairingAgenteImpresionInput.Dispositivo("CAJA-01","1.0.0","Windows")));assertThat(agent.getEstado()).isEqualTo(EstadoAgenteImpresion.ACTIVE);assertThat(pairing.getConsumidoEn()).isNotNull();assertThat(result.agentId()).isEqualTo(agentId);assertThat(result.accessToken()).doesNotContain(code);ArgumentCaptor<CredencialAgenteImpresion> saved=ArgumentCaptor.forClass(CredencialAgenteImpresion.class);verify(credentials).saveAndFlush(saved.capture());assertThat(saved.getValue().getRevocadaEn()).isNull();verify(audit).registrarPara(eq(company),isNull(),eq("PairingSucceeded"),eq("PrintAgent"),eq(agentId),anyString());}
    @Test void codigoConsumidoEsRechazadoSinCrearOtraCredencial(){String code="111111";var pairing=new VinculacionAgenteImpresion(tenant,company,agentId,tokens.hashCodigo(code),OffsetDateTime.now().plusMinutes(10),user);pairing.consumir(OffsetDateTime.now());when(pairings.bloquearPorHash(tokens.hashCodigo(code))).thenReturn(Optional.of(pairing));assertThatThrownBy(()->service.vincular(new PairingAgenteImpresionInput(code,new PairingAgenteImpresionInput.Dispositivo(null,null,null)))).isInstanceOf(VinculacionAgenteException.class).hasMessage("No fue posible vincular el dispositivo.");verifyNoInteractions(credentials);verify(agents,never()).bloquearAgente(any(),any());}
    @Test void agenteRevocadoNoAutentica(){String access=tokens.nuevoToken(tenant);var credential=new CredencialAgenteImpresion(tenant,company,agentId,tokens.hashAcceso(access),OffsetDateTime.now().plusMinutes(5),tokens.hashRenovacion(tokens.nuevoToken(tenant)),OffsetDateTime.now().plusDays(1),null);agent.revocar(user);when(credentials.findByHashAcceso(tokens.hashAcceso(access))).thenReturn(Optional.of(credential));when(agents.findByIdAndTenantIdAndEmpresaId(agentId,tenant,company)).thenReturn(Optional.of(agent));assertThatThrownBy(()->service.autenticar(access)).isInstanceOf(VinculacionAgenteException.class);}
    @Test void heartbeatSeCoalesceDentroDelIntervalo(){agent.vincular("CAJA-01","1.0","Windows");when(agents.bloquearAgente(agentId,tenant)).thenReturn(Optional.of(agent));service.heartbeat(new PrintAgentPrincipal(agentId,tenant,company,branch,"PA-000001","POS"),"1.0");verify(agents,never()).save(any());}
    private static void set(Object target,String name,Object value){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}catch(ReflectiveOperationException ex){throw new AssertionError(ex);}}
}
