package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class NotaCreditoVentaService {
    @org.springframework.beans.factory.annotation.Autowired private LocalFileStorageService storage;
    private static final String OP="SALES_CREDIT_NOTE_ISSUE";
    private final FacturaVentaRepository facturas;private final LineaFacturaVentaRepository lineas;
    private final MotivoDevolucionVentaRepository motivos;private final NotaCreditoVentaConsultaRepository consulta;
    private final EmpresaRepository empresas;private final DatosEmpresaRepository datos;private final PeriodoFiscalRepository periodos;
    private final IdempotentOperationService idempotencia;private final AuditoriaService auditoria;
    public NotaCreditoVentaService(FacturaVentaRepository facturas,LineaFacturaVentaRepository lineas,MotivoDevolucionVentaRepository motivos,
            NotaCreditoVentaConsultaRepository consulta,EmpresaRepository empresas,DatosEmpresaRepository datos,PeriodoFiscalRepository periodos,
            IdempotentOperationService idempotencia,AuditoriaService auditoria){
        this.facturas=facturas;this.lineas=lineas;this.motivos=motivos;this.consulta=consulta;this.empresas=empresas;this.datos=datos;this.periodos=periodos;this.idempotencia=idempotencia;this.auditoria=auditoria;
    }
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.ver')")
    public Page<NotaCreditoVentaConsultaRepository.Resumen> buscar(String texto,String cliente,LocalDate desde,LocalDate hasta,int page,int size){
        pagina(page,size);if(desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new ReglaNegocioException("El rango de fechas no es válido.");return consulta.buscar(limpiar(texto),limpiar(cliente),desde,hasta,page,size);
    }
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.ver')")
    public Page<NotaCreditoVentaConsultaRepository.Resumen> buscar(String texto,String cliente,LocalDate desde,LocalDate hasta,int page,int size,String estado){
        pagina(page,size);if(!Set.of("","CONFIRMED","VOIDED").contains(estado)||desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new ReglaNegocioException("Filtro inválido.");return consulta.buscar(limpiar(texto),limpiar(cliente),desde,hasta,page,size,estado);
    }
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.crear')")
    public Page<NotaCreditoVentaConsultaRepository.FacturaOpcion> facturas(String texto,int page,int size){pagina(page,size);return consulta.facturas(limpiar(texto),page,size);}
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.crear')")
    public PreparacionNotaCreditoVentaDto preparar(UUID id){
        var f=factura(id,false);var p=TenantContext.principalActual();var consumos=consulta.consumidos(id);
        var ls=lineas.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(id,p.tenantId(),p.empresaId()).stream().map(l->{var usado=consumos.getOrDefault(l.getId(),sinConsumo()).cantidad();return new PreparacionNotaCreditoVentaDto.Linea(l.getId(),l.getDescripcion(),l.getCantidad(),usado,l.getCantidad().subtract(usado).max(BigDecimal.ZERO));}).toList();
        BigDecimal usado=consulta.totalAcreditado(id);
        return new PreparacionNotaCreditoVentaDto(id,f.getNumero(),f.getFecha(),nombreCliente(f.getClienteNombre()),f.getClienteIdentificacion(),f.getMonedaCodigo(),f.getTotal(),usado,f.getTotal().subtract(usado).max(BigDecimal.ZERO),ls,
            motivos.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),p.empresaId()).stream().map(m->new PreparacionNotaCreditoVentaDto.Motivo(m.getId(),m.getNombre())).toList(),f.getCondicionPago());
    }
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.crear')")
    public NotaCreditoVentaDocumento calcular(EmitirNotaCreditoVentaInput input){return construir(input,factura(requerir(input),false),false);}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.crear') and hasAuthority('notas_credito.emitir')")
    public NotaCreditoVentaDocumento emitir(EmitirNotaCreditoVentaInput input){
        requerir(input);var p=TenantContext.principalActual();if(input.idempotencia()==null)throw new ReglaNegocioException("No fue posible identificar la solicitud de emisión.");
        var intent=idempotencia.begin(p.tenantId(),p.empresaId(),OP,input.idempotencia(),CommandFingerprint.sha256(input));
        if(intent.replay())return consulta.documento(intent.resultId());
        FacturaVenta f=factura(input.facturaId(),true);var doc=construir(input,f,true);
        consulta.insertar(doc,f.getId(),f.getMonedaId());
        auditoria.registrar("SALES_CREDIT_NOTE_ISSUED","NotaCreditoVenta",doc.id(),"{\"numero\":\""+doc.numero()+"\",\"facturaId\":\""+f.getId()+"\",\"sucursalId\":\""+f.getSucursalId()+"\",\"total\":"+doc.total()+",\"traceId\":\""+com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate()+"\"}");
        idempotencia.complete(p.tenantId(),p.empresaId(),OP,input.idempotencia(),"SALES_CREDIT_NOTE",doc.id());return doc;
    }
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.ver')")
    public NotaCreditoVentaDocumento obtener(UUID id){return consulta.documento(id);}

    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.crear')")
    public LocalDate fechaActual(){var p=TenantContext.principalActual();var e=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));return LocalDate.now(ZoneId.of(e.getZonaHoraria()));}

    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.ver')")
    public NotaCreditoVentaCicloDto ciclo(UUID id){
        var c=consulta.ciclo(id,false);if(!c.anulable())return c;var d=consulta.documento(id);var p=TenantContext.principalActual();var company=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
        boolean eligible=d.ncf()==null||d.ncf().isBlank();
        for(var date:new HashSet<>(List.of(d.fecha(),LocalDate.now(ZoneId.of(company.getZonaHoraria())))))if(periodos.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(p.tenantId(),p.empresaId(),date,date).filter(x->x.getEstado()!=PeriodoFiscal.Estado.ABIERTO).isPresent())eligible=false;
        return new NotaCreditoVentaCicloDto(c.facturaId(),c.devolucionId(),c.estadoOrigen(),c.version(),c.estado(),eligible,c.motivo(),c.anuladaPor(),c.anuladaEn());
    }

    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_credito.anular')")
    public NotaCreditoVentaDocumento anular(AnularNotaCreditoVentaInput input){
        if(input==null||input.notaId()==null||input.idempotencia()==null||input.version()<0)throw new ReglaNegocioException("No fue posible identificar la solicitud de anulación.");
        String reason=limpiar(input.motivo());if(reason.length()<3||reason.length()>1000)throw new ReglaNegocioException("Indique un motivo de anulación de 3 a 1,000 caracteres.");
        var p=TenantContext.principalActual();var origin=consulta.ciclo(input.notaId(),false);
        var intent=idempotencia.begin(p.tenantId(),p.empresaId(),"SALES_CREDIT_NOTE_VOID",input.idempotencia(),CommandFingerprint.sha256(input));
        if(intent.replay())return consulta.documento(intent.resultId());
        var invoice=facturas.bloquear(origin.facturaId(),p.tenantId(),p.empresaId()).filter(f->EmpresaContext.permiteSucursal(f.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));
        var state=consulta.ciclo(input.notaId(),true);
        if(!"CONFIRMED".equals(state.estado())||state.version()!=input.version())throw new ReglaNegocioException("La nota cambió o ya fue anulada. Actualice la consulta.");
        if(!state.anulable())throw new ReglaNegocioException("La nota no puede anularse en su estado financiero actual. Revise sus aplicaciones y documentos relacionados.");
        var document=consulta.documento(input.notaId());
        if(document.ncf()!=null&&!document.ncf().isBlank())throw new ReglaNegocioException("Esta nota requiere el procedimiento fiscal de anulación de su comprobante.");
        var company=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
        for(var date:new HashSet<>(List.of(document.fecha(),LocalDate.now(ZoneId.of(company.getZonaHoraria())))))periodos.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(p.tenantId(),p.empresaId(),date,date).filter(x->x.getEstado()!=PeriodoFiscal.Estado.ABIERTO).ifPresent(x->{throw new ReglaNegocioException("El período fiscal está cerrado o bloqueado.");});
        consulta.revertir(document,invoice.getId());consulta.marcarAnulada(document.id(),input.version(),reason,input.idempotencia(),company.getZonaHoraria());
        var audit=new LinkedHashMap<String,Object>();audit.put("CreditNoteId",document.id());audit.put("CreditNoteNumber",document.numero());audit.put("InvoiceId",invoice.getId());audit.put("OriginType",document.origen());audit.put("OriginDocumentId",state.devolucionId());audit.put("TenantId",p.tenantId());audit.put("CompanyId",p.empresaId());audit.put("UserId",p.usuarioId());audit.put("Reason",reason);audit.put("PreviousStatus","CONFIRMED");audit.put("NewStatus","VOIDED");audit.put("TraceId",com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());audit.put("Timestamp",java.time.Instant.now().toString());
        auditoria.registrar("SALES_CREDIT_NOTE_VOIDED","NotaCreditoVenta",document.id(),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(audit));
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){org.slf4j.LoggerFactory.getLogger(NotaCreditoVentaService.class).atInfo().addKeyValue("CreditNoteId",document.id()).addKeyValue("InvoiceId",invoice.getId()).addKeyValue("TenantId",p.tenantId()).addKeyValue("CompanyId",p.empresaId()).addKeyValue("UserId",p.usuarioId()).addKeyValue("TraceId",audit.get("TraceId")).log("sales_credit_note_voided");}});
        idempotencia.complete(p.tenantId(),p.empresaId(),"SALES_CREDIT_NOTE_VOID",input.idempotencia(),"SALES_CREDIT_NOTE",document.id());return consulta.documento(document.id());
    }
    private NotaCreditoVentaDocumento construir(EmitirNotaCreditoVentaInput input,FacturaVenta f,boolean emitir){
        var p=TenantContext.principalActual();Empresa e=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(()->new RecursoNoEncontradoException("Empresa no encontrada."));
        ZoneId zona=ZoneId.of(e.getZonaHoraria());LocalDate hoy=LocalDate.now(zona);
        if(input.fecha()==null||input.fecha().isBefore(f.getFecha())||input.fecha().isAfter(hoy))throw new ReglaNegocioException("La fecha debe estar entre la fecha de factura y la fecha actual de la empresa.");
        periodos.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(p.tenantId(),p.empresaId(),input.fecha(),input.fecha())
            .filter(x->x.getEstado()!=PeriodoFiscal.Estado.ABIERTO).ifPresent(x->{throw new ReglaNegocioException("El período fiscal de la nota está cerrado o bloqueado.");});
        var motivo=Optional.ofNullable(input.motivoId()).flatMap(id->motivos.findByIdAndTenantIdAndEmpresaIdAndActivoTrue(id,p.tenantId(),p.empresaId())).orElseThrow(()->new ReglaNegocioException("Seleccione un motivo disponible."));
        String descripcion=texto(input.descripcion(),1000,true),observacion=texto(input.observacion(),1000,false);
        List<LineaFacturaVenta> originales=emitir?lineas.bloquearPorFactura(f.getId(),p.tenantId(),p.empresaId()):lineas.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(f.getId(),p.tenantId(),p.empresaId());
        Map<UUID,LineaFacturaVenta> mapa=new HashMap<>();originales.forEach(l->mapa.put(l.getId(),l));Set<UUID> vistas=new HashSet<>();List<NotaCreditoVentaDocumento.Linea> ls=new ArrayList<>();var consumos=consulta.consumidos(f.getId());
        if(input.lineas().size()>500)throw new ReglaNegocioException("La solicitud excede 500 líneas.");
        for(var seleccion:input.lineas()){
            if(seleccion==null||seleccion.lineaFacturaId()==null||seleccion.cantidad()==null||seleccion.cantidad().signum()<0||seleccion.cantidad().scale()>4||seleccion.cantidad().precision()-seleccion.cantidad().scale()>15)throw new ReglaNegocioException("La cantidad indicada no es válida.");
            if(!vistas.add(seleccion.lineaFacturaId()))throw new ReglaNegocioException("La solicitud contiene líneas repetidas.");
            var original=mapa.get(seleccion.lineaFacturaId());if(original==null)throw new RecursoNoEncontradoException("Una línea no pertenece a la factura.");
            if(seleccion.cantidad().signum()==0)continue;
            ls.add(calcularLinea(original,seleccion.cantidad(),consumos.getOrDefault(original.getId(),sinConsumo())));
        }
        if(ls.isEmpty())throw new ReglaNegocioException("Seleccione al menos una cantidad para acreditar.");
        BigDecimal bruto=suma(ls,NotaCreditoVentaDocumento.Linea::bruto),discount=suma(ls,NotaCreditoVentaDocumento.Linea::descuento),surcharge=suma(ls,NotaCreditoVentaDocumento.Linea::recargo),tax=suma(ls,NotaCreditoVentaDocumento.Linea::impuesto),total=suma(ls,NotaCreditoVentaDocumento.Linea::total);
        if(total.signum()<=0||total.compareTo(f.getTotal().subtract(consulta.totalAcreditado(f.getId())))>0)throw new ReglaNegocioException("El importe supera el disponible de la factura.");
        var financiero=emitir?consulta.aplicar(f.getId(),f.getClienteId(),f.getMonedaId(),total):new NotaCreditoVentaConsultaRepository.Aplicacion(null,BigDecimal.ZERO,null,BigDecimal.ZERO);
        DatosEmpresa de=datos.findByEmpresaId(p.empresaId()).orElse(null);
        return new NotaCreditoVentaDocumento(emitir?UUID.randomUUID():null,emitir?consulta.siguiente():"VISTA PREVIA",input.fecha(),LocalDateTime.now(zona),p.nombre(),"FINANCIAL",null,"CONFIRMED",f.getSucursalId(),f.getClienteId(),nombreCliente(f.getClienteNombre()),f.getClienteIdentificacion(),null,
            de==null||limpiar(de.getNombreComercial()).isBlank()?e.getNombre():de.getNombreComercial(),e.getIdentificacionFiscal(),de==null?null:de.getDireccion(),de==null?null:de.getTelefono(),f.getNumero(),f.getFecha(),null,null,f.getCondicionPago(),f.getMonedaCodigo(),motivo.getNombre(),descripcion,observacion,ls,bruto,discount,surcharge,tax,total,financiero.anterior(),financiero.aplicado(),financiero.posterior(),financiero.favor(),e.getNombre(),de==null?null:de.getCorreo(),emitir&&storage!=null?CompanyReportAssets.logo(e,storage):null);
    }
    public static NotaCreditoVentaDocumento.Linea calcularLinea(LineaFacturaVenta l,BigDecimal qty,NotaCreditoVentaConsultaRepository.Consumido usado){
        BigDecimal disponible=l.getCantidad().subtract(usado.cantidad());if(qty.signum()<=0||qty.compareTo(disponible)>0)throw new ReglaNegocioException("La cantidad disponible cambió. Revise las notas y devoluciones anteriores.");
        boolean ultima=qty.compareTo(disponible)==0;
        BigDecimal bruto=porcion(l.getSubtotalBruto(),usado.bruto(),qty,l.getCantidad(),ultima),discount=porcion(l.getDescuento(),usado.descuento(),qty,l.getCantidad(),ultima),surcharge=porcion(l.getRecargo(),usado.recargo(),qty,l.getCantidad(),ultima),tax=porcion(l.getImpuesto(),usado.impuesto(),qty,l.getCantidad(),ultima);
        BigDecimal total=bruto.subtract(discount).add(surcharge).add(tax);
        if(total.compareTo(l.getTotal().subtract(usado.total()))>0)throw new ReglaNegocioException("El crédito excede el importe histórico restante de la línea.");
        return new NotaCreditoVentaDocumento.Linea(l.getId(),l.getCodigoProducto(),l.getDescripcion(),l.getUnidadNombre(),qty,l.getPrecioUnitario(),bruto,discount,surcharge,l.getImpuestoNombre(),l.getTasaImpuesto(),tax,total);
    }
    private static NotaCreditoVentaConsultaRepository.Consumido sinConsumo(){return new NotaCreditoVentaConsultaRepository.Consumido(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);}
    private static BigDecimal porcion(BigDecimal original,BigDecimal usado,BigDecimal qty,BigDecimal originalQty,boolean ultima){BigDecimal remaining=original.subtract(usado);if(remaining.signum()<0)throw new ReglaNegocioException("Los créditos históricos de la línea requieren revisión.");return ultima?remaining:original.multiply(qty).divide(originalQty,4,RoundingMode.HALF_UP).min(remaining);}
    private FacturaVenta factura(UUID id,boolean lock){var p=TenantContext.principalActual();var f=(lock?facturas.bloquear(id,p.tenantId(),p.empresaId()):facturas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())).filter(x->EmpresaContext.permiteSucursal(x.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));
        if(f.getStatus()!=EstadoFacturaVenta.CONFIRMED)throw new ReglaNegocioException("La factura no está confirmada.");
        return f;
    }
    public static String nombreCliente(String value){return value==null||value.isBlank()?"Consumidor Final":value.trim();}
    private static UUID requerir(EmitirNotaCreditoVentaInput i){if(i==null||i.facturaId()==null)throw new ReglaNegocioException("Seleccione la factura de origen.");return i.facturaId();}
    private static void pagina(int p,int z){if(p<0||!Set.of(5,10,20,25,50).contains(z))throw new ReglaNegocioException("Paginación inválida.");}
    private static String limpiar(String x){return x==null?"":x.trim();}
    private static String texto(String x,int max,boolean required){String v=limpiar(x);if(v.length()>max||required&&v.isBlank())throw new ReglaNegocioException("Complete la descripción; el texto admite hasta "+max+" caracteres.");return v.isBlank()?null:v;}
    private static BigDecimal suma(List<NotaCreditoVentaDocumento.Linea> ls,java.util.function.Function<NotaCreditoVentaDocumento.Linea,BigDecimal> fn){return ls.stream().map(fn).reduce(BigDecimal.ZERO,BigDecimal::add);}
}
