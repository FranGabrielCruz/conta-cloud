package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.DepreciacionRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DepreciacionService {
    public static final String VER="depreciaciones.ver";
    public static final String PROCESAR="depreciaciones.procesar";
    public static final String PDF="depreciaciones.pdf";
    private static final String ACCESO="@empresaModuloService.habilitado('ACTIVOS_FIJOS') and hasAuthority('"+VER+"')";
    private static final String PROCESO=ACCESO+" and hasAuthority('"+PROCESAR+"')";
    private static final Logger LOG=LoggerFactory.getLogger(DepreciacionService.class);
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final DepreciacionRepository repo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datosEmpresa;
    private final AuditoriaService auditoria;
    private final ContabilizacionOperativaService contabilidad;

    public DepreciacionService(DepreciacionRepository repo,EmpresaRepository empresas,DatosEmpresaRepository datosEmpresa,
                               AuditoriaService auditoria,ContabilizacionOperativaService contabilidad) {
        this.repo=repo;
        this.empresas=empresas;
        this.datosEmpresa=datosEmpresa;
        this.auditoria=auditoria;
        this.contabilidad=contabilidad;
    }

    @PreAuthorize(ACCESO)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Fila> historial(LocalDate desde,LocalDate hasta,int pagina,int tamano) {
        if (pagina<0 || !Set.of(10,25,50,100).contains(tamano) || (desde!=null && hasta!=null && desde.isAfter(hasta)))
            throw new ReglaNegocioException("Revisa el rango de fechas y la paginación.");
        return repo.historial(TenantContext.principalActual(),desde,hasta,PageRequest.of(pagina,tamano));
    }

    @PreAuthorize(ACCESO)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Indicadores indicadores() {
        var p=TenantContext.principalActual();
        var mes=mesActual(empresa(p));
        var indicadores=repo.indicadores(p,mes);
        long activos=repo.candidatos(p,mes).stream().filter(a->configuracionValida(a)
            && a.costo().subtract(a.anteriorActual()).subtract(a.ajustes()).compareTo(a.residual())>0).count();
        return new Indicadores(indicadores.ultimo(),activos,indicadores.acumulados());
    }

    @PreAuthorize(PROCESO)
    @Transactional(readOnly=true)
    public YearMonth siguiente() {
        var p=procesador();
        return repo.ultimo(p).map(u->u.periodo().plusMonths(1)).orElseGet(()->mesActual(empresa(p)));
    }

    @PreAuthorize(PROCESO)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public VistaPrevia calcular(YearMonth periodo) {
        var p=procesador();
        validarPeriodo(p,periodo,repo.ultimo(p).orElse(null),false);
        evento(p,periodo,"depreciacion_calculo_iniciado",0);
        var previa=calcular(p,periodo,UUID.randomUUID());
        evento(p,periodo,"depreciacion_calculo_completado",previa.lineas().size());
        return previa;
    }

    @PreAuthorize(PROCESO)
    @Transactional
    public UUID procesar(YearMonth periodo,String revision,UUID solicitud) {
        var p=procesador();
        if (solicitud==null || revision==null) throw new ReglaNegocioException("Calcula la depreciación antes de procesarla.");
        try {
            repo.bloquearEmpresa(p);
            var existente=repo.solicitud(p,solicitud).orElse(null);
            if (existente!=null) {
                if (existente.usuario().equals(p.usuarioId()) && existente.periodo().equals(periodo)) return existente.id();
                throw new ReglaNegocioException("La solicitud de procesamiento ya fue utilizada.");
            }
            var ultimo=repo.ultimo(p).orElse(null);
            validarPeriodo(p,periodo,ultimo,true);
            evento(p,periodo,"depreciacion_proceso_iniciado",0);
            repo.bloquearActivos(p);
            var previa=calcular(p,periodo,solicitud);
            if (!previa.revision().equals(revision))
                throw new ReglaNegocioException("Los datos del cálculo cambiaron. Vuelve a calcular la depreciación.");
            if (previa.lineas().isEmpty())
                throw new ReglaNegocioException("No existen activos elegibles para depreciar en este período.");
            var proceso=new Proceso(UUID.randomUUID(),ultimo==null?1:ultimo.numero()+1,periodo,previa.fecha(),null,
                p.usuarioId(),p.nombre(),solicitud,emisor(p));
            return guardar(p,previa,proceso);
        } catch (RuntimeException ex) {
            evento(p,periodo,"depreciacion_proceso_rechazado",0);
            throw ex;
        }
    }

    private UUID guardar(TenantPrincipal p,VistaPrevia previa,Proceso proceso) {
        repo.guardar(p,proceso,previa.lineas());
        contabilidad.depreciacion(proceso.id(),proceso.codigo(),proceso.fecha(),previa.totales());
        auditoria.registrar("PROCESAR","DepreciacionActivoFijo",proceso.id(),JSON.writeValueAsString(java.util.Map.of(
            "numero",proceso.codigo(),"periodo",proceso.periodo().toString(),"tenantId",p.tenantId(),"empresaId",p.empresaId(),
            "usuarioId",p.usuarioId(),"activos",previa.lineas().size(),"totales",previa.totales(),
            "resultado","PROCESADO","traceId",TraceContext.currentOrCreate())));
        evento(p,proceso.periodo(),"depreciacion_proceso_registrado",previa.lineas().size());
        return proceso.id();
    }

    @PreAuthorize(ACCESO)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detalle detalle(UUID id) { return cargarDetalle(id); }

    @PreAuthorize(ACCESO+" and hasAuthority('"+PDF+"')")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detalle documento(UUID id) { return cargarDetalle(id); }

    private Detalle cargarDetalle(UUID id) {
        var p=TenantContext.principalActual();
        var proceso=repo.procesoVisible(p,id).orElseThrow(()->new RecursoNoEncontradoException("Depreciación no encontrada."));
        var lineas=repo.detalle(p,id);
        return new Detalle(proceso,lineas,totales(lineas));
    }

    private void validarPeriodo(TenantPrincipal p,YearMonth periodo,Proceso ultimo,boolean bloqueo) {
        if (periodo==null || periodo.getYear()<1900 || periodo.isAfter(mesActual(empresa(p))))
            throw new ReglaNegocioException("Selecciona un período mensual válido.");
        if (ultimo!=null && !periodo.isAfter(ultimo.periodo()))
            throw new ReglaNegocioException("El período seleccionado ya fue procesado o es anterior al último registrado.");
        if (!repo.periodoAbierto(p,periodo,bloqueo))
            throw new ReglaNegocioException("El mes debe estar cubierto por un período fiscal abierto.");
    }

    private VistaPrevia calcular(TenantPrincipal p,YearMonth periodo,UUID solicitud) {
        var lineas=new ArrayList<Linea>();
        var incidencias=new ArrayList<String>();
        for (var a:repo.candidatos(p,periodo)) {
            if (!configuracionValida(a) || a.movimientoPosterior() || a.anterior().add(a.ajustes()).compareTo(a.costo())>0) {
                incidencias.add("El activo "+a.codigo()+" tiene parámetros o movimientos incompatibles con el período.");
                continue;
            }
            var linea=lineaRecta(a,periodo);
            if (linea!=null) lineas.add(linea);
        }
        if (!incidencias.isEmpty()) evento(p,periodo,"depreciacion_activos_excluidos",incidencias.size());
        var filas=List.copyOf(lineas);
        return new VistaPrevia(periodo,periodo.atEndOfMonth(),filas,totales(filas),List.copyOf(incidencias),
            revision(periodo,filas,incidencias),solicitud);
    }

    static Linea lineaRecta(Candidato a,YearMonth periodo) {
        var base=a.costo().subtract(a.residual());
        var libros=a.costo().subtract(a.anterior()).subtract(a.ajustes());
        var disponible=libros.subtract(a.residual());
        if (disponible.signum()<=0) return null;
        long mes=ChronoUnit.MONTHS.between(YearMonth.from(a.inicio()),periodo)+1;
        if (mes<1) return null;
        var cuota=mes>=a.vida()?disponible:base.divide(BigDecimal.valueOf(a.vida()),2,RoundingMode.HALF_UP).min(disponible);
        return cuota.signum()==0?null:new Linea(a.id(),a.sucursal(),a.codigo(),a.nombre(),a.moneda(),
            a.costo(),base,a.anterior(),cuota,libros.subtract(cuota));
    }

    private static boolean configuracionValida(Candidato a) {
        return "LINEA_RECTA".equals(a.metodo()) && a.inicio()!=null && a.vida()!=null && a.vida()>0
            && a.residual()!=null && a.residual().signum()>=0 && a.costo().compareTo(a.residual())>0;
    }

    static List<Total> totales(List<Linea> lineas) {
        var porMoneda=new TreeMap<String,Total>();
        for (var l:lineas) {
            var total=new Total(l.moneda(),1,l.costo(),l.anterior(),l.importe(),l.acumulada(),l.libros());
            porMoneda.merge(l.moneda(),total,(a,b)->new Total(a.moneda(),a.activos()+b.activos(),a.costo().add(b.costo()),
                a.anterior().add(b.anterior()),a.importe().add(b.importe()),a.acumulada().add(b.acumulada()),a.libros().add(b.libros())));
        }
        return List.copyOf(porMoneda.values());
    }

    private static String revision(YearMonth periodo,List<Linea> lineas,List<String> incidencias) {
        try {
            byte[] contenido=JSON.writeValueAsString(List.of(periodo.toString(),lineas,incidencias)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static TenantPrincipal procesador() {
        var p=TenantContext.principalActual();
        if (!p.accesoTodasSucursales()) throw new AccessDeniedException("Procesar requiere acceso a todas las sucursales.");
        return p;
    }

    private com.citacloud.springboot.contacloud.app.models.Empresa empresa(TenantPrincipal p) {
        return empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private Empresa emisor(TenantPrincipal p) {
        var e=empresa(p);
        var d=datosEmpresa.findByEmpresaId(p.empresaId()).orElse(null);
        String nombre=d==null?null:d.getNombreComercial();
        return new Empresa(nombre==null||nombre.isBlank()?e.getNombre():nombre,e.getIdentificacionFiscal(),
            d==null?null:d.getDireccion(),d==null?null:d.getTelefono(),e.getPaisCodigo(),e.getLogoObjectKey());
    }

    private static YearMonth mesActual(com.citacloud.springboot.contacloud.app.models.Empresa e) {
        return YearMonth.now(ZoneId.of(e.getZonaHoraria()));
    }

    private static void evento(TenantPrincipal p,YearMonth periodo,String evento,int activos) {
        LOG.atInfo().addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId())
            .addKeyValue("UserId",p.usuarioId()).addKeyValue("Periodo",periodo)
            .addKeyValue("AssetCount",activos).addKeyValue("TraceId",TraceContext.currentOrCreate()).log(evento);
    }
}
