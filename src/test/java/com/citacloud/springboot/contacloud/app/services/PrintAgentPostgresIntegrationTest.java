package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.AgenteImpresionMapper;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Duration;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses only the existing development database. Each fixture has random IDs and is removed in finally/AfterEach. */
@EnabledIfEnvironmentVariable(named="CONTACLOUD_POSTGRES_TESTS",matches="true")
class PrintAgentPostgresIntegrationTest {
    static PGSimpleDataSource ds;
    static UUID tenant,empresa,sucursal,moneda,usuario;
    static LocalContainerEntityManagerFactoryBean factory;
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    final List<UUID> cajas=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    JdbcTemplate jdbc;
    final List<UUID> agentesExtra=new ArrayList<>();
    UUID sucursalExtra,cajaExtra;

    @BeforeAll static void conectarYValidarModelo() throws Exception {
        ds=new PGSimpleDataSource();ds.setURL(System.getenv().getOrDefault("DB_URL","jdbc:postgresql://localhost:5432/conta_cloud"));ds.setUser(System.getenv().getOrDefault("DB_USERNAME","postgres"));ds.setPassword(System.getenv("DB_PASSWORD"));
        try(var c=ds.getConnection();var s=c.createStatement()){
            try(var r=s.executeQuery("select current_database()")){r.next();assertThat(r.getString(1)).isEqualTo("conta_cloud");}
            try(var r=s.executeQuery("select e.tenant_id,e.id,s.id,m.id,u.id from empresas e join sucursales s on s.empresa_id=e.id join monedas m on m.empresa_id=e.id join usuario_empresa ue on ue.empresa_id=e.id and ue.activo=true join usuarios u on u.id=ue.usuario_id where e.activo=true and s.activo=true and m.activo=true and u.activo=true order by e.id,s.id,m.id,u.id limit 1")){
                assertThat(r.next()).as("Debe existir una empresa configurada con sucursal, moneda y membresía de usuario activos").isTrue();
                tenant=r.getObject(1,UUID.class);empresa=r.getObject(2,UUID.class);sucursal=r.getObject(3,UUID.class);moneda=r.getObject(4,UUID.class);usuario=r.getObject(5,UUID.class);
            }
        }
        factory=new LocalContainerEntityManagerFactoryBean();var vendor=new HibernateJpaVendorAdapter();vendor.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");factory.setJpaVendorAdapter(vendor);factory.setDataSource(ds);factory.setPackagesToScan("com.citacloud.springboot.contacloud.app.models");factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));factory.afterPropertiesSet();
    }
    @AfterAll static void cerrar(){if(factory!=null)factory.destroy();}
    @BeforeEach void preparar(){
        jdbc=new JdbcTemplate(ds);
        for(UUID id:cajas)jdbc.update("insert into cajas(id,tenant_id,empresa_id,sucursal_id,moneda_id,codigo,nombre,creado_por) values(?,?,?,?,?,?,?,?)",id,tenant,empresa,sucursal,moneda,"TEST-"+id.toString().substring(0,20),"TEST multicaja "+id,usuario);
        for(UUID id:List.of(a,b))jdbc.update("insert into print_agents(id,tenant_id,empresa_id,sucursal_id,display_code,name,status,created_by,print_on_screen) values(?,?,?,?,?,?,'ACTIVE',?,?)",id,tenant,empresa,sucursal,"TEST-"+id.toString().substring(0,20),"TEST multicaja "+id,usuario,id.equals(a));
    }
    @AfterEach void limpiar(){
        if(jdbc==null)return;
        jdbc.update("delete from billing_print_jobs where tenant_id=? and empresa_id=? and agent_id in (?,?)",tenant,empresa,a,b);
        jdbc.update("delete from print_agents where tenant_id=? and empresa_id=? and id in (?,?)",tenant,empresa,a,b);
        for(UUID id:agentesExtra){jdbc.update("delete from billing_print_jobs where tenant_id=? and empresa_id=? and agent_id=?",tenant,empresa,id);jdbc.update("delete from print_agents where tenant_id=? and empresa_id=? and id=?",tenant,empresa,id);}
        for(UUID id:cajas)jdbc.update("delete from cajas where tenant_id=? and empresa_id=? and id=?",tenant,empresa,id);
        if(cajaExtra!=null)jdbc.update("delete from cajas where tenant_id=? and empresa_id=? and id=?",tenant,empresa,cajaExtra);
        if(sucursalExtra!=null)jdbc.update("delete from sucursales where empresa_id=? and id=?",empresa,sucursalExtra);
        SecurityContextHolder.clearContext();
    }

    @Test void jpaPersisteVariasCajasYPreferenciaIndependiente(){
        try(var em=factory.getObject().createEntityManager()){
            em.getTransaction().begin();
            var agent=em.createQuery("select a from AgenteImpresion a where a.id=:id and a.tenantId=:t and a.empresaId=:e",AgenteImpresion.class).setParameter("id",a).setParameter("t",tenant).setParameter("e",empresa).getSingleResult();
            var seleccion=em.createQuery("select c from Caja c where c.id in :ids and c.tenantId=:t and c.empresaId=:e",Caja.class).setParameter("ids",cajas).setParameter("t",tenant).setParameter("e",empresa).getResultList();
            agent.reemplazarCajas(new LinkedHashSet<>(seleccion));agent.configurar(agent.getNombre(),sucursal,false);em.getTransaction().commit();em.clear();
            var guardado=em.createQuery("select a from AgenteImpresion a where a.id=:id and a.tenantId=:t and a.empresaId=:e",AgenteImpresion.class).setParameter("id",a).setParameter("t",tenant).setParameter("e",empresa).getSingleResult();
            assertThat(guardado.getCajas()).hasSize(3);assertThat(guardado.isPrintOnScreen()).isFalse();
        }
        assertThat(jdbc.queryForObject("select print_on_screen from print_agents where tenant_id=? and empresa_id=? and id=?",Boolean.class,tenant,empresa,b)).isFalse();
    }

    @Test void crearYEditarConRepositoriosRealesIncluyeCambiosDeSucursalSinNuevoPairing(){
        sucursalExtra=UUID.randomUUID();cajaExtra=UUID.randomUUID();
        jdbc.update("insert into sucursales(id,empresa_id,codigo,nombre) values(?,?,?,?)",sucursalExtra,empresa,"TEST-"+sucursalExtra.toString().substring(0,20),"TEST sucursal multicaja "+sucursalExtra);
        jdbc.update("insert into cajas(id,tenant_id,empresa_id,sucursal_id,moneda_id,codigo,nombre,creado_por) values(?,?,?,?,?,?,?,?)",cajaExtra,tenant,empresa,sucursalExtra,moneda,"TEST-"+cajaExtra.toString().substring(0,20),"TEST caja multicaja "+cajaExtra,usuario);
        assertThatThrownBy(()->asignar(a,cajaExtra)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var p=new TenantPrincipal(usuario,tenant,empresa,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("agentes_impresion.crear","agentes_impresion.editar","agentes_impresion.ver","facturas.configurar_impresion"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
        try(var em=factory.getObject().createEntityManager()){
            var repos=new JpaRepositoryFactory(em);var agentes=repos.getRepository(AgenteImpresionRepository.class);var cajasRepo=repos.getRepository(CashRegisterRepository.class);
            var numbers=mock(PrintAgentNumberService.class);when(numbers.siguiente(tenant,empresa)).thenReturn("TEST-"+UUID.randomUUID().toString().substring(0,20));
            var pairings=mock(VinculacionAgenteImpresionRepository.class);var credentials=mock(CredencialAgenteImpresionRepository.class);
            var service=new PrintAgentAdminService(cajasRepo,agentes,pairings,credentials,repos.getRepository(SucursalRepository.class),new AgenteImpresionMapper(),numbers,mock(PrintAgentTokenService.class),mock(AuditoriaService.class),new PrintAgentAssignmentService(cajasRepo,agentes),mock(JdbcTemplate.class),Duration.ofMinutes(10),Duration.ofMinutes(2));
            em.getTransaction().begin();var creado=service.crear(new CrearAgenteImpresionInput("TEST agente CRUD",sucursal,List.of(cajas.get(0),cajas.get(1)),false));agentesExtra.add(creado.id());em.getTransaction().commit();
            assertThat(creado.cajas()).hasSize(2);assertThat(creado.printOnScreen()).isFalse();
            em.getTransaction().begin();var editado=service.editar(creado.id(),new EditarAgenteImpresionInput("TEST editado CRUD",sucursalExtra,List.of(cajaExtra),true,creado.version()));em.getTransaction().commit();
            assertThat(editado.sucursalId()).isEqualTo(sucursalExtra);assertThat(editado.cajas()).extracting(CajaAgenteImpresionDto::id).containsExactly(cajaExtra);assertThat(editado.printOnScreen()).isTrue();verifyNoInteractions(pairings,credentials);
        }
    }

    @Test void clavesCompuestasYEstadoDerivadoImpidenCrucesYBypass(){
        asignar(a,cajas.getFirst());
        assertThatThrownBy(()->asignar(a,cajas.getFirst())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("insert into print_agent_cash_registers(tenant_id,empresa_id,sucursal_id,agent_id,cash_register_id) values(?,?,?,?,?)",UUID.randomUUID(),empresa,sucursal,b,cajas.get(1))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("insert into print_agent_cash_registers(tenant_id,empresa_id,sucursal_id,agent_id,cash_register_id) values(?,?,?,?,?)",tenant,empresa,UUID.randomUUID(),b,cajas.get(1))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("insert into print_agent_cash_registers(tenant_id,empresa_id,sucursal_id,agent_id,cash_register_id,agent_status) values(?,?,?,?,?,'REVOKED')",tenant,empresa,sucursal,b,cajas.getFirst())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void asignacionConcurrenteSoloPermiteUnDestinoActivo() throws Exception {
        competir((c,id)->{try(var s=c.prepareStatement("insert into print_agent_cash_registers(tenant_id,empresa_id,sucursal_id,agent_id,cash_register_id) values(?,?,?,?,?)")){s.setObject(1,tenant);s.setObject(2,empresa);s.setObject(3,sucursal);s.setObject(4,id);s.setObject(5,cajas.getFirst());s.executeUpdate();}});
        assertThat(jdbc.queryForObject("select count(*) from print_agent_cash_registers where tenant_id=? and empresa_id=? and cash_register_id=? and agent_status='ACTIVE'",Integer.class,tenant,empresa,cajas.getFirst())).isEqualTo(1);
    }

    @Test void activacionConcurrenteDePendientesTambienEsExclusiva() throws Exception {
        jdbc.update("update print_agents set status='PENDING' where tenant_id=? and empresa_id=? and id in (?,?)",tenant,empresa,a,b);
        asignar(a,cajas.getFirst());asignar(b,cajas.getFirst());
        competir((c,id)->{try(var s=c.prepareStatement("update print_agents set status='ACTIVE' where tenant_id=? and empresa_id=? and id=?")){s.setObject(1,tenant);s.setObject(2,empresa);s.setObject(3,id);s.executeUpdate();}});
        assertThat(jdbc.queryForObject("select count(*) from print_agent_cash_registers where tenant_id=? and empresa_id=? and cash_register_id=? and agent_status='ACTIVE'",Integer.class,tenant,empresa,cajas.getFirst())).isEqualTo(1);
    }

    @Test void revocacionLiberaLaCajaSinEliminarLaRelacionHistorica(){
        asignar(a,cajas.getFirst());jdbc.update("update print_agents set status='REVOKED' where tenant_id=? and empresa_id=? and id=?",tenant,empresa,a);asignar(b,cajas.getFirst());
        assertThat(jdbc.queryForObject("select agent_status from print_agent_cash_registers where tenant_id=? and empresa_id=? and agent_id=? and cash_register_id=?",String.class,tenant,empresa,a,cajas.getFirst())).isEqualTo("REVOKED");
    }

    @Test void trabajosSoloDeCajasAsignadasYConfirmacionHistoricaTrasRetirarCaja(){
        asignar(a,cajas.get(0));asignar(a,cajas.get(1));asignar(b,cajas.get(2));
        UUID j1=trabajo(a,cajas.get(0)),j2=trabajo(a,cajas.get(1));trabajo(a,cajas.get(2));
        var audit=mock(AuditoriaService.class);var service=servicio(a,audit);var principal=principal(a);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var primero=tx.execute(s->service.siguiente(principal).orElseThrow());assertThat(primero.id()).isIn(j1,j2);
        UUID retirada=primero.id().equals(j1)?cajas.get(0):cajas.get(1);
        jdbc.update("delete from print_agent_cash_registers where tenant_id=? and empresa_id=? and agent_id=? and cash_register_id=?",tenant,empresa,a,retirada);
        tx.executeWithoutResult(s->service.completar(principal,primero.id(),primero.claimId(),true));
        tx.executeWithoutResult(s->service.completar(principal,primero.id(),primero.claimId(),true));
        assertThat(tx.execute(s->service.siguiente(principal).orElseThrow()).id()).isIn(j1,j2).isNotEqualTo(primero.id());
        var sinPendientes=tx.execute(s->service.siguiente(principal));assertThat(sinPendientes).isEmpty();
    }

    @Test void trescientasSolicitudesConcurrentesSeReservanUnaSolaVezYNoCruzanAgentes() throws Exception {
        asignar(a,cajas.get(0));asignar(a,cajas.get(1));asignar(b,cajas.get(2));
        for(int i=0;i<100;i++){trabajo(a,cajas.get(0));trabajo(a,cajas.get(1));trabajo(b,cajas.get(2));}
        Set<UUID> atendidas=ConcurrentHashMap.newKeySet();
        try(var pool=Executors.newFixedThreadPool(3)){
            List<Future<Integer>> futuros=new ArrayList<>();
            for(UUID id:List.of(a,a,b))futuros.add(pool.submit(()->{var service=servicio(id,mock(AuditoriaService.class));var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));int total=0;while(true){var job=tx.execute(s->service.siguiente(principal(id)));if(job.isEmpty())return total;assertThat(atendidas.add(job.get().id())).isTrue();total++;}}));
            int total=0;for(var f:futuros)total+=f.get(120,TimeUnit.SECONDS);assertThat(total).isEqualTo(300);
        }
        assertThat(atendidas).hasSize(300);
        for(UUID caja:cajas)assertThat(jdbc.queryForObject("select count(*) from billing_print_jobs where tenant_id=? and empresa_id=? and cash_register_id=? and status='PROCESSING'",Integer.class,tenant,empresa,caja)).isEqualTo(100);
    }

    @Test void salidaPdfNoInsertaTrabajosYModoImpresoraEncolaSoloUnoPorIntentoEnCadaCaja(){
        for(UUID caja:cajas)asignar(a,caja);
        var principal=new TenantPrincipal(usuario,tenant,empresa,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("facturas.pdf"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        var em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        var repos=new JpaRepositoryFactory(em);var agents=repos.getRepository(AgenteImpresionRepository.class);
        var invoices=mock(FacturaVentaRepository.class);var pdf=mock(FacturaVentaPdfService.class);
        var service=new BillingPrintService(jdbc,invoices,new PrintAgentRoutingService(repos.getRepository(CashRegisterRepository.class),agents),pdf,agents,mock(AuditoriaService.class));
        var tx=new TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        var facturas=new ArrayList<UUID>();
        for(UUID caja:cajas){
            UUID id=UUID.randomUUID();facturas.add(id);var f=mock(FacturaVenta.class);when(f.getCajaId()).thenReturn(caja);when(f.getSucursalId()).thenReturn(sucursal);
            when(invoices.findByIdAndTenantIdAndEmpresaId(id,tenant,empresa)).thenReturn(Optional.of(f));
            var salida=tx.execute(t->service.solicitar(id,UUID.randomUUID()));
            assertThat(salida.printOnScreen()).isTrue();assertThat(salida.trabajoId()).isNull();
        }
        assertThat(jdbc.queryForObject("select count(*) from billing_print_jobs where tenant_id=? and empresa_id=? and agent_id=?",Integer.class,tenant,empresa,a)).isZero();
        verifyNoInteractions(pdf);
        jdbc.update("update print_agents set print_on_screen=false where tenant_id=? and empresa_id=? and id=?",tenant,empresa,a);
        for(UUID id:facturas){
            when(pdf.generarParaImpresion(id)).thenReturn(new ArchivoPdfDto("ticket.pdf",new byte[]{1}));
            UUID intento=UUID.randomUUID();var salida=tx.execute(t->service.solicitar(id,intento));
            assertThat(salida.printOnScreen()).isFalse();assertThat(salida.trabajoId()).isNotNull();
            var repetida=tx.execute(t->service.solicitar(id,intento));assertThat(repetida).isEqualTo(salida);
            verify(pdf).generarParaImpresion(id);
        }
        assertThat(jdbc.queryForObject("select count(*) from billing_print_jobs where tenant_id=? and empresa_id=? and agent_id=?",Integer.class,tenant,empresa,a)).isEqualTo(3);
        verify(pdf,never()).generar(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(TipoDocumentoImpresion.class)
    void politicaGlobalAplicaAmbosModosATresCajasYConservaTipo(TipoDocumentoImpresion tipo){
        for(UUID caja:cajas)asignar(a,caja);
        var principal=new TenantPrincipal(usuario,tenant,empresa,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("PRINT_TEST"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        var em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        var repos=new JpaRepositoryFactory(em);var agents=repos.getRepository(AgenteImpresionRepository.class);
        var router=new BillingPrintRouter(jdbc,new PrintAgentRoutingService(repos.getRepository(CashRegisterRepository.class),agents),agents,mock(AuditoriaService.class));
        var tx=new TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        var rendered=new AtomicInteger();var documentos=new ArrayList<BillingPrintRouter.Documento>();
        Map<UUID,BillingPrintRouter.Documento> stored=new HashMap<>();
        var registrations=Arrays.stream(TipoDocumentoImpresion.values()).map(t->new BillingPrintDocumentRegistry.Registration(t,"PRINT_TEST",id->{var d=stored.get(id);if(d==null)throw new RecursoNoEncontradoException("Documento no encontrado.");return new BillingPrintDocumentRegistry.Document(d.sucursalId(),d.cajaId(),()->new ArchivoPdfDto("normal.pdf",new byte[]{2}),d.ticket());})).toList();
        var application=new BillingPrintApplicationService(new BillingPrintDocumentRegistry(registrations),router);
        for(UUID caja:cajas){
            var d=new BillingPrintRouter.Documento(tipo,UUID.randomUUID(),sucursal,caja,()->{rendered.incrementAndGet();return new ArchivoPdfDto("ticket.pdf",new byte[]{1});});documentos.add(d);stored.put(d.id(),d);
            var result=tx.execute(t->application.imprimir(tipo,d.id(),UUID.randomUUID()));
            assertThat(result.outputMode()).isEqualTo(BillingPrintOutputMode.SCREEN);assertThat(result.output().trabajoId()).isNull();assertThat(result.screenDocument().contenido()).containsExactly((byte)2);
        }
        assertThat(rendered.get()).isZero();assertThat(jdbc.queryForObject("select count(*) from billing_print_jobs where tenant_id=? and empresa_id=? and agent_id=?",Integer.class,tenant,empresa,a)).isZero();
        jdbc.update("update print_agents set print_on_screen=false where tenant_id=? and empresa_id=? and id=?",tenant,empresa,a);
        for(var d:documentos){
            UUID intento=UUID.randomUUID();var result=tx.execute(t->application.imprimir(tipo,d.id(),intento));var repetida=tx.execute(t->application.imprimir(tipo,d.id(),intento));
            assertThat(result.outputMode()).isEqualTo(BillingPrintOutputMode.PRINT_AGENT);assertThat(result.screenDocument()).isNull();assertThat(repetida.output()).isEqualTo(result.output());
            assertThat(jdbc.queryForObject("select document_type from billing_print_jobs where id=? and tenant_id=? and empresa_id=?",String.class,result.output().trabajoId(),tenant,empresa)).isEqualTo(tipo.name());
        }
        assertThat(rendered.get()).isEqualTo(3);
        var claimService=servicio(a,mock(AuditoriaService.class));var claimTx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        for(int i=0;i<3;i++){var claimed=claimTx.execute(t->claimService.siguiente(principal(a)).orElseThrow());assertThat(claimed.documentType()).isEqualTo(tipo);}
        var vacio=claimTx.execute(t->claimService.siguiente(principal(a)));assertThat(vacio).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(TipoDocumentoImpresion.class)
    void solicitudesSimultaneasDelMismoIntentoCreanUnSoloTrabajo(TipoDocumentoImpresion tipo) throws Exception {
        asignar(a,cajas.getFirst());jdbc.update("update print_agents set print_on_screen=false where tenant_id=? and empresa_id=? and id=?",tenant,empresa,a);
        var principal=new TenantPrincipal(usuario,tenant,empresa,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("facturas.pdf","devoluciones_venta.pdf"));
        var em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());var repos=new JpaRepositoryFactory(em);
        var router=new BillingPrintRouter(jdbc,new PrintAgentRoutingService(repos.getRepository(CashRegisterRepository.class),repos.getRepository(AgenteImpresionRepository.class)),repos.getRepository(AgenteImpresionRepository.class),mock(AuditoriaService.class));
        var inicio=new CountDownLatch(1);var renders=new AtomicInteger();UUID intento=UUID.randomUUID();
        var documento=new BillingPrintRouter.Documento(tipo,UUID.randomUUID(),sucursal,cajas.getFirst(),()->{renders.incrementAndGet();return new ArchivoPdfDto("ticket.pdf",new byte[]{1});});
        try(var pool=Executors.newFixedThreadPool(2)){
            var trabajos=new ArrayList<Future<SolicitudImpresionResultadoDto>>();
            for(int i=0;i<2;i++)trabajos.add(pool.submit(()->{
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
                try{inicio.await();return new TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject())).execute(t->router.solicitar(documento,intento));}
                finally{SecurityContextHolder.clearContext();}
            }));
            inicio.countDown();var primero=trabajos.getFirst().get(30,TimeUnit.SECONDS);assertThat(trabajos.getLast().get(30,TimeUnit.SECONDS)).isEqualTo(primero);
        }
        assertThat(renders.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from billing_print_jobs where tenant_id=? and empresa_id=? and agent_id=?",Integer.class,tenant,empresa,a)).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(TipoDocumentoImpresion.class)
    void reintentoNoCambiaDestinoAunqueCambieConfiguracion(TipoDocumentoImpresion tipo){
        asignar(a,cajas.getFirst());var p=new TenantPrincipal(usuario,tenant,empresa,null,"TEST","Prueba","test","",true,true,Set.of(),Set.of("facturas.pdf"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
        var em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());var repos=new JpaRepositoryFactory(em);
        var router=new BillingPrintRouter(jdbc,new PrintAgentRoutingService(repos.getRepository(CashRegisterRepository.class),repos.getRepository(AgenteImpresionRepository.class)),repos.getRepository(AgenteImpresionRepository.class),mock(AuditoriaService.class));
        var tx=new TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        var renders=new AtomicInteger();var document=new BillingPrintRouter.Documento(tipo,UUID.randomUUID(),sucursal,cajas.getFirst(),()->{renders.incrementAndGet();return new ArchivoPdfDto("ticket.pdf",new byte[]{1});});UUID screenAttempt=UUID.randomUUID();
        var first=tx.execute(t->router.solicitar(document,screenAttempt));assertThat(first.printOnScreen()).isTrue();
        jdbc.update("update print_agents set print_on_screen=false where tenant_id=? and empresa_id=? and id=?",tenant,empresa,a);
        var retry=tx.execute(t->router.solicitar(document,screenAttempt));assertThat(retry.printOnScreen()).isTrue();assertThat(renders.get()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from billing_print_jobs where tenant_id=? and empresa_id=? and agent_id=?",Integer.class,tenant,empresa,a)).isZero();
        UUID physicalAttempt=UUID.randomUUID();var physical=tx.execute(t->router.solicitar(document,physicalAttempt));assertThat(physical.printOnScreen()).isFalse();
        jdbc.update("update print_agents set print_on_screen=true where tenant_id=? and empresa_id=? and id=?",tenant,empresa,a);
        var samePhysical=tx.execute(t->router.solicitar(document,physicalAttempt));assertThat(samePhysical).isEqualTo(physical);assertThat(renders.get()).isEqualTo(1);
        var other=new BillingPrintRouter.Documento(tipo,UUID.randomUUID(),sucursal,cajas.getFirst(),document.ticket());assertThatThrownBy(()->tx.execute(t->router.solicitar(other,screenAttempt))).isInstanceOf(ReglaNegocioException.class);
    }

    private void asignar(UUID agent,UUID caja){jdbc.update("insert into print_agent_cash_registers(tenant_id,empresa_id,sucursal_id,agent_id,cash_register_id) values(?,?,?,?,?)",tenant,empresa,sucursal,agent,caja);}
    private UUID trabajo(UUID agent,UUID caja){UUID id=UUID.randomUUID();jdbc.update("insert into billing_print_jobs(id,tenant_id,empresa_id,branch_id,cash_register_id,agent_id,document_id,idempotency_key,document_pdf) values(?,?,?,?,?,?,?,?,?)",id,tenant,empresa,sucursal,caja,agent,UUID.randomUUID(),UUID.randomUUID(),new byte[]{1});return id;}
    private PrintAgentPrincipal principal(UUID id){return new PrintAgentPrincipal(id,tenant,empresa,sucursal,"TEST","Prueba");}
    private BillingPrintService servicio(UUID id,AuditoriaService audit){var repo=mock(AgenteImpresionRepository.class);var agent=mock(AgenteImpresion.class);when(agent.getId()).thenReturn(id);when(agent.getSucursalId()).thenReturn(sucursal);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);when(agent.getCajas()).thenReturn(Set.of(mock(Caja.class)));when(repo.bloquear(id,tenant,empresa)).thenReturn(Optional.of(agent));return new BillingPrintService(jdbc,mock(FacturaVentaRepository.class),mock(PrintAgentRoutingService.class),mock(FacturaVentaPdfService.class),repo,audit);}
    private void competir(Operacion operacion) throws Exception {
        var inicio=new CountDownLatch(1);var exitos=new AtomicInteger();var rechazados=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)){
            List<Future<?>> futuros=new ArrayList<>();for(UUID id:List.of(a,b))futuros.add(pool.submit(()->{try(var c=ds.getConnection()){c.setAutoCommit(false);try(var s=c.createStatement()){s.execute("set local lock_timeout='10s'");}inicio.await();try{operacion.ejecutar(c,id);c.commit();exitos.incrementAndGet();}catch(SQLException ex){c.rollback();assertThat(ex.getSQLState()).isEqualTo("23505");rechazados.incrementAndGet();}}catch(Exception ex){throw new RuntimeException(ex);}}));inicio.countDown();for(var f:futuros)f.get(20,TimeUnit.SECONDS);
        }
        assertThat(exitos.get()).isEqualTo(1);assertThat(rechazados.get()).isEqualTo(1);
    }
    @FunctionalInterface interface Operacion {void ejecutar(Connection c,UUID agent) throws Exception;}
}
