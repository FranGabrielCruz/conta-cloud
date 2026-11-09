package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CarteraCobrarDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.CuentasCobrarConsultaRepository;
import com.citacloud.springboot.contacloud.app.repositories.CuentasCobrarConsultaRepository.*;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CuentasCobrarService {
    private static final String VER="@empresaModuloService.habilitado('VENTAS') and hasAuthority('cuentas_cobrar.ver')";
    private static final String REPORTAR=VER+" and hasAuthority('cuentas_cobrar.reportar')";
    private final CuentasCobrarConsultaRepository repo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;

    public CuentasCobrarService(CuentasCobrarConsultaRepository repo,EmpresaRepository empresas,
            DatosEmpresaRepository datosEmpresa) {
        this.repo=repo;
        this.empresas=empresas;
        this.datosEmpresa=datosEmpresa;
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true)
    public LocalDate hoy() { return LocalDate.now(contexto().alcance().zona()); }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Pantalla consultar(Filtro filtro,boolean porCliente,int pagina,int tamano) {
        if(pagina<0||!Set.of(10,25,50,100).contains(tamano))throw new ReglaNegocioException("Paginación inválida.");
        var contexto=contexto();
        var consulta=consulta(contexto,filtro,LocalDate.now(contexto.alcance().zona()));
        var page=PageRequest.of(pagina,tamano);
        return new Pantalla(porCliente?Page.empty():repo.cuentas(consulta,page),
            porCliente?repo.clientes(consulta,page):Page.empty(),repo.indicadores(consulta));
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true)
    public List<Cliente> buscarClientes(String texto,int inicio,int limite) {
        if(inicio<0||limite<1||limite>100)throw new ReglaNegocioException("Paginación inválida.");
        var contexto=contexto();
        return repo.opciones(consulta(contexto,new Filtro(null,Estado.TODOS,null,null,texto),
            LocalDate.now(contexto.alcance().zona())),inicio,limite);
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detalle detalle(UUID id) {
        if(id==null)throw new RecursoNoEncontradoException("Cuenta por cobrar no encontrada.");
        var contexto=contexto();
        var consulta=consulta(contexto,new Filtro(null,Estado.TODOS,null,null,""),LocalDate.now(contexto.alcance().zona()));
        var cuenta=repo.cuenta(consulta,id).orElseThrow(()->new RecursoNoEncontradoException("Cuenta por cobrar no encontrada."));
        var aplicaciones=repo.aplicaciones(consulta,id);
        var cobros=aplicado(aplicaciones,"Recibo");
        var creditos=aplicado(aplicaciones,"Nota de crédito");
        LoggerFactory.getLogger(getClass()).atDebug().addKeyValue("CuentaId",id)
            .addKeyValue("TraceId",TraceContext.currentOrCreate()).log("cuentas_cobrar_detalle");
        return new Detalle(cuenta,cobros,creditos,cuenta.aplicado().subtract(cobros).subtract(creditos),aplicaciones);
    }

    @PreAuthorize(REPORTAR)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReporteCartera cartera(Filtro filtro) {
        var contexto=contexto();
        var consulta=consulta(contexto,filtro,LocalDate.now(contexto.alcance().zona()));
        return new ReporteCartera(encabezado(contexto,consulta),repo.carteraCompleta(consulta),repo.indicadores(consulta));
    }

    @PreAuthorize(REPORTAR)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReporteEdad antiguedad(Filtro filtro,LocalDate corte) {
        var contexto=contexto();
        var hoy=LocalDate.now(contexto.alcance().zona());
        if(corte==null||corte.isAfter(hoy)||corte.getYear()<1900)
            throw new ReglaNegocioException("Indica una fecha de corte válida, igual o anterior a hoy.");
        var consulta=consulta(contexto,filtro,corte);
        boolean historico=corte.isBefore(hoy);
        if(historico&&!repo.corteReconstruible(consulta))
            throw new ReglaNegocioException("El historial de la cartera filtrada no permite reconstruir este corte. Ajusta los filtros o selecciona la fecha de hoy.");
        return new ReporteEdad(encabezado(contexto,consulta),repo.antiguedad(consulta,historico));
    }

    private Contexto contexto() {
        var p=TenantContext.principalActual();
        var empresa=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
        return new Contexto(new Alcance(p.tenantId(),p.empresaId(),p.accesoTodasSucursales(),p.sucursalIds(),
            ZoneId.of(empresa.getZonaHoraria())),empresa);
    }

    private static Consulta consulta(Contexto contexto,Filtro filtro,LocalDate corte) {
        if(filtro==null)filtro=new Filtro(null,Estado.ACTIVA,null,null,"");
        if(filtro.venceDesde()!=null&&filtro.venceHasta()!=null&&filtro.venceDesde().isAfter(filtro.venceHasta()))
            throw new ReglaNegocioException("El vencimiento inicial no puede ser posterior al final.");
        String texto=filtro.buscar()==null?"":filtro.buscar().strip();
        if(texto.length()>150)throw new ReglaNegocioException("La búsqueda admite hasta 150 caracteres.");
        return new Consulta(contexto.alcance(),
            new Filtro(filtro.cliente(),filtro.estado()==null?Estado.ACTIVA:filtro.estado(),filtro.venceDesde(),filtro.venceHasta(),texto),corte);
    }

    private Encabezado encabezado(Contexto contexto,Consulta consulta) {
        var e=contexto.empresa();
        var datos=datosEmpresa.findByEmpresaId(e.getId()).orElse(null);
        var nombre=datos==null||datos.getNombreComercial()==null||datos.getNombreComercial().isBlank()
            ?e.getNombre():datos.getNombreComercial();
        var f=consulta.filtro();
        String cliente="Todos";
        if(f.cliente()!=null){
            var porCliente=new Consulta(consulta.alcance(),new Filtro(f.cliente(),Estado.TODOS,null,null,""),consulta.corte());
            cliente=repo.opciones(porCliente,0,1).stream().map(Cliente::nombre).findFirst().orElse("Sin coincidencias");
        }
        return new Encabezado(nombre,e.getIdentificacionFiscal(),datos==null?null:datos.getDireccion(),
            datos==null?null:datos.getTelefono(),e.getPaisCodigo(),e.getLogoObjectKey(),consulta.corte(),f,cliente);
    }

    private static BigDecimal aplicado(List<Aplicacion> aplicaciones,String tipo) {
        return aplicaciones.stream().filter(a->!a.revertida()&&tipo.equals(a.tipo()))
            .map(Aplicacion::importe).reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    private record Contexto(Alcance alcance,com.citacloud.springboot.contacloud.app.models.Empresa empresa) {}
    public record Encabezado(String empresa,String rnc,String direccion,String telefono,String pais,String logo,
            LocalDate corte,Filtro filtro,String cliente) {}
    public record ReporteCartera(Encabezado encabezado,List<Cuenta> cuentas,Indicadores indicadores) {}
    public record ReporteEdad(Encabezado encabezado,List<Edad> edades) {}
}
