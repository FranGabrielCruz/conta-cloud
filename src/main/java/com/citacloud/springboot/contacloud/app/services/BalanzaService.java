package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.*;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.BalanzaRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class BalanzaService {
    private static final Logger LOG=LoggerFactory.getLogger(BalanzaService.class);
    private static final String VER="@empresaModuloService.habilitado('CONTABILIDAD') and hasAuthority('balanza.ver')";
    private static final List<Importes> CERO=List.of(Importes.cuenta("",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
    private final BalanzaRepository repo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;

    public BalanzaService(BalanzaRepository repo,EmpresaRepository empresas,DatosEmpresaRepository datosEmpresa) {
        this.repo=repo;
        this.empresas=empresas;
        this.datosEmpresa=datosEmpresa;
    }

    @PreAuthorize(VER)
    public Inicial inicial() {
        var p=TenantContext.principalActual();
        return new Inicial(YearMonth.now(ZoneId.of(empresa(p).getZonaHoraria())),repo.niveles(p));
    }

    @PreAuthorize(VER)
    public Resultado consultar(Filtro filtro,int pagina,int tamano) {
        if(pagina<0||!Set.of(10,25,50,100).contains(tamano)) throw new ReglaNegocioException("Revisa la paginación.");
        return leer(TenantContext.principalActual(),validar(filtro),pagina,tamano);
    }

    @PreAuthorize(VER+" and hasAuthority('balanza.pdf')")
    public Reporte reporte(Filtro filtro) {
        var p=TenantContext.principalActual();
        var resultado=leer(p,validar(filtro),null,0);
        var e=empresa(p);
        var datos=datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        var comercial=datos==null?null:datos.getNombreComercial();
        var emisor=new Empresa(comercial==null||comercial.isBlank()?e.getNombre():comercial,e.getIdentificacionFiscal(),
            datos==null?null:datos.getDireccion(),datos==null?null:datos.getTelefono(),e.getPaisCodigo(),e.getLogoObjectKey());
        return new Reporte(resultado,emisor,ZonedDateTime.now(ZoneId.of(e.getZonaHoraria())));
    }

    static Filtro validar(Filtro f) {
        if(f==null||f.fromDate()==null||f.toDate()==null||f.toDate().isBefore(f.fromDate()))
            throw new ReglaNegocioException("Indica un rango de fechas válido.");
        if(f.level()!=null&&f.level()<1) throw new ReglaNegocioException("Selecciona un nivel válido del catálogo.");
        String buscar=f.search()==null?"":f.search().strip();
        if(buscar.length()>150) throw new ReglaNegocioException("La búsqueda admite hasta 150 caracteres.");
        return new Filtro(f.fromDate(),f.toDate(),f.level(),f.display()==null?Mostrar.MOVIMIENTOS_O_SALDO:f.display(),buscar);
    }

    private Resultado leer(TenantPrincipal p,Filtro f,Integer pagina,int tamano) {
        long inicio=System.nanoTime();
        if(f.level()!=null&&f.level()>repo.niveles(p))
            throw new ReglaNegocioException("Selecciona un nivel válido del catálogo.");
        var r=repo.resumen(p,f);
        var items=r.total()==0?List.<Cuenta>of():repo.cuentas(p,f,pagina,tamano);
        var t=r.totales();
        var totales=new Totales(t.filtrados().isEmpty()?CERO:t.filtrados(),t.generales().isEmpty()?CERO:t.generales());
        String trace=TraceContext.currentOrCreate();
        LOG.atInfo().addKeyValue("TraceId",trace).addKeyValue("UserId",p.usuarioId())
            .addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("FromDate",f.fromDate()).addKeyValue("ToDate",f.toDate()).addKeyValue("Level",f.level())
            .addKeyValue("Display",f.display()).addKeyValue("Cuentas",r.total())
            .addKeyValue("DuracionMs",(System.nanoTime()-inicio)/1_000_000).log("balanza_consultada");
        for(var general:totales.generales()) {
            registrarDiferencia(p,f,trace,general.moneda(),"saldo_inicial",general.diferenciaInicial());
            registrarDiferencia(p,f,trace,general.moneda(),"movimientos",general.diferenciaMovimientos());
            registrarDiferencia(p,f,trace,general.moneda(),"saldo_final",general.diferenciaFinal());
        }
        return new Resultado(f,items,totales,r.total(),pagina==null?0:pagina,tamano);
    }

    private static void registrarDiferencia(TenantPrincipal p,Filtro f,String trace,String moneda,String tipo,BigDecimal valor) {
        if(valor.signum()!=0) LOG.atWarn().addKeyValue("TraceId",trace)
            .addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("FromDate",f.fromDate()).addKeyValue("ToDate",f.toDate())
            .addKeyValue("Moneda",moneda).addKeyValue("Comprobacion",tipo).addKeyValue("Diferencia",valor)
            .log("balanza_diferencia_contable");
    }

    private com.citacloud.springboot.contacloud.app.models.Empresa empresa(TenantPrincipal p) {
        return empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
    }
}
