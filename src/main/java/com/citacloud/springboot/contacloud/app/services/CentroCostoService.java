package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.CentroCostoRepository;
import com.citacloud.springboot.contacloud.app.repositories.CuentaContableRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly=true)
public class CentroCostoService {
    private static final String VER="@empresaModuloService.habilitado('CONTABILIDAD') and hasAuthority('centros_costos.ver')";
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger(CentroCostoService.class);
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final CentroCostoRepository repo;
    private final CuentaContableRepository catalogo;
    private final AuditoriaService auditoria;

    public CentroCostoService(CentroCostoRepository repo,CuentaContableRepository catalogo,AuditoriaService auditoria) {
        this.repo=repo;
        this.catalogo=catalogo;
        this.auditoria=auditoria;
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Resultado listar(Filtro f,int pagina,int tamano) {
        if(pagina<0||!Set.of(10,25,50,100).contains(tamano)) throw new ReglaNegocioException("Revisa la paginación.");
        return repo.listar(TenantContext.principalActual(),new Filtro(f.activo(),buscar(f.buscar())),(long)pagina*tamano,tamano);
    }

    @PreAuthorize(VER)
    public List<Referencia> padres(String texto,int offset,int limite) {
        return repo.opciones(TenantContext.principalActual(),new Filtro(null,buscar(texto)),Math.max(0,offset),Math.clamp(limite,1,100));
    }

    @PreAuthorize(VER)
    public Centro detalle(UUID id) { return detalle(TenantContext.principalActual(),id); }

    @PreAuthorize(VER+" and hasAuthority('centros_costos.crear')")
    @Transactional
    public UUID crear(Entrada entrada,UUID clave) {
        if(clave==null) throw new ReglaNegocioException("Falta la identificación de la solicitud.");
        var p=TenantContext.principalActual();
        catalogo.bloquearEmpresa(p);
        var existente=repo.solicitud(p,clave);
        if(existente.isPresent()) return existente.get();
        var e=validar(entrada);
        validarPadre(p,null,e.padre());
        var id=UUID.randomUUID();
        long numero=repo.siguiente(p);
        repo.crear(p,id,clave,e,numero,String.format(Locale.ROOT,"CC-%03d",numero));
        auditar(p,"CREAR",null,detalle(p,id));
        return id;
    }

    @PreAuthorize(VER+" and hasAuthority('centros_costos.editar')")
    @Transactional
    public void editar(UUID id,long version,Entrada entrada) {
        var p=TenantContext.principalActual();
        catalogo.bloquearEmpresa(p);
        var anterior=detalle(p,id);
        verificarVersion(anterior,version);
        var e=validar(entrada);
        validarPadre(p,id,e.padre());
        repo.editar(p,id,e);
        auditar(p,"EDITAR",anterior,detalle(p,id));
    }

    @PreAuthorize(VER+" and hasAuthority('centros_costos.activar')")
    @Transactional
    public void activar(UUID id,long version) { estado(id,version,true); }

    @PreAuthorize(VER+" and hasAuthority('centros_costos.desactivar')")
    @Transactional
    public void desactivar(UUID id,long version) { estado(id,version,false); }

    private void estado(UUID id,long version,boolean activo) {
        var p=TenantContext.principalActual();
        catalogo.bloquearEmpresa(p);
        var anterior=detalle(p,id);
        verificarVersion(anterior,version);
        if(anterior.referencia().activo()==activo) return;
        repo.estado(p,id,activo);
        auditar(p,activo?"ACTIVAR":"DESACTIVAR",anterior,detalle(p,id));
    }

    private Centro detalle(TenantPrincipal p,UUID id) {
        return repo.detalle(p,id).orElseThrow(()->new RecursoNoEncontradoException("Centro de costos no encontrado."));
    }

    private void validarPadre(TenantPrincipal p,UUID id,UUID padre) {
        if(padre==null) return;
        detalle(p,padre);
        if(id!=null&&repo.ciclo(p,id,padre)) throw new ReglaNegocioException("El centro padre no puede ser el mismo centro ni un descendiente.");
    }

    private static void verificarVersion(Centro c,long version) {
        if(c.version()!=version) throw new ReglaNegocioException("El centro de costos cambió. Vuelve a abrirlo e inténtalo de nuevo.");
    }

    static Entrada validar(Entrada e) {
        if(e==null||e.nombre()==null||e.nombre().isBlank()) throw new ReglaNegocioException("Indica el nombre del centro de costos.");
        var nombre=e.nombre().strip();
        var descripcion=e.descripcion()==null?null:e.descripcion().strip();
        if(nombre.length()>160||descripcion!=null&&descripcion.length()>2000)
            throw new ReglaNegocioException("El nombre o la descripción supera la longitud permitida.");
        return new Entrada(nombre,e.padre(),descripcion==null||descripcion.isEmpty()?null:descripcion);
    }

    private static String buscar(String texto) {
        String limpio=texto==null?"":texto.strip();
        if(limpio.length()>150) throw new ReglaNegocioException("La búsqueda admite hasta 150 caracteres.");
        return limpio;
    }

    private void auditar(TenantPrincipal p,String operacion,Centro anterior,Centro actual) {
        var cambios=new LinkedHashMap<String,Object>();
        var trace=TraceContext.currentOrCreate();
        cambios.put("traceId",trace);
        cambios.put("anterior",anterior);
        cambios.put("actual",actual);
        auditoria.registrar(operacion,"CentroCosto",actual.referencia().id(),JSON.writeValueAsString(cambios));
        LOG.atInfo().addKeyValue("TraceId",trace).addKeyValue("UserId",p.usuarioId())
            .addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("CostCenterId",actual.referencia().id()).addKeyValue("Operation",operacion)
            .addKeyValue("OldStatus",anterior==null?null:anterior.referencia().activo())
            .addKeyValue("NewStatus",actual.referencia().activo()).log("centro_costo_actualizado");
    }
}
