package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto;
import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.LibroMayorRepository;
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
@Transactional(readOnly=true)
public class LibroMayorService {
    private static final Logger LOG=LoggerFactory.getLogger(LibroMayorService.class);
    private static final String VER="@empresaModuloService.habilitado('CONTABILIDAD') and hasAuthority('libro_mayor.ver')";
    private final LibroMayorRepository repo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;

    public LibroMayorService(LibroMayorRepository repo,EmpresaRepository empresas,DatosEmpresaRepository datosEmpresa) {
        this.repo=repo;
        this.empresas=empresas;
        this.datosEmpresa=datosEmpresa;
    }

    @PreAuthorize(VER)
    public YearMonth mesInicial() {
        return YearMonth.now(ZoneId.of(empresa(TenantContext.principalActual()).getZonaHoraria()));
    }

    @PreAuthorize(VER)
    public List<Cuenta> cuentas() {
        return repo.cuentas(TenantContext.principalActual());
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Resultado consultar(Filtro filtro,int pagina,int tamano) {
        if(pagina<0||!Set.of(10,25,50,100).contains(tamano))
            throw new ReglaNegocioException("Revisa la paginación.");
        return leer(TenantContext.principalActual(),validar(filtro),pagina,tamano);
    }

    @PreAuthorize(VER+" and hasAuthority('libro_mayor.pdf')")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Reporte reporte(Filtro filtro) {
        var p=TenantContext.principalActual();
        var f=validar(filtro);
        var resultado=leer(p,f,null,0);
        var empresa=empresa(p);
        var datos=datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        var comercial=datos==null?null:datos.getNombreComercial();
        var emisor=new Empresa(comercial==null||comercial.isBlank()?empresa.getNombre():comercial,
            empresa.getIdentificacionFiscal(),datos==null?null:datos.getDireccion(),datos==null?null:datos.getTelefono(),
            empresa.getPaisCodigo(),empresa.getLogoObjectKey());
        return new Reporte(f,resultado,emisor,ZonedDateTime.now(ZoneId.of(empresa.getZonaHoraria())));
    }

    private Resultado leer(TenantPrincipal p,Filtro f,Integer pagina,int tamano) {
        long inicio=System.nanoTime();
        var cuenta=repo.cuenta(p,f.accountId()).orElseThrow(()->new RecursoNoEncontradoException("Cuenta no encontrada."));
        var resumen=repo.resumen(p,f);
        if(resumen.isEmpty()) {
            var cero=Saldo.de(BigDecimal.ZERO);
            resumen=List.of(new Resumen("",cero,BigDecimal.ZERO,BigDecimal.ZERO,cero,cero,0));
        }
        long total=resumen.stream().mapToLong(Resumen::movimientos).sum();
        var movimientos=total==0?List.<Movimiento>of():repo.movimientos(p,f,pagina,tamano);
        LOG.atInfo().addKeyValue("TraceId",TraceContext.currentOrCreate()).addKeyValue("UserId",p.usuarioId())
            .addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("AccountId",f.accountId()).addKeyValue("FromDate",f.fromDate()).addKeyValue("ToDate",f.toDate())
            .addKeyValue("Type",f.type()).addKeyValue("Origin",f.origin()).addKeyValue("Movimientos",total)
            .addKeyValue("DuracionMs",(System.nanoTime()-inicio)/1_000_000).log("libro_mayor_consultado");
        return new Resultado(cuenta,f.fromDate(),f.toDate(),movimientos,resumen,total,pagina==null?0:pagina,tamano);
    }

    static Filtro validar(Filtro f) {
        if(f==null||f.accountId()==null) throw new ReglaNegocioException("Selecciona una cuenta contable.");
        var periodo=LibroDiarioService.validar(new LibroDiarioDto.Filtro(f.fromDate(),f.toDate(),f.type(),f.origin(),f.search()));
        return new Filtro(f.accountId(),periodo.desde(),periodo.hasta(),periodo.tipo(),periodo.origen(),periodo.buscar());
    }

    private com.citacloud.springboot.contacloud.app.models.Empresa empresa(TenantPrincipal p) {
        return empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
    }
}
