package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Moneda;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Movimiento;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CierreContableService {
    private static final String VER="@empresaModuloService.habilitado('CONTABILIDAD') and hasAuthority('cierres_contables.ver')";
    private static final String CONFIGURAR=VER+" and hasAnyAuthority('configuracion_contable.editar','CONFIGURACION_CONTABLE_EDITAR')";
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger(CierreContableService.class);
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final CierreContableRepository repo;
    private final CuentaContableRepository cuentas;
    private final AsientoContableRepository asientosRepo;
    private final AsientoContableService asientos;
    private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datos;
    private final AuditoriaService auditoria;

    public CierreContableService(CierreContableRepository repo,CuentaContableRepository cuentas,
            AsientoContableRepository asientosRepo,AsientoContableService asientos,
            EmpresaRepository empresas,DatosEmpresaRepository datos,AuditoriaService auditoria) {
        this.repo=repo;
        this.cuentas=cuentas;
        this.asientosRepo=asientosRepo;
        this.asientos=asientos;
        this.empresas=empresas;
        this.datos=datos;
        this.auditoria=auditoria;
    }

    @PreAuthorize(VER)
    public Lista listar(Filtro filtro,int pagina,int tamano) {
        if(pagina<0||!Set.of(10,25,50,100).contains(tamano)) throw new ReglaNegocioException("Revisa la paginación.");
        var p=TenantContext.principalActual();
        return repo.listar(p,new Filtro(filtro.periodo(),filtro.estado(),limpiar(filtro.buscar(),150)),(long)pagina*tamano,tamano);
    }

    @PreAuthorize(VER)
    public List<Periodo> opciones(String buscar,int offset,int limite) {
        return repo.listar(TenantContext.principalActual(),new Filtro(null,null,limpiar(buscar,150)),Math.max(0,offset),
            Math.clamp(limite,1,100)).periodos();
    }

    @PreAuthorize(VER)
    public Detalle detalle(UUID id) {
        var p=TenantContext.principalActual();
        var periodo=periodo(p,id);
        var historia=repo.historial(p,id);
        var evaluacion=periodo.estado()==Estado.ABIERTO?evaluar(p,periodo,moneda(p)):null;
        boolean anterior=periodo.estado()==Estado.CERRADO&&historia.isEmpty()&&repo.usuarioCierreAnterior(p,id).isPresent();
        boolean reabrible=periodo.estado()==Estado.CERRADO&&(anterior||historia.stream().anyMatch(c->c.reapertura()==null))
            &&repo.posterioresCerrados(p,periodo.hasta())==0;
        return new Detalle(periodo,evaluacion,historia,reabrible);
    }

    @PreAuthorize(VER)
    public Evaluacion validacion(UUID id) {
        var p=TenantContext.principalActual();
        return evaluar(p,periodo(p,id),moneda(p));
    }

    @PreAuthorize(CONFIGURAR)
    public List<Cuenta> buscarResultado(String buscar,int offset,int limite) {
        return repo.buscarResultado(TenantContext.principalActual(),limpiar(buscar,150),Math.max(0,offset),Math.clamp(limite,1,100));
    }

    @PreAuthorize(CONFIGURAR)
    @Transactional
    public void configurarResultado(UUID cuenta) {
        var p=TenantContext.principalActual();
        cuentas.bloquearEmpresa(p);
        if(cuenta==null||!repo.configurarCuenta(p,cuenta))
            throw new ReglaNegocioException("Selecciona una cuenta de movimiento activa habilitada para el resultado del ejercicio.");
        auditoria.registrar("CONFIGURAR_RESULTADO_EJERCICIO","CuentaContable",cuenta,
            JSON.writeValueAsString(java.util.Map.of("traceId",TraceContext.currentOrCreate())));
    }

    @PreAuthorize(VER+" and hasAuthority('cierres_contables.ejecutar')")
    @Transactional
    public Cierre cerrar(UUID id,UUID clave,String observacion) {
        var p=TenantContext.principalActual();
        exigirClave(clave);
        // Orden compartido con la contabilización: empresa, período, asiento y secuencia.
        cuentas.bloquearEmpresa(p);
        registrar(p,id,null,"AccountingPeriodCloseRequested");
        try {
            var periodo=periodo(p,id);
            var existente=repo.solicitud(p,clave,false);
            if(existente.isPresent()) return idempotente(p,id,null,existente.get());
            if(periodo.estado()!=Estado.ABIERTO) throw new ReglaNegocioException("El período contable ya no está abierto.");
            repo.bloquearPeriodo(p,id);
            var moneda=moneda(p);
            var e=evaluar(p,periodo,moneda);
            if(!e.permiteCerrar()) throw new ReglaNegocioException(e.validaciones().stream().filter(v->!v.correcta())
                .map(Validacion::detalle).findFirst().orElseThrow());
            var resumen=resumen(p,periodo,e,p.nombre(),limpiar(observacion,1000));
            UUID cierreId=UUID.randomUUID();
            UUID asiento=e.tipo()==Tipo.EJERCICIO?asientoAnual(p,periodo,moneda,e):null;
            repo.registrar(p,cierreId,id,clave,asiento,resumen);
            repo.estado(p,id,true);
            auditar(p,id,cierreId,"CERRAR_PERIODO",asiento);
            return cierre(p,cierreId);
        } catch(RuntimeException ex) {
            registrar(p,id,null,"AccountingPeriodCloseFailed");
            throw ex;
        }
    }

    @PreAuthorize(VER+" and hasAuthority('cierres_contables.reabrir')")
    @Transactional
    public Cierre reabrir(UUID id,UUID cierreId,UUID clave,String motivo) {
        var p=TenantContext.principalActual();
        exigirClave(clave);
        cuentas.bloquearEmpresa(p);
        registrar(p,id,cierreId,"AccountingPeriodReopenRequested");
        try {
            var periodo=periodo(p,id);
            var existente=repo.solicitud(p,clave,true);
            if(existente.isPresent()) return idempotente(p,id,cierreId,existente.get());
            String razon=limpiar(motivo,1000);
            if(razon.isEmpty()) throw new ReglaNegocioException("Indica el motivo de la reapertura.");
            if(periodo.estado()!=Estado.CERRADO)
                throw new ReglaNegocioException("El cierre seleccionado ya no está vigente.");
            if(repo.posterioresCerrados(p,periodo.hasta())>0)
                throw new ReglaNegocioException("Reabre primero los períodos posteriores cerrados.");
            repo.bloquearPeriodo(p,id);
            var c=cierreId==null?registrarAnterior(p,periodo):idempotente(p,id,cierreId,cierreId);
            if(c.reapertura()!=null) throw new ReglaNegocioException("El cierre seleccionado ya no está vigente.");
            UUID reversa=c.asientoId()==null?null:asientos.revertirCierre(p,c.asientoId(),c.resumen().hasta(),razon,UUID.randomUUID());
            repo.reapertura(p,c.id(),clave,razon,TraceContext.currentOrCreate(),reversa);
            repo.estado(p,id,false);
            auditar(p,id,c.id(),"REABRIR_PERIODO",reversa);
            return cierre(p,c.id());
        } catch(RuntimeException ex) {
            registrar(p,id,cierreId,"AccountingPeriodReopenFailed");
            throw ex;
        }
    }

    @PreAuthorize(VER+" and hasAuthority('cierres_contables.pdf')")
    public Cierre reporte(UUID periodoId,UUID cierreId) {
        var p=TenantContext.principalActual();
        if(cierreId!=null) return idempotente(p,periodoId,null,cierreId);
        periodo(p,periodoId);
        return repo.historial(p,periodoId).stream().findFirst()
            .orElseThrow(()->new RecursoNoEncontradoException("El período no tiene un cierre registrado."));
    }

    private Evaluacion evaluar(TenantPrincipal p,Periodo periodo,Moneda moneda) {
        var fin=YearMonth.from(periodo.hasta());
        var inicio=LocalDate.of(fin.getYear(),repo.mesInicio(p),1);
        if(inicio.isAfter(periodo.hasta())) inicio=inicio.minusYears(1);
        boolean anual=fin.equals(YearMonth.from(inicio.plusMonths(11)));
        var desde=anual?inicio:periodo.desde();
        var codigo=moneda==null?"":moneda.codigo();
        var cuadre=repo.cuadre(p,desde,periodo.hasta(),codigo);
        var configurada=repo.cuentaResultado(p).orElse(null);
        var cuenta=configurada==null?null:configurada.cuenta();
        var estadoCuenta=configurada==null?EstadoCuentaResultado.NO_CONFIGURADA
            :configurada.valida()?EstadoCuentaResultado.VALIDA:EstadoCuentaResultado.INVALIDA;
        var anteriores=repo.anteriores(p,inicio,periodo.desde());
        var v=new ArrayList<Validacion>();
        v.add(new Validacion("Estado del período",periodo.estado()==Estado.ABIERTO,
            periodo.estado()==Estado.ABIERTO?"Abierto":"El período no está abierto.",periodo.estado()==Estado.ABIERTO?"Abierto":"No abierto"));
        v.add(conteo("Asientos descuadrados",cuadre.descuadrados(),"Hay asientos descuadrados: "));
        v.add(conteo("Asientos en borrador",cuadre.borradores(),"Hay borradores pendientes: "));
        v.add(new Validacion("Balanza de comprobación",cuadre.balanza(),
            cuadre.balanza()?"Cuadrada":"La Balanza de Comprobación presenta diferencias.",cuadre.balanza()?"Cuadrada":"Descuadrada"));
        v.add(validarAnteriores(anteriores));
        v.add(new Validacion("Moneda base",moneda!=null&&cuadre.monedaValida(),
            moneda!=null&&cuadre.monedaValida()?codigo:"Configura la moneda base y revisa la moneda de los asientos del período.",
            moneda!=null&&cuadre.monedaValida()?codigo:"Requiere revisión"));
        if(anual) v.add(switch(estadoCuenta) {
            case NO_CONFIGURADA -> new Validacion("Cuenta de resultado",false,"Configura una cuenta de resultado del ejercicio.","No configurada");
            case INVALIDA -> new Validacion("Cuenta de resultado",false,"Configura una cuenta válida para el resultado del ejercicio.","Configuración inválida");
            case VALIDA -> new Validacion("Cuenta de resultado",true,cuenta.etiqueta(),cuenta.etiqueta());
        });
        return new Evaluacion(anual?Tipo.EJERCICIO:Tipo.PERIODO,anual?inicio:null,codigo,cuenta,
            repo.resultado(p,desde,periodo.hasta(),codigo),List.copyOf(v),inicio,
            repo.resultado(p,anual?periodo.desde():inicio,periodo.hasta(),codigo),anteriores,estadoCuenta);
    }

    static Validacion validarAnteriores(PeriodosAnteriores anteriores) {
        long pendientes=anteriores.pendientes();
        if(pendientes>0) return new Validacion("Períodos anteriores",false,
            "Completa y cierra todos los períodos anteriores del ejercicio.",
            pendientes==1?"1 pendiente":pendientes+" pendientes");
        String valor=anteriores.existentes()==0?"0 pendientes":"Todos cerrados";
        return new Validacion("Períodos anteriores",true,"No existen períodos anteriores pendientes.",valor);
    }

    private UUID asientoAnual(TenantPrincipal p,Periodo periodo,Moneda moneda,Evaluacion e) {
        var movimientos=new ArrayList<>(repo.cancelaciones(p,e.inicioEjercicio(),periodo.hasta(),moneda.codigo()));
        if(movimientos.isEmpty()) return null;
        var neto=e.resultado().neto();
        if(neto.signum()!=0) movimientos.add(new Movimiento(e.cuentaResultado().id(),"Resultado del ejercicio",
            neto.negate().max(BigDecimal.ZERO),neto.max(BigDecimal.ZERO)));
        var asiento=asientos.registrarCierre(p,periodo.hasta(),moneda,movimientos,UUID.randomUUID());
        registrar(p,periodo.id(),null,"AccountingYearCloseGenerated");
        return asiento;
    }

    private Moneda moneda(TenantPrincipal p) { return asientosRepo.monedaBase(p).orElse(null); }

    private Cierre registrarAnterior(TenantPrincipal p,Periodo periodo) {
        if(!repo.historial(p,periodo.id()).isEmpty())
            throw new ReglaNegocioException("Selecciona el cierre vigente para reabrir el período.");
        var usuario=repo.usuarioCierreAnterior(p,periodo.id())
            .orElseThrow(()->new ReglaNegocioException("El período no tiene fecha y usuario de cierre registrados."));
        var moneda=moneda(p);
        var codigo=moneda==null?"":moneda.codigo();
        var e=new Evaluacion(Tipo.PERIODO,null,codigo,null,repo.resultado(p,periodo.desde(),periodo.hasta(),codigo),List.of());
        UUID id=UUID.randomUUID();
        var resumen=resumen(p,periodo,e,usuario,"Cierre previo al historial de cierres. Resultado reconstruido al reabrir a partir del Libro Diario.");
        repo.registrarCierreAnterior(p,id,periodo.id(),resumen);
        return cierre(p,id);
    }

    private Resumen resumen(TenantPrincipal p,Periodo periodo,Evaluacion e,String usuario,String observacion) {
        var empresa=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId())
            .orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
        var d=datos.findByEmpresaId(p.empresaId()).orElse(null);
        var nombre=d==null?null:d.getNombreComercial();
        var emisor=new Empresa(nombre==null||nombre.isBlank()?empresa.getNombre():nombre,empresa.getIdentificacionFiscal(),
            d==null?null:d.getDireccion(),d==null?null:d.getTelefono(),empresa.getPaisCodigo(),empresa.getLogoObjectKey());
        return new Resumen(periodo.nombre(),periodo.desde(),periodo.hasta(),e,usuario,observacion,emisor,empresa.getZonaHoraria());
    }

    private static Validacion conteo(String nombre,long cantidad,String aviso) {
        String valor=Long.toString(cantidad);
        return new Validacion(nombre,cantidad==0,cantidad==0?valor:aviso+cantidad+".",valor);
    }

    private Periodo periodo(TenantPrincipal p,UUID id) {
        return repo.periodo(p,id).orElseThrow(()->new RecursoNoEncontradoException("Período contable no encontrado."));
    }

    private Cierre cierre(TenantPrincipal p,UUID id) {
        return repo.cierre(p,id).orElseThrow(()->new RecursoNoEncontradoException("Cierre contable no encontrado."));
    }

    private Cierre idempotente(TenantPrincipal p,UUID periodo,UUID solicitado,UUID existente) {
        var c=cierre(p,existente);
        if(!c.periodoId().equals(periodo)||solicitado!=null&&!solicitado.equals(existente))
            throw new ReglaNegocioException("La solicitud no corresponde al cierre seleccionado.");
        return c;
    }

    private static String limpiar(String valor,int limite) {
        String texto=valor==null?"":valor.strip();
        if(texto.length()>limite) throw new ReglaNegocioException("Un campo supera "+limite+" caracteres.");
        return texto;
    }

    private static void exigirClave(UUID clave) {
        if(clave==null) throw new ReglaNegocioException("Falta la clave de la solicitud.");
    }

    private void auditar(TenantPrincipal p,UUID periodo,UUID cierre,String accion,UUID asiento) {
        var info=new java.util.LinkedHashMap<String,Object>();
        info.put("periodoId",periodo);
        info.put("asientoId",asiento);
        info.put("traceId",TraceContext.currentOrCreate());
        auditoria.registrar(accion,"CierreContable",cierre,JSON.writeValueAsString(info));
        registrar(p,periodo,cierre,accion.equals("CERRAR_PERIODO")?"AccountingPeriodClosed":"AccountingPeriodReopened");
    }

    private static void registrar(TenantPrincipal p,UUID periodo,UUID cierre,String evento) {
        LOG.atInfo().addKeyValue("TraceId",TraceContext.currentOrCreate()).addKeyValue("TenantId",p.tenantId())
            .addKeyValue("CompanyId",p.empresaId()).addKeyValue("UserId",p.usuarioId())
            .addKeyValue("PeriodId",periodo).addKeyValue("CloseId",cierre).log(evento);
    }
}
