package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.dto.MantenimientoDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.ActivoFijoRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.MantenimientoRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly=true)
public class MantenimientoService {
    public static final String RECURSO="mantenimiento_activos.";
    private static final String VER="@empresaModuloService.habilitado('ACTIVOS_FIJOS') and hasAuthority('mantenimiento_activos.ver')";
    private static final String CREAR=VER+" and hasAuthority('mantenimiento_activos.crear')";
    private static final String EDITAR=VER+" and hasAuthority('mantenimiento_activos.editar')";
    private static final String COMPLETAR=VER+" and hasAuthority('mantenimiento_activos.completar')";
    private static final String MANTENIMIENTO="MANTENIMIENTO";
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final MantenimientoRepository repo;
    private final ActivoFijoRepository activos;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;
    private final AuditoriaService auditoria;
    private final int diasProximos;

    public MantenimientoService(MantenimientoRepository repo,ActivoFijoRepository activos,EmpresaRepository empresas,
            DatosEmpresaRepository datosEmpresa,AuditoriaService auditoria,
            @Value("${contacloud.activos.mantenimiento.dias-proximos:30}") int diasProximos) {
        this.repo=repo;
        this.activos=activos;
        this.empresas=empresas;
        this.datosEmpresa=datosEmpresa;
        this.auditoria=auditoria;
        this.diasProximos=Math.max(0,diasProximos);
    }

    @PreAuthorize(VER)
    public LocalDate hoy() { return LocalDate.now(zona(empresa(principal()))); }

    @PreAuthorize(VER)
    public List<Tipo> tipos() { return repo.tipos(); }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Fila> listar(Filtro f,int pagina,int tamano) {
        if (pagina<0 || !Set.of(10,25,50,100).contains(tamano) || (f.desde()!=null && f.hasta()!=null && f.desde().isAfter(f.hasta())))
            throw new ReglaNegocioException("Revisa las fechas y la paginación.");
        var filtro=new Filtro(f.desde(),f.hasta(),texto(f.tipo(),24),f.estado(),f.vencido(),Objects.toString(texto(f.buscar(),150),""));
        return repo.listar(principal(),filtro,hoy(),PageRequest.of(pagina,tamano));
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Indicadores indicadores() { return repo.indicadores(principal(),hoy(),diasProximos); }

    @PreAuthorize(VER)
    public Detalle detalle(UUID id) { return detalle(principal(),id,false); }

    @PreAuthorize(VER)
    public List<Fila> historialActivo(UUID id) { return repo.historialActivo(principal(),id,hoy()); }

    @PreAuthorize("("+CREAR+") or ("+EDITAR+") or ("+COMPLETAR+")")
    public Catalogos catalogos() { return catalogos(principal()); }

    @PreAuthorize(CREAR)
    public List<Activo> buscarActivos(String texto,int offset,int limite) {
        if(offset<0 || limite<1 || limite>100) throw new ReglaNegocioException("Paginación inválida.");
        return repo.buscarActivos(principal(),Objects.toString(texto(texto,150),""),offset,limite);
    }

    @PreAuthorize(CREAR)
    @Transactional
    public UUID crear(Entrada solicitud) {
        var p=principal();
        var entrada=validar(solicitud);
        var activo=activo(p,entrada.activo());
        validarCatalogos(p,entrada,null);
        var id=UUID.randomUUID();
        var codigo=repo.siguienteCodigo(p);
        repo.crear(p,id,activo.sucursal(),codigo,entrada);
        auditar(p,detalle(p,id,false),null,Estado.PROGRAMADO,"CREAR",null);
        return id;
    }

    @PreAuthorize(EDITAR)
    @Transactional
    public void editar(UUID id,long version,Entrada solicitud) {
        var p=principal();
        var d=detalle(p,id,true);
        exigir(d,version,Estado.PROGRAMADO);
        var e=validar(solicitud);
        if (!d.datos().activo().equals(e.activo())) throw new ReglaNegocioException("El activo de la orden no puede cambiarse.");
        activo(p,e.activo());
        validarCatalogos(p,e,d.datos());
        repo.editar(p,id,e);
        auditar(p,d,d.estado(),d.estado(),"EDITAR",null);
    }

    @PreAuthorize(VER+" and hasAuthority('mantenimiento_activos.iniciar')")
    @Transactional
    public void iniciar(UUID id,long version,boolean fuera,UUID solicitud) {
        var p=principal();
        var d=detalle(p,id,true);
        if (repetida(p,d,solicitud,Estado.EN_PROCESO)) return;
        exigir(d,version,Estado.PROGRAMADO);
        var activo=activo(p,d.datos().activo());
        if (fuera && MANTENIMIENTO.equals(activo.condicion()))
            throw new ReglaNegocioException("El activo ya está fuera de servicio por mantenimiento.");
        repo.iniciar(p,d,activo.condicion(),fuera,solicitud);
        if (fuera) repo.condicion(p,activo.id(),MANTENIMIENTO,activo.condicion());
        auditar(p,d,d.estado(),Estado.EN_PROCESO,"INICIAR",null);
    }

    @PreAuthorize(COMPLETAR)
    @Transactional
    public void completar(UUID id,long version,Completar solicitud,UUID operacion) {
        var p=principal();
        var d=detalle(p,id,true);
        if (repetida(p,d,operacion,Estado.COMPLETADO)) return;
        exigir(d,version,Estado.EN_PROCESO);
        var a=activo(p,d.datos().activo());
        var empresa=empresa(p);
        if (solicitud==null || solicitud.realizada()==null
            || solicitud.realizada().isBefore(d.inicio().atZoneSameInstant(zona(empresa)).toLocalDate())
            || solicitud.realizada().isAfter(LocalDate.now(zona(empresa))))
            throw new ReglaNegocioException("La fecha realizada debe estar entre el inicio y hoy.");
        var c=new Completar(solicitud.realizada(),importe(solicitud.costo()),solicitud.proveedor(),
            requerido(solicitud.trabajo(),2000,"Indica el trabajo realizado."),solicitud.proxima(),texto(solicitud.observacion(),2000));
        validarProxima(c.realizada(),c.proxima());
        var proveedor=Objects.equals(c.proveedor(),d.datos().proveedor())?d.proveedor()
            :nombreProveedor(catalogos(p),c.proveedor());
        var snapshot=new Snapshot(a.codigo(),a.nombre(),a.marca(),a.modelo(),a.serie(),d.responsable(),proveedor,d.moneda(),
            d.tipo(),emisor(p,empresa),a.estadoPatrimonial());
        repo.completar(p,d,c,snapshot,operacion);
        restaurar(p,d,a);
        auditar(p,d,d.estado(),Estado.COMPLETADO,"COMPLETAR",null);
    }

    @PreAuthorize(VER+" and hasAuthority('mantenimiento_activos.cancelar')")
    @Transactional
    public void cancelar(UUID id,long version,String motivo,UUID solicitud) {
        var p=principal();
        var d=detalle(p,id,true);
        if (repetida(p,d,solicitud,Estado.CANCELADO)) return;
        exigir(d,version,Estado.PROGRAMADO,Estado.EN_PROCESO);
        String razon=requerido(motivo,1000,"Indica el motivo de cancelación.");
        if (d.fueraServicio()) {
            var activo=repo.activo(p,d.datos().activo(),true)
                .orElseThrow(()->new ReglaNegocioException("El activo ya no está en tu alcance."));
            restaurar(p,d,activo);
        }
        repo.cancelar(p,d,razon,solicitud);
        auditar(p,d,d.estado(),Estado.CANCELADO,"CANCELAR",razon);
    }

    @PreAuthorize(VER+" and hasAuthority('mantenimiento_activos.imprimir')")
    public Detalle documento(UUID id) {
        var d=detalle(principal(),id,false);
        if (d.estado()!=Estado.COMPLETADO) throw new ReglaNegocioException("Solo se imprimen mantenimientos completados.");
        return d;
    }

    private static void exigir(Detalle d,long version,Estado... estados) {
        if(d.version()!=version) throw new ReglaNegocioException("El mantenimiento cambió. Vuelve a abrirlo.");
        if (!List.of(estados).contains(d.estado()))
            throw new ReglaNegocioException("El estado del mantenimiento no permite esta acción.");
    }

    private boolean repetida(TenantPrincipal p,Detalle d,UUID solicitud,Estado destino) {
        if (solicitud==null) throw new ReglaNegocioException("Solicitud de operación inválida.");
        return d.estado()==destino && repo.repetida(p,d.id(),solicitud,destino);
    }

    private void restaurar(TenantPrincipal p,Detalle d,Activo a) {
        if (d.fueraServicio() && !a.baja()) repo.condicion(p,a.id(),d.condicionAnterior(),MANTENIMIENTO);
    }

    static Entrada validar(Entrada e) {
        if (e==null || e.activo()==null || e.tipo()==null || e.prioridad()==null || e.programada()==null || e.moneda()==null)
            throw new ReglaNegocioException("Completa el activo, tipo, prioridad, fecha y moneda.");
        validarProxima(e.programada(),e.proxima());
        return new Entrada(e.activo(),requerido(e.tipo(),24,"Selecciona un tipo."),e.prioridad(),e.programada(),e.proxima(),
            e.responsable(),e.proveedor(),e.moneda(),importe(e.estimado()),texto(e.referencia(),150),
            requerido(e.descripcion(),2000,"Indica la descripción del mantenimiento."),texto(e.observacion(),2000));
    }

    private void validarCatalogos(TenantPrincipal p,Entrada e,Entrada anterior) {
        var catalogos=catalogos(p);
        if(catalogos.tipos().stream().noneMatch(t->t.codigo().equals(e.tipo())))
            throw new ReglaNegocioException("Selecciona un tipo válido.");
        if ((anterior==null || !e.moneda().equals(anterior.moneda()))
            && catalogos.monedas().stream().noneMatch(m->m.id().equals(e.moneda())))
            throw new ReglaNegocioException("Selecciona una moneda activa.");
        if (e.responsable()!=null && (anterior==null || !e.responsable().equals(anterior.responsable()))
            && catalogos.responsables().stream().noneMatch(o->o.id().equals(e.responsable())))
            throw new ReglaNegocioException("Selecciona un responsable válido.");
        if (anterior==null || !Objects.equals(e.proveedor(),anterior.proveedor())) nombreProveedor(catalogos,e.proveedor());
    }

    private static String nombreProveedor(Catalogos catalogos,UUID id) {
        if(id==null) return null;
        return catalogos.proveedores().stream().filter(o->o.id().equals(id)).map(o->o.nombre()).findFirst()
            .orElseThrow(()->new ReglaNegocioException("Selecciona un proveedor válido."));
    }

    private Catalogos catalogos(TenantPrincipal p) {
        var c=activos.catalogos(p,true);
        return new Catalogos(repo.tipos(),c.monedas(),c.responsables(),c.proveedores());
    }

    private Activo activo(TenantPrincipal p,UUID id) {
        return repo.activo(p,id,true).filter(a->!a.baja()).orElseThrow(
            ()->new ReglaNegocioException("El activo seleccionado no está disponible para mantenimiento."));
    }

    private Detalle detalle(TenantPrincipal p,UUID id,boolean bloquear) {
        return repo.detalle(p,id,bloquear).orElseThrow(()->new RecursoNoEncontradoException("Mantenimiento no encontrado."));
    }

    private static void validarProxima(LocalDate fecha,LocalDate proxima) {
        if (proxima!=null && !proxima.isAfter(fecha)) throw new ReglaNegocioException("El próximo mantenimiento debe ser posterior a la fecha indicada.");
    }

    private static BigDecimal importe(BigDecimal valor) {
        if (valor==null || valor.signum()<0) throw new ReglaNegocioException("Indica un costo mayor o igual a cero.");
        try {
            var normalizado=valor.setScale(2,RoundingMode.UNNECESSARY);
            if (normalizado.precision()<=19) return normalizado;
        } catch (ArithmeticException ex) {
            throw new ReglaNegocioException("El costo admite hasta dos decimales.");
        }
        throw new ReglaNegocioException("El costo excede el máximo permitido.");
    }

    private static String requerido(String valor,int max,String mensaje) {
        var limpio=texto(valor,max);
        if(limpio==null) throw new ReglaNegocioException(mensaje);
        return limpio;
    }

    private static String texto(String valor,int max) {
        if (valor==null || valor.isBlank()) return null;
        var limpio=valor.strip();
        if (limpio.length()>max) throw new ReglaNegocioException("Un campo supera el máximo de "+max+" caracteres.");
        return limpio;
    }

    private void auditar(TenantPrincipal p,Detalle d,Estado anterior,Estado nuevo,String accion,String motivo) {
        var datos=new LinkedHashMap<String,Object>();
        datos.put("codigo",d.codigo());
        datos.put("tenantId",p.tenantId());
        datos.put("sucursalId",d.sucursal());
        datos.put("activoId",d.datos().activo());
        datos.put("anterior",anterior);
        datos.put("nuevo",nuevo);
        datos.put("motivo",motivo);
        datos.put("traceId",TraceContext.currentOrCreate());
        auditoria.registrar(accion,"MantenimientoActivo",d.id(),JSON.writeValueAsString(datos));
        LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("MaintenanceId",d.id()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("TraceId",datos.get("traceId")).addKeyValue("Operacion",accion).log("activo_mantenimiento");
    }

    private com.citacloud.springboot.contacloud.app.models.Empresa empresa(TenantPrincipal p) {
        return empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private Empresa emisor(TenantPrincipal p,com.citacloud.springboot.contacloud.app.models.Empresa empresa) {
        var v=datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        return new Empresa(v==null||v.getNombreComercial()==null||v.getNombreComercial().isBlank()?empresa.getNombre():v.getNombreComercial(),
            empresa.getIdentificacionFiscal(),v==null?null:v.getDireccion(),v==null?null:v.getTelefono(),
            empresa.getPaisCodigo(),empresa.getLogoObjectKey());
    }

    private static ZoneId zona(com.citacloud.springboot.contacloud.app.models.Empresa empresa) { return ZoneId.of(empresa.getZonaHoraria()); }
    private static TenantPrincipal principal() { return TenantContext.principalActual(); }
}
