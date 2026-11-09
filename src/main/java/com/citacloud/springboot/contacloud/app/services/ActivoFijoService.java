package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.ActivoFijoRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ActivoFijoService {
    public static final String MODULO = "ACTIVOS_FIJOS";
    public static final String VER = "activos_fijos.ver";
    public static final String CREAR = "activos_fijos.crear";
    public static final String EDITAR = "activos_fijos.editar";
    public static final String REPORTAR = "activos_fijos.reportar";
    private static final String ACCESO = "@empresaModuloService.habilitado('" + MODULO + "') and hasAuthority('" + VER + "')";
    private static final String PUEDE_CREAR = ACCESO + " and hasAuthority('" + CREAR + "')";
    private static final String PUEDE_EDITAR = ACCESO + " and hasAuthority('" + EDITAR + "')";
    private static final String PUEDE_REPORTAR = ACCESO + " and hasAuthority('" + REPORTAR + "')";
    private static final Logger LOG = LoggerFactory.getLogger(ActivoFijoService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ActivoFijoRepository repo;
    private final AuditoriaService auditoria;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;

    public ActivoFijoService(ActivoFijoRepository repo, AuditoriaService auditoria,
                             EmpresaRepository empresas, DatosEmpresaRepository datosEmpresa) {
        this.repo = repo;
        this.auditoria = auditoria;
        this.empresas = empresas;
        this.datosEmpresa = datosEmpresa;
    }

    @PreAuthorize(ACCESO)
    @Transactional(readOnly = true)
    public Catalogos filtros() {
        return repo.catalogos(TenantContext.principalActual(), false);
    }

    @PreAuthorize(PUEDE_CREAR + " or (" + PUEDE_EDITAR + ")")
    @Transactional(readOnly = true)
    public Catalogos formulario() {
        return repo.catalogos(TenantContext.principalActual(), true);
    }

    @PreAuthorize(PUEDE_CREAR + " or (" + PUEDE_EDITAR + ")")
    @Transactional(readOnly = true)
    public List<Factura> facturas(UUID proveedor, String texto, int offset, int limite) {
        if (offset<0 || limite<1 || limite>100) throw new ReglaNegocioException("Paginación inválida.");
        return proveedor == null ? List.of() : repo.facturas(TenantContext.principalActual(), proveedor,
            Objects.toString(texto(texto,150),""), offset, limite);
    }

    @PreAuthorize(ACCESO)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Listado consultar(Filtro entrada, int pagina, int tamano) {
        if (pagina<0 || !Set.of(10,25,50,100).contains(tamano)) throw new ReglaNegocioException("Paginación inválida.");
        var p=TenantContext.principalActual();
        var filtro=filtro(p,entrada);
        var page=PageRequest.of(pagina,tamano);
        var totales=repo.totales(p,filtro);
        long cantidad=totales.stream().mapToLong(Totales::cantidad).sum();
        var filas=page.getOffset()>=cantidad?List.<Resumen>of():repo.listar(p,filtro,page.getOffset(),tamano);
        return new Listado(new PageImpl<>(filas,page,cantidad),totales);
    }

    @PreAuthorize(ACCESO)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detalle detalle(UUID id) {
        var p=TenantContext.principalActual();
        var activo=registro(p,id);
        return new Detalle(activo,repo.asignaciones(p,id),repo.movimientos(p,id));
    }

    @PreAuthorize(PUEDE_CREAR)
    @Transactional
    public Registro crear(Datos entrada) {
        var p=TenantContext.principalActual();
        var datos=validar(entrada);
        referencias(p,datos,null);
        var id=UUID.randomUUID();
        var codigo=repo.siguienteCodigo(p);
        repo.insertar(p,id,codigo,datos);
        var creado=registro(p,id);
        repo.guardarAsignacion(p,null,creado);
        auditar(p,null,creado,"CREAR");
        return creado;
    }

    @PreAuthorize(PUEDE_EDITAR)
    @Transactional
    public Registro editar(UUID id, long version, Datos entrada) {
        var p=TenantContext.principalActual();
        if (id==null || !repo.bloquear(p,id)) throw noEncontrado();
        var anterior=registro(p,id);
        if (anterior.version()!=version) throw conflicto();
        if (anterior.resumen().estado()==Estado.BAJA) throw new ReglaNegocioException("Los activos dados de baja solo se pueden consultar.");
        var datos=validar(entrada);
        if (anterior.finanzasBloqueadas() && (!anterior.datos().adquisicion().equals(datos.adquisicion())
                || !anterior.datos().depreciacion().equals(datos.depreciacion()))) {
            evento(p,anterior.resumen(),"edicion_financiera_restringida");
            throw new ReglaNegocioException("Los datos de adquisición y depreciación están protegidos porque el activo ya posee movimientos financieros.");
        }
        if (anterior.datos().equals(datos)) return anterior;
        referencias(p,datos,anterior.datos());
        if (!repo.actualizar(p,id,version,datos)) throw conflicto();
        var actualizado=registro(p,id);
        boolean reasignado=!anterior.datos().asignacion().equals(datos.asignacion());
        if (reasignado) repo.guardarAsignacion(p,anterior,actualizado);
        auditar(p,anterior,actualizado,reasignado?"EDITAR_ASIGNACION":"EDITAR");
        return actualizado;
    }

    @PreAuthorize(PUEDE_REPORTAR)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Reporte reporte(Filtro entrada) {
        var p=TenantContext.principalActual();
        var filtro=filtro(p,entrada);
        var empresa=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(ActivoFijoService::noEncontrado);
        var datos=datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        var filtros=new java.util.ArrayList<String>();
        if (filtro.categoria()!=null || filtro.sucursal()!=null) {
            var catalogos=repo.catalogos(p,false);
            if (filtro.categoria()!=null) {
                var categoria=catalogos.categorias().stream().filter(c->c.codigo().equals(filtro.categoria()))
                    .map(Categoria::nombre).findFirst().orElse("Sin coincidencias");
                filtros.add("Categoría: "+categoria);
            }
            if (filtro.sucursal()!=null) {
                var sucursal=catalogos.sucursales().stream().filter(s->s.id().equals(filtro.sucursal()))
                    .map(Opcion::nombre).findFirst().orElse("Sin coincidencias");
                filtros.add("Sucursal: "+sucursal);
            }
        }
        if (filtro.ubicacion()!=null) filtros.add("Ubicación: "+filtro.ubicacion());
        if (filtro.estado()!=null) filtros.add("Estado: "+filtro.estado().etiqueta());
        if (!filtro.buscar().isEmpty()) filtros.add("Búsqueda: "+filtro.buscar());
        var activos=repo.listar(p,filtro,null,0);
        LOG.atInfo().addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("UserId",p.usuarioId()).addKeyValue("TraceId",TraceContext.currentOrCreate())
            .addKeyValue("Cantidad",activos.size()).log("activos_fijos_reporte");
        return new Reporte(datos==null||texto(datos.getNombreComercial(),150)==null?empresa.getNombre():datos.getNombreComercial(),
            empresa.getIdentificacionFiscal(),datos==null?null:datos.getDireccion(),datos==null?null:datos.getTelefono(),
            empresa.getPaisCodigo(),empresa.getLogoObjectKey(),LocalDate.now(ZoneId.of(empresa.getZonaHoraria())),
            List.copyOf(filtros),activos,repo.totales(p,filtro));
    }

    private void referencias(TenantPrincipal p, Datos d, Datos anterior) {
        var cat=repo.catalogos(p,true);
        var a=d.adquisicion();
        var s=d.asignacion();
        if (cat.categorias().stream().noneMatch(c->c.codigo().equals(d.categoria()))) throw new ReglaNegocioException("Selecciona una categoría válida.");
        sucursalPermitida(p,s.sucursal());
        if ((anterior==null || !s.sucursal().equals(anterior.asignacion().sucursal()))
                && !contiene(cat.sucursales(),s.sucursal())) throw new ReglaNegocioException("Selecciona una sucursal activa.");
        if ((anterior==null || !a.moneda().equals(anterior.adquisicion().moneda()))
                && cat.monedas().stream().noneMatch(m->m.id().equals(a.moneda()))) throw new ReglaNegocioException("Selecciona una moneda activa.");
        if (a.proveedor()!=null && (anterior==null || !Objects.equals(a.proveedor(),anterior.adquisicion().proveedor()))
                && !contiene(cat.proveedores(),a.proveedor())) throw new ReglaNegocioException("Selecciona un proveedor válido.");
        if (s.responsable()!=null && (anterior==null || !Objects.equals(s.responsable(),anterior.asignacion().responsable()))
                && !contiene(cat.responsables(),s.responsable())) throw new ReglaNegocioException("Selecciona un responsable válido.");
        if (a.factura()!=null && cambiaVinculo(a,anterior) && !repo.facturaValida(p,a))
            throw new ReglaNegocioException("Selecciona una factura registrada del proveedor en la moneda elegida.");
    }

    private static boolean cambiaVinculo(Adquisicion a, Datos anterior) {
        if (anterior==null) return true;
        var previo=anterior.adquisicion();
        return !a.factura().equals(previo.factura()) || !Objects.equals(a.proveedor(),previo.proveedor())
            || !a.moneda().equals(previo.moneda());
    }

    static Datos validar(Datos d) {
        if (d==null || d.adquisicion()==null || d.asignacion()==null || d.depreciacion()==null)
            throw new ReglaNegocioException("Revisa los datos requeridos del activo.");
        var a=d.adquisicion(); var s=d.asignacion(); var dep=d.depreciacion();
        String nombre=texto(d.nombre(),180), categoria=texto(d.categoria(),24);
        var costo=importe(a.costo());
        if (nombre==null || categoria==null || a.fecha()==null || costo==null || costo.signum()<=0 || a.moneda()==null || s.sucursal()==null)
            throw new ReglaNegocioException("Completa el nombre, categoría, fecha, costo, moneda y sucursal del activo.");
        if (a.factura()!=null && a.proveedor()==null) throw new ReglaNegocioException("Selecciona el proveedor de la factura.");
        var residual=importe(dep.residual());
        if (dep.depreciable()) {
            if (dep.metodo()==null || dep.vidaMeses()==null || dep.vidaMeses()<1 || dep.vidaMeses()>1200
                    || dep.inicio()==null || dep.inicio().isBefore(a.fecha()))
                throw new ReglaNegocioException("Completa el método, la vida útil y una fecha de inicio igual o posterior a la adquisición.");
            if (residual==null || residual.signum()<0 || residual.compareTo(costo)>=0)
                throw new ReglaNegocioException("El valor residual debe ser cero o mayor y menor que el costo.");
        } else if (dep.metodo()!=null || dep.vidaMeses()!=null || dep.residual()!=null || dep.inicio()!=null) {
            throw new ReglaNegocioException("Un activo no depreciable no admite parámetros de depreciación.");
        }
        return new Datos(nombre,categoria,texto(d.marca(),100),texto(d.modelo(),100),texto(d.serie(),120),
            new Adquisicion(a.fecha(),costo,a.moneda(),a.proveedor(),a.factura(),texto(a.referencia(),100)),
            new Asignacion(s.sucursal(),texto(s.ubicacion(),150),s.responsable()),
            new Depreciacion(dep.depreciable(),dep.metodo(),dep.vidaMeses(),residual,dep.inicio()),texto(d.observacion(),1000));
    }

    private static BigDecimal importe(BigDecimal importe) {
        if (importe==null) return null;
        try {
            var normalizado=importe.setScale(2,RoundingMode.UNNECESSARY);
            if (normalizado.precision()<=19) return normalizado;
        } catch (ArithmeticException ex) {
            throw new ReglaNegocioException("Los importes admiten hasta dos decimales.");
        }
        throw new ReglaNegocioException("El importe excede el máximo permitido.");
    }

    private static boolean contiene(List<Opcion> opciones, UUID id) {
        return opciones.stream().anyMatch(o->o.id().equals(id));
    }

    private static String texto(String valor, int max) {
        if (valor==null || valor.isBlank()) return null;
        String limpio=valor.strip();
        if (limpio.length()>max) throw new ReglaNegocioException("Un campo supera el máximo de "+max+" caracteres.");
        return limpio;
    }

    private static Filtro filtro(TenantPrincipal p, Filtro f) {
        if (f==null) return new Filtro(null,null,null,null,"");
        if (f.sucursal()!=null) sucursalPermitida(p,f.sucursal());
        return new Filtro(texto(f.categoria(),24),f.sucursal(),texto(f.ubicacion(),150),f.estado(),Objects.toString(texto(f.buscar(),150),""));
    }

    private static void sucursalPermitida(TenantPrincipal p, UUID id) {
        if (!p.accesoTodasSucursales() && !p.sucursalIds().contains(id))
            throw new ReglaNegocioException("No tienes acceso a la sucursal seleccionada.");
    }

    private Registro registro(TenantPrincipal p, UUID id) {
        if (id==null) throw noEncontrado();
        return repo.registro(p,id).orElseThrow(ActivoFijoService::noEncontrado);
    }

    private void auditar(TenantPrincipal p, Registro anterior, Registro actual, String accion) {
        var detalle=new LinkedHashMap<String,Object>();
        detalle.put("codigo",actual.resumen().codigo());
        detalle.put("tenantId",p.tenantId());
        detalle.put("sucursalId",actual.datos().asignacion().sucursal());
        detalle.put("traceId",TraceContext.currentOrCreate());
        detalle.put("anterior",anterior==null?null:anterior.datos());
        detalle.put("nuevo",actual.datos());
        auditoria.registrar(accion,"ActivoFijo",actual.resumen().id(),JSON.writeValueAsString(detalle));
        evento(p,actual.resumen(),accion);
    }

    private static void evento(TenantPrincipal p, Resumen r, String accion) {
        LOG.atInfo().addKeyValue("AssetId",r.id()).addKeyValue("AssetCode",r.codigo())
            .addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("UserId",p.usuarioId()).addKeyValue("TraceId",TraceContext.currentOrCreate())
            .addKeyValue("Operation",accion).log("activos_fijos");
    }

    private static RecursoNoEncontradoException noEncontrado() { return new RecursoNoEncontradoException("Activo no encontrado."); }
    private static ReglaNegocioException conflicto() { return new ReglaNegocioException("El activo cambió. Cierra el formulario y vuelve a abrirlo."); }
}
