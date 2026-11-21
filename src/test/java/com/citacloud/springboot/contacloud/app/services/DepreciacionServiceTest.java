package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.*;
import com.citacloud.springboot.contacloud.app.repositories.DepreciacionRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DepreciacionServiceTest {
    private static final YearMonth INICIO=YearMonth.of(2026,11);

    @Test
    void calculaLineaRectaYAjustaElUltimoMesSinSuperarElResidual() {
        var activo=candidato("150000","30000",60,"0","0");
        var primera=DepreciacionService.lineaRecta(activo,INICIO);
        assertThat(primera.base()).isEqualByComparingTo("120000");
        assertThat(primera.importe()).isEqualByComparingTo("2000.00");
        assertThat(primera.libros()).isEqualByComparingTo("148000");
        assertThat(DepreciacionService.lineaRecta(activo,INICIO.minusMonths(1))).isNull();
        var ultima=DepreciacionService.lineaRecta(candidato("100","1",7,"84.84","0"),INICIO.plusMonths(6));
        assertThat(ultima.importe()).isEqualByComparingTo("14.16");
        assertThat(ultima.libros()).isEqualByComparingTo("1.00");
        assertThat(DepreciacionService.lineaRecta(candidato("100","1",7,"98","1"),INICIO.plusMonths(7))).isNull();
    }

    @Test
    void generaPdfDeVariasPaginasConElDetalleHistoricoYTotalesPorMoneda() throws Exception {
        var servicio=mock(DepreciacionService.class);
        var pdf=new DepreciacionPdfService(servicio,null);
        var id=UUID.randomUUID();
        var empresa=new Empresa("Empresa de prueba","123456789","Calle Uno","8095551234","DO",null);
        var proceso=new Proceso(id,1,INICIO,INICIO.atEndOfMonth(),OffsetDateTime.parse("2026-11-30T18:42:00-04:00"),
            UUID.randomUUID(),"Juan Pérez",UUID.randomUUID(),empresa);
        var lineas=java.util.stream.IntStream.rangeClosed(1,37).mapToObj(i->new Linea(UUID.randomUUID(),UUID.randomUUID(),
            "AF-"+i,"Activo histórico "+i,i<37?"DOP":"USD",new BigDecimal("150000"),new BigDecimal("120000"),
            new BigDecimal("4000"),new BigDecimal("2000"),new BigDecimal("144000"))).toList();
        when(servicio.documento(id)).thenReturn(new Detalle(proceso,lineas,DepreciacionService.totales(lineas)));
        var archivo=pdf.generar(id);
        if (Boolean.getBoolean("depreciacion.pdf.preview"))
            java.nio.file.Files.write(java.nio.file.Path.of("target","depreciacion-prueba.pdf"),archivo.contenido());
        try (var doc=Loader.loadPDF(archivo.contenido())) {
            var texto=new PDFTextStripper().getText(doc);
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            assertThat(texto).contains("DEPRECIACIÓN DE ACTIVOS FIJOS","DEP-000001","30/11/2026","Juan Pérez",
                "Activo histórico 37","72,000.00","2,000.00","123-45678-9","809-555-1234","TOTAL DOP","TOTAL USD","Página 2")
                .doesNotContain("74,000.00","ContaCloud");
            assertThat(texto.lines().filter(s->s.contains("DEP. PERÍODO"))).hasSize(doc.getNumberOfPages());
        }
        verify(servicio).documento(id);
    }

    @Configuration @EnableMethodSecurity
    static class Seguridad {}

    @Test
    void exigePermisosSeparadosParaConsultaProcesoYPdf() {
        try (var ctx=new AnnotationConfigApplicationContext()) {
            var repo=mock(DepreciacionRepository.class);
            var modulos=mock(EmpresaModuloService.class);
            when(modulos.habilitado("ACTIVOS_FIJOS")).thenReturn(true);
            ctx.register(Seguridad.class);
            ctx.registerBean("empresaModuloService",EmpresaModuloService.class,()->modulos);
            ctx.registerBean(DepreciacionService.class,()->new DepreciacionService(repo,null,null,null,null));
            ctx.refresh();
            var svc=ctx.getBean(DepreciacionService.class);
            autenticar(Set.of("depreciaciones.procesar"),true);
            assertThatThrownBy(()->svc.historial(null,null,0,10)).isInstanceOf(AccessDeniedException.class);
            autenticar(Set.of("depreciaciones.ver"),true);
            assertThatThrownBy(()->svc.procesar(INICIO,"revision",UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->svc.documento(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            autenticar(Set.of("depreciaciones.ver","depreciaciones.procesar"),false);
            assertThatThrownBy(()->svc.calcular(INICIO)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(repo);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Candidato candidato(String costo,String residual,int vida,String anterior,String ajustes) {
        return new Candidato(UUID.randomUUID(),UUID.randomUUID(),"AF-000001","Equipo","DOP",new BigDecimal(costo),
            new BigDecimal(residual),vida,"LINEA_RECTA",INICIO.atDay(1),new BigDecimal(anterior),new BigDecimal(ajustes),false,
            new BigDecimal(anterior));
    }

    static void autenticar(Set<String> permisos,boolean todas) {
        var p=new TenantPrincipal(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"TEST","Prueba",
            "test","",true,todas,Set.of(),permisos);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
}
