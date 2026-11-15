package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.AsientoContableRepository;
import com.citacloud.springboot.contacloud.app.repositories.CuentaContableRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly=true)
public class AsientoContableService {
    private static final String VER="@empresaModuloService.habilitado('CONTABILIDAD') and hasAuthority('asientos.ver')";
    private static final String CREAR=VER+" and hasAuthority('asientos.crear')";
    private static final String EDITAR=VER+" and hasAuthority('asientos.editar')";
    private static final String POST=VER+" and hasAuthority('asientos.contabilizar')";
    private static final Set<Tipo> MANUALES=Set.of(Tipo.DIARIO,Tipo.APERTURA,Tipo.AJUSTE);
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final AsientoContableRepository repo;
    private final CuentaContableRepository catalogo;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datos;
    private final AuditoriaService auditoria;

    public AsientoContableService(AsientoContableRepository repo,CuentaContableRepository catalogo,EmpresaRepository empresas,
                                 DatosEmpresaRepository datos,AuditoriaService auditoria) {
        this.repo=repo;
        this.catalogo=catalogo;
        this.empresas=empresas;
        this.datos=datos;
        this.auditoria=auditoria;
    }

    @PreAuthorize(VER)
    public LocalDate hoy() { return LocalDate.now(ZoneId.of(empresa(principal()).getZonaHoraria())); }

    @PreAuthorize(VER)
    public Moneda monedaBase() { return monedaBase(principal()); }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Fila> listar(Filtro f,int pagina,int tamano) {
        if(pagina<0 || !Set.of(10,25,50,100).contains(tamano) || f.desde()!=null&&f.hasta()!=null&&f.desde().isAfter(f.hasta()))
            throw new ReglaNegocioException("Revisa el rango de fechas y la paginación.");
        var filtro=new Filtro(f.desde(),f.hasta(),f.tipo(),f.estado(),f.origen(),Objects.toString(texto(f.buscar(),150),""));
        return repo.listar(principal(),filtro,PageRequest.of(pagina,tamano));
    }

    @PreAuthorize(VER)
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detalle detalle(UUID id) { return detalle(principal(),id); }

    @PreAuthorize("("+CREAR+") or ("+EDITAR+")")
    public List<Cuenta> cuentas(String texto,int offset,int limite) {
        if(offset<0||limite<1||limite>100) throw new ReglaNegocioException("Paginación inválida.");
        return repo.buscar(principal(),Objects.toString(texto(texto,150),""),offset,limite);
    }

    @PreAuthorize(CREAR)
    @Transactional
    public UUID crear(Entrada solicitud,UUID clave) {
        var p=principal();
        catalogo.bloquearEmpresa(p);
        return guardar(p,null,0,solicitud,clave);
    }

    @PreAuthorize(EDITAR)
    @Transactional
    public void editar(UUID id,long version,Entrada solicitud) {
        var p=principal();
        catalogo.bloquearEmpresa(p);
        guardar(p,id,version,solicitud,null);
    }

    @PreAuthorize(POST+" and ((#id == null and hasAuthority('asientos.crear')) or (#id != null and hasAuthority('asientos.editar')))")
    @Transactional
    public UUID guardarYContabilizar(UUID id,long version,Entrada entrada,UUID clave) {
        exigirClave(clave);
        var p=principal();
        catalogo.bloquearEmpresa(p);
        var conocido=id==null?repo.porSolicitud(p,clave).orElse(null):id;
        if(conocido!=null && clave.equals(detalle(p,conocido).contabilizacion())) return conocido;
        var guardado=guardar(p,id,version,entrada,clave);
        publicar(p,detalle(p,guardado),clave);
        return guardado;
    }

    @PreAuthorize(POST)
    @Transactional
    public void contabilizar(UUID id,long version,UUID clave) {
        exigirClave(clave);
        var p=principal();
        catalogo.bloquearEmpresa(p);
        var d=detalle(p,id);
        if(clave.equals(d.contabilizacion())) return;
        exigirBorrador(d,version);
        publicar(p,d,clave);
    }

    @PreAuthorize(VER+" and hasAuthority('asientos.reversar')")
    @Transactional
    public UUID reversar(UUID id,long version,LocalDate fecha,String motivo,UUID clave) {
        exigirClave(clave);
        var p=principal();
        catalogo.bloquearEmpresa(p);
        var original=detalle(p,id);
        if(original.fila().estado()==Estado.REVERSADO) {
            var existente=repo.reversa(p,id).orElseThrow();
            if(clave.equals(detalle(p,existente).contabilizacion())) return existente;
        }
        if(!original.reversible() || original.version()!=version)
            throw new ReglaNegocioException("El asiento cambió o no se puede reversar desde Contabilidad.");
        if(fecha==null || fecha.isBefore(original.fila().fecha()))
            throw new ReglaNegocioException("La fecha de reversión debe ser igual o posterior a la del asiento original.");
        var razon=requerido(motivo,1000,"Indica el motivo de reversión.");
        periodo(p,fecha);
        var e=new Entrada(fecha,Tipo.REVERSION,original.moneda().id(),"Reversión de "+original.fila().numero(),
            original.fila().numero(),null,movimientos(original,true));
        var lineas=validarLineas(p,e.lineas());
        partidaDoble(e.lineas());
        var reversa=insertar(p,e,original.moneda(),lineas,null,id,razon,clave);
        repo.contabilizar(p,reversa,clave,emisor(p));
        repo.marcarReversado(p,id);
        auditar(p,"REVERSAR",detalle(p,reversa),id);
        return reversa;
    }

    // Punto de entrada para módulos con su propia autorización y transacción de negocio.
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID registrarAutomatico(Fuente origen,LocalDate fecha,UUID moneda,String concepto,List<LineaConfigurada> lineas) {
        var p=principal();
        var fuente=validarFuente(origen);
        catalogo.bloquearEmpresa(p);
        var existente=repo.porFuente(p,fuente);
        if(existente.isPresent()) return existente.get();
        if(lineas==null || lineas.stream().anyMatch(l->l==null || l.clave()==null || l.clave().isBlank()))
            throw new ReglaNegocioException("Indica las claves contables de los movimientos.");
        var cuentas=repo.claves(p,lineas.stream().map(LineaConfigurada::clave).collect(Collectors.toSet()));
        var e=normalizar(new Entrada(fecha,Tipo.AUTOMATICO,moneda,concepto,fuente.numero(),null,
            lineas.stream().map(l->new Movimiento(cuentas.get(l.clave()),l.descripcion(),l.debito(),l.credito())).toList()));
        periodo(p,e.fecha());
        var base=validarMoneda(p,e.moneda());
        var renglones=validarLineas(p,e.lineas());
        partidaDoble(e.lineas());
        var clave=UUID.randomUUID();
        var id=insertar(p,e,base,renglones,fuente,null,null,clave);
        repo.contabilizar(p,id,clave,emisor(p));
        auditar(p,"CONTABILIZAR_AUTOMATICO",detalle(p,id),null);
        return id;
    }

    @PreAuthorize(VER+" and hasAuthority('asientos.pdf')")
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Reporte reporte(UUID id) {
        var p=principal();
        var d=detalle(p,id);
        return new Reporte(d,d.emisor()==null?emisor(p):d.emisor());
    }

    private UUID guardar(TenantPrincipal p,UUID id,long version,Entrada solicitud,UUID clave) {
        if(id==null) {
            exigirClave(clave);
            var encontrado=repo.porSolicitud(p,clave);
            if(encontrado.isPresent()) return encontrado.get();
        } else exigirBorrador(detalle(p,id),version);
        var e=normalizar(solicitud);
        if(!MANUALES.contains(e.tipo())) throw new ReglaNegocioException("Selecciona un tipo de asiento manual.");
        if(repo.periodoCerrado(p,e.fecha())) throw new ReglaNegocioException("El período fiscal está cerrado.");
        var moneda=validarMoneda(p,e.moneda());
        var lineas=validarLineas(p,e.lineas());
        UUID guardado=id;
        if(id==null) guardado=insertar(p,e,moneda,lineas,null,null,null,clave);
        else {
            repo.editar(p,id,e,moneda);
            repo.reemplazarLineas(p,id,lineas);
        }
        auditar(p,id==null?"CREAR":"EDITAR",detalle(p,guardado),null);
        return guardado;
    }

    private UUID insertar(TenantPrincipal p,Entrada e,Moneda moneda,List<Linea> lineas,Fuente fuente,
                          UUID original,String motivo,UUID clave) {
        var id=UUID.randomUUID();
        repo.crear(p,id,repo.siguiente(p),e,moneda,fuente,original,motivo,clave);
        repo.reemplazarLineas(p,id,lineas);
        return id;
    }

    private void publicar(TenantPrincipal p,Detalle d,UUID clave) {
        periodo(p,d.fila().fecha());
        validarMoneda(p,d.moneda().id());
        var movimientos=movimientos(d,false);
        var lineas=validarLineas(p,movimientos);
        partidaDoble(movimientos);
        repo.reemplazarLineas(p,d.fila().id(),lineas);
        repo.contabilizar(p,d.fila().id(),clave,emisor(p));
        auditar(p,"CONTABILIZAR",detalle(p,d.fila().id()),null);
    }

    private List<Linea> validarLineas(TenantPrincipal p,List<Movimiento> movimientos) {
        if(movimientos.stream().anyMatch(l->l.cuenta()==null)) throw new ReglaNegocioException("Selecciona una cuenta de movimiento válida en cada línea.");
        var cuentas=repo.cuentas(p,movimientos.stream().map(Movimiento::cuenta).collect(Collectors.toSet()));
        return movimientos.stream().map(l->{
            var c=cuentas.get(l.cuenta());
            if(c==null||!c.activa()||!c.movimiento())
                throw new ReglaNegocioException("Cada línea debe utilizar una cuenta de movimiento activa de la empresa.");
            return new Linea(c,l.descripcion(),l.debito(),l.credito());
        }).toList();
    }

    static Entrada normalizar(Entrada e) {
        if(e==null||e.fecha()==null||e.tipo()==null||e.moneda()==null)
            throw new ReglaNegocioException("Completa la fecha, el tipo y la moneda.");
        var renglones=e.lineas()==null?List.<Movimiento>of():e.lineas();
        if(renglones.size()>500) throw new ReglaNegocioException("El asiento admite hasta 500 líneas.");
        var lineas=renglones.stream().map(l->{
            if(l==null) throw new ReglaNegocioException("Revisa las líneas del asiento.");
            var debe=importe(l.debito());
            var haber=importe(l.credito());
            if(debe.signum()>0&&haber.signum()>0) throw new ReglaNegocioException("Una línea solo puede tener débito o crédito.");
            return new Movimiento(l.cuenta(),texto(l.descripcion(),500),debe,haber);
        }).toList();
        return new Entrada(e.fecha(),e.tipo(),e.moneda(),requerido(e.concepto(),500,"Indica el concepto."),
            texto(e.referencia(),150),texto(e.observacion(),2000),lineas);
    }

    public static Total totales(List<Movimiento> movimientos) {
        BigDecimal debito=BigDecimal.ZERO,credito=BigDecimal.ZERO;
        for(var m:movimientos) {
            debito=debito.add(Objects.requireNonNullElse(m.debito(),BigDecimal.ZERO));
            credito=credito.add(Objects.requireNonNullElse(m.credito(),BigDecimal.ZERO));
        }
        return new Total(debito,credito);
    }

    static void partidaDoble(List<Movimiento> movimientos) {
        if(movimientos.size()<2 || movimientos.stream().anyMatch(l->l.debito().signum()==0&&l.credito().signum()==0))
            throw new ReglaNegocioException("Para contabilizar, registra al menos dos líneas con un débito o un crédito positivo.");
        if(totales(movimientos).diferencia().signum()!=0) throw new ReglaNegocioException("Los débitos y créditos deben cuadrar.");
    }

    private static BigDecimal importe(BigDecimal valor) {
        var monto=Objects.requireNonNullElse(valor,BigDecimal.ZERO);
        try {
            monto=monto.setScale(2,RoundingMode.UNNECESSARY);
            if(monto.signum()>=0&&monto.precision()<=19) return monto;
        } catch(ArithmeticException ex) {
            throw new ReglaNegocioException("Los importes admiten hasta dos decimales.");
        }
        throw new ReglaNegocioException("Indica un importe no negativo dentro del límite permitido.");
    }

    private static List<Movimiento> movimientos(Detalle d,boolean invertir) {
        return d.lineas().stream().map(l->new Movimiento(l.cuenta().id(),l.descripcion(),
            invertir?l.credito():l.debito(),invertir?l.debito():l.credito())).toList();
    }

    private static Fuente validarFuente(Fuente f) {
        if(f==null||f.origen()==null||f.origen()==Origen.MANUAL||f.id()==null)
            throw new ReglaNegocioException("Indica el documento de origen.");
        return new Fuente(f.origen(),requerido(f.tipo(),80,"Indica el tipo de documento."),f.id(),
            requerido(f.numero(),120,"Indica el número del documento."),requerido(f.evento(),80,"Indica el evento."));
    }

    private static void exigirBorrador(Detalle d,long version) {
        if(d.version()!=version||d.fila().estado()!=Estado.BORRADOR||d.fila().origen()!=Origen.MANUAL)
            throw new ReglaNegocioException("El asiento cambió o ya no es un borrador editable.");
    }

    private void periodo(TenantPrincipal p,LocalDate fecha) {
        if(!repo.periodoAbierto(p,fecha)) throw new ReglaNegocioException("La fecha debe pertenecer a un período fiscal abierto.");
    }

    private Moneda validarMoneda(TenantPrincipal p,UUID id) {
        var base=monedaBase(p);
        if(!base.id().equals(id)) throw new ReglaNegocioException("El asiento debe utilizar la moneda base de la empresa.");
        return base;
    }

    private Moneda monedaBase(TenantPrincipal p) {
        return repo.monedaBase(p).orElseThrow(()->new ReglaNegocioException("Configura una moneda base activa para la empresa."));
    }

    private Detalle detalle(TenantPrincipal p,UUID id) {
        return repo.detalle(p,id).orElseThrow(()->new RecursoNoEncontradoException("Asiento no encontrado."));
    }

    private com.citacloud.springboot.contacloud.app.models.Empresa empresa(TenantPrincipal p) {
        return empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private Empresa emisor(TenantPrincipal p) {
        var e=empresa(p);
        var d=datos.findByEmpresaId(p.empresaId()).orElse(null);
        var comercial=d==null?null:d.getNombreComercial();
        return new Empresa(comercial==null||comercial.isBlank()?e.getNombre():comercial,e.getIdentificacionFiscal(),
            d==null?null:d.getDireccion(),d==null?null:d.getTelefono(),e.getPaisCodigo(),e.getLogoObjectKey());
    }

    private void auditar(TenantPrincipal p,String accion,Detalle d,UUID original) {
        var trace=TraceContext.currentOrCreate();
        var info=new LinkedHashMap<String,Object>();
        info.put("traceId",trace);
        info.put("numero",d.fila().numero());
        info.put("estado",d.fila().estado());
        info.put("total",d.fila().total());
        info.put("original",original);
        info.put("fuente",d.fuente());
        auditoria.registrar(accion,"AsientoContable",d.fila().id(),JSON.writeValueAsString(info));
        LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("TraceId",trace).addKeyValue("TenantId",p.tenantId())
            .addKeyValue("CompanyId",p.empresaId()).addKeyValue("AsientoId",d.fila().id()).addKeyValue("Operacion",accion).log("asiento_contable");
    }

    private static void exigirClave(UUID clave) {
        if(clave==null) throw new ReglaNegocioException("Solicitud de operación inválida.");
    }

    private static String requerido(String valor,int max,String mensaje) {
        var limpio=texto(valor,max);
        if(limpio==null) throw new ReglaNegocioException(mensaje);
        return limpio;
    }

    private static String texto(String valor,int max) {
        if(valor==null||valor.isBlank()) return null;
        var limpio=valor.strip();
        if(limpio.length()>max) throw new ReglaNegocioException("Un campo supera el máximo de "+max+" caracteres.");
        return limpio;
    }

    private static TenantPrincipal principal() { return TenantContext.principalActual(); }
}
