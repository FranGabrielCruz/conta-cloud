package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.*;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.LibroDiarioRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class LibroDiarioService {
    private static final String VER="@empresaModuloService.habilitado('CONTABILIDAD') and hasAuthority('libro_diario.ver')";
    private final LibroDiarioRepository repo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;

    public LibroDiarioService(LibroDiarioRepository repo,EmpresaRepository empresas,DatosEmpresaRepository datosEmpresa) {
        this.repo=repo;
        this.empresas=empresas;
        this.datosEmpresa=datosEmpresa;
    }

    @PreAuthorize(VER)
    public Filtro periodoInicial() {
        var e=empresa(TenantContext.principalActual());
        var mes=YearMonth.now(ZoneId.of(e.getZonaHoraria()));
        return new Filtro(mes.atDay(1),mes.atEndOfMonth(),null,null,"");
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Resultado consultar(Filtro filtro,int pagina,int tamano) {
        if(pagina<0||!Set.of(10,25,50,100).contains(tamano)) throw new ReglaNegocioException("Revisa la paginación.");
        var f=validar(filtro);
        var p=TenantContext.principalActual();
        var totales=repo.totales(p,f);
        var asientos=repo.asientos(p,f,pagina,tamano);
        return new Resultado(asientos,totales,totales.stream().mapToLong(TotalMoneda::asientos).sum(),pagina,tamano);
    }

    @PreAuthorize(VER+" and hasAuthority('libro_diario.pdf')")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Reporte reporte(Filtro filtro) {
        var f=validar(filtro);
        var p=TenantContext.principalActual();
        var empresa=empresa(p);
        var datos=datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        var comercial=datos==null?null:datos.getNombreComercial();
        var emisor=new Empresa(comercial==null||comercial.isBlank()?empresa.getNombre():comercial,empresa.getIdentificacionFiscal(),
            datos==null?null:datos.getDireccion(),datos==null?null:datos.getTelefono(),empresa.getPaisCodigo(),empresa.getLogoObjectKey());
        return new Reporte(f,repo.asientos(p,f,null,null),repo.totales(p,f),emisor,ZonedDateTime.now(ZoneId.of(empresa.getZonaHoraria())));
    }

    static Filtro validar(Filtro f) {
        if(f==null||f.desde()==null||f.hasta()==null||f.desde().isAfter(f.hasta()))
            throw new ReglaNegocioException("Indica un rango de fechas válido.");
        var buscar=f.buscar()==null?"":f.buscar().strip();
        if(buscar.length()>150) throw new ReglaNegocioException("La búsqueda admite hasta 150 caracteres.");
        return new Filtro(f.desde(),f.hasta(),f.tipo(),f.origen(),buscar);
    }

    private com.citacloud.springboot.contacloud.app.models.Empresa empresa(TenantPrincipal p) {
        return empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
    }
}
