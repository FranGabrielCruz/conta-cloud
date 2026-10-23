package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.ErrorCode;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class DevolucionVentaService {
    public static final int TAMANO_PAGINA=5;
    private static final UUID SUCURSAL_MARCADOR=new UUID(0L,0L);
    private static final String OPERATION="SALES_RETURN_CONFIRM";
    private final FacturaVentaRepository facturas; private final LineaFacturaVentaRepository lineasFactura;
    private final DevolucionVentaRepository devoluciones; private final LineaDevolucionVentaRepository lineasDevolucion;
    private final MotivoDevolucionVentaRepository motivos; private final AlmacenRepository almacenes;
    private final NotaCreditoVentaRepository notas; private final CreditoClienteRepository creditos;
    private final InventoryMovementService inventario; private final IdempotentOperationService idempotencia;
    private final AuditoriaService auditoria; private final JdbcTemplate jdbc; private final MeterRegistry metricas;

    public DevolucionVentaService(FacturaVentaRepository facturas,LineaFacturaVentaRepository lineasFactura,
            DevolucionVentaRepository devoluciones,LineaDevolucionVentaRepository lineasDevolucion,
            MotivoDevolucionVentaRepository motivos,AlmacenRepository almacenes,NotaCreditoVentaRepository notas,
            CreditoClienteRepository creditos,InventoryMovementService inventario,IdempotentOperationService idempotencia,
            AuditoriaService auditoria,JdbcTemplate jdbc,MeterRegistry metricas){
        this.facturas=facturas;this.lineasFactura=lineasFactura;this.devoluciones=devoluciones;this.lineasDevolucion=lineasDevolucion;
        this.motivos=motivos;this.almacenes=almacenes;this.notas=notas;this.creditos=creditos;this.inventario=inventario;
        this.idempotencia=idempotencia;this.auditoria=auditoria;this.jdbc=jdbc;this.metricas=metricas;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('devoluciones_venta.ver')")
    public Page<FacturaDevolucionResumenDto> buscar(String criterio,LocalDate desde,LocalDate hasta,
            EstadoDevolucionFacturaFiltro estado,int pagina){
        if(pagina<0)throw new ReglaNegocioException(ErrorCode.VALIDATION_ERROR,"La página solicitada no es válida.");
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta))throw new ReglaNegocioException(ErrorCode.VALIDATION_ERROR,"La fecha inicial no puede ser posterior a la fecha final.");
        var p=TenantContext.principalActual();String texto=limpiar(criterio,180).toLowerCase(Locale.ROOT);String identificacion=texto.replaceAll("\\D","");
        Collection<UUID>sucursales=p.accesoTodasSucursales()?List.of(SUCURSAL_MARCADOR):p.sucursalIds();
        if(!p.accesoTodasSucursales()&&sucursales.isEmpty())return Page.empty(PageRequest.of(pagina,TAMANO_PAGINA));
        String filtro=estado==null?EstadoDevolucionFacturaFiltro.TODAS.name():estado.name();
        return facturas.buscarParaDevolucion(p.tenantId(),p.empresaId(),p.accesoTodasSucursales(),sucursales,texto,
                identificacion,desde,hasta,filtro,PageRequest.of(pagina,TAMANO_PAGINA)).map(f->new FacturaDevolucionResumenDto(
                f.getId(),f.getNumero(),f.getConfirmadaEn(),f.getClienteNombre(),f.getTotal(),f.getMonedaCodigo(),estadoDerivado(f)));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('devoluciones_venta.crear')")
    public DevolucionVentaDetalleDto preparar(UUID facturaId){
        var p=TenantContext.principalActual();FacturaVenta f=facturas.findByIdAndTenantIdAndEmpresaId(facturaId,p.tenantId(),p.empresaId())
                .orElseThrow(()->error(ErrorCode.RETURN_INVOICE_NOT_FOUND,"Factura no encontrada."));
        validarFactura(f);
        List<DevolucionVentaDetalleDto.Linea> detalle=lineasFactura.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(f.getId(),p.tenantId(),p.empresaId()).stream().map(l->{
            BigDecimal devuelta=cantidadDevuelta(p,l.getId());BigDecimal disponible=l.getCantidad().subtract(devuelta).max(BigDecimal.ZERO);
            return new DevolucionVentaDetalleDto.Linea(l.getId(),l.getProductoId(),l.getCodigoProducto(),l.getDescripcion(),l.getUnidadNombre(),
                    l.getCantidad(),devuelta,disponible,l.getPrecioUnitario(),l.getSubtotalBruto(),l.getDescuento(),l.getRecargo(),l.getImpuesto(),l.getTotal(),l.isControlaExistencia());
        }).toList();
        List<DevolucionVentaDetalleDto.Motivo> catalogo=motivos.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),p.empresaId()).stream()
                .map(m->new DevolucionVentaDetalleDto.Motivo(m.getId(),m.getCodigo(),m.getNombre(),m.isRequiereObservacion())).toList();
        List<DevolucionVentaDetalleDto.Almacen> destinos=almacenes.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),p.empresaId()).stream()
                .filter(a->!a.isAdministradoSistema()&&EmpresaContext.permiteSucursal(a.getSucursalId()))
                .map(a->new DevolucionVentaDetalleDto.Almacen(a.getId(),a.getNombre())).toList();
        return new DevolucionVentaDetalleDto(f.getId(),f.getNumero(),f.getConfirmadaEn(),f.getClienteNombre(),f.getClienteIdentificacion(),
                f.getTotal(),estadoDerivado(f),f.getMonedaCodigo(),f.getMonedaSimbolo(),f.getCondicionPago(),detalle,catalogo,destinos);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('devoluciones_venta.confirmar')")
    public DevolucionVentaResultadoDto confirmar(ConfirmarDevolucionVentaInput input){
        if(input==null||input.claveOperacion()==null||input.facturaId()==null)throw error(ErrorCode.VALIDATION_ERROR,"No fue posible identificar la devolución.");
        var p=TenantContext.principalActual();String huella=CommandFingerprint.sha256(input);
        var inicio=idempotencia.begin(p.tenantId(),p.empresaId(),OPERATION,input.claveOperacion(),huella);
        if(inicio.replay())return resultadoExistente(inicio.resultId(),p);
        FacturaVenta factura=facturas.bloquear(input.facturaId(),p.tenantId(),p.empresaId())
                .orElseThrow(()->error(ErrorCode.RETURN_INVOICE_NOT_FOUND,"Factura no encontrada."));
        validarFactura(factura);
        MotivoDevolucionVenta motivo=Optional.ofNullable(input.motivoId()).flatMap(id->motivos.findByIdAndTenantIdAndEmpresaIdAndActivoTrue(id,p.tenantId(),p.empresaId()))
                .orElseThrow(()->error(ErrorCode.RETURN_REASON_REQUIRED,"Seleccione un motivo de devolución."));
        String observacion=limpiar(input.observacion(),1000);if(motivo.isRequiereObservacion()&&observacion.isBlank())
            throw error(ErrorCode.RETURN_REASON_REQUIRED,"La observación es obligatoria para el motivo seleccionado.");
        Almacen almacen=null;if(input.retornaInventario()){
            if(input.almacenDestinoId()==null)throw error(ErrorCode.RETURN_WAREHOUSE_REQUIRED,"Seleccione el almacén destino.");
            almacen=almacenes.findByIdAndTenantIdAndEmpresaId(input.almacenDestinoId(),p.tenantId(),p.empresaId())
                    .filter(a->a.isActivo()&&!a.isAdministradoSistema()&&EmpresaContext.permiteSucursal(a.getSucursalId()))
                    .orElseThrow(()->error(ErrorCode.RETURN_WAREHOUSE_INVALID,"El almacén seleccionado no está disponible."));
        }else if(input.almacenDestinoId()!=null)throw error(ErrorCode.RETURN_WAREHOUSE_INVALID,"No indique un almacén cuando los productos no retornan al inventario.");
        Map<UUID,BigDecimal>solicitadas=new HashMap<>();for(var l:input.lineas()){
            if(l==null||l.lineaFacturaId()==null||l.cantidad()==null||l.cantidad().signum()<=0)continue;
            if(solicitadas.put(l.lineaFacturaId(),escala(l.cantidad()))!=null)throw error(ErrorCode.VALIDATION_ERROR,"La devolución contiene una línea duplicada.");
        }
        if(solicitadas.isEmpty())throw error(ErrorCode.RETURN_NOTHING_SELECTED,"Seleccione al menos un producto para devolver.");
        List<LineaFacturaVenta>originales=lineasFactura.bloquearPorFactura(factura.getId(),p.tenantId(),p.empresaId());
        Map<UUID,LineaFacturaVenta>porId=new HashMap<>();originales.forEach(l->porId.put(l.getId(),l));
        List<Calculada>calculadas=new ArrayList<>();for(var entry:solicitadas.entrySet()){
            LineaFacturaVenta original=porId.get(entry.getKey());if(original==null)throw error(ErrorCode.RETURN_INVOICE_NOT_FOUND,"Una línea no pertenece a la factura seleccionada.");
            BigDecimal devuelta=cantidadDevuelta(p,original.getId());BigDecimal disponible=original.getCantidad().subtract(devuelta);
            if(entry.getValue().compareTo(disponible)>0){metricas.counter("contacloud.sales.return","result","concurrency_conflict").increment();throw error(ErrorCode.RETURN_QUANTITY_EXCEEDED,"La cantidad disponible para devolver cambió porque otra operación fue procesada. Revise nuevamente la factura.");}
            calculadas.add(calcular(original,entry.getValue()));
        }
        calculadas.sort(Comparator.comparing(c->c.original.getId()));
        BigDecimal bruto=suma(calculadas,Calculada::bruto),descuento=suma(calculadas,Calculada::descuento),recargo=suma(calculadas,Calculada::recargo),impuesto=suma(calculadas,Calculada::impuesto),total=suma(calculadas,Calculada::total);
        if(total.signum()<=0)throw error(ErrorCode.RETURN_NOTHING_SELECTED,"El total de la devolución debe ser mayor que cero.");
        DevolucionVenta devolucion=devoluciones.saveAndFlush(new DevolucionVenta(p.tenantId(),p.empresaId(),siguiente("sales_return_sequence","DEV-"),factura,motivo,
                observacion.isBlank()?null:observacion,input.retornaInventario(),almacen==null?null:almacen.getId(),
                almacen==null?null:almacen.getNombre(),bruto,descuento,recargo,impuesto,total,input.claveOperacion(),p.usuarioId()));
        int orden=1;List<LineaDevolucionVenta>creadas=new ArrayList<>();for(Calculada c:calculadas){LineaDevolucionVenta linea=lineasDevolucion.save(new LineaDevolucionVenta(devolucion,c.original,c.cantidad,c.bruto,c.descuento,c.recargo,c.base,c.impuesto,c.total,orden++));creadas.add(linea);}lineasDevolucion.flush();
        NotaCreditoVenta nota=notas.saveAndFlush(new NotaCreditoVenta(p.tenantId(),p.empresaId(),siguiente("sales_credit_note_sequence","NCV-"),devolucion,total,p.usuarioId()));
        if(devolucion.getClienteId()!=null&&nota.getCreditoCliente().signum()>0)creditos.save(new CreditoCliente(p.tenantId(),p.empresaId(),devolucion.getClienteId(),devolucion.getMonedaId(),nota.getId(),nota.getCreditoCliente(),p.usuarioId()));
        if(input.retornaInventario())for(LineaDevolucionVenta linea:creadas)inventario.devolverVenta(devolucion,linea,almacen.getId(),p.usuarioId());
        auditoria.registrar("SALES_RETURN_CONFIRMED","DevolucionVenta",devolucion.getId(),"{\"numero\":\""+devolucion.getNumero()+
                "\",\"factura\":\""+factura.getNumero()+"\",\"notaCredito\":\""+nota.getNumero()+"\",\"motivo\":\""+motivo.getCodigo()+
                "\",\"retornaInventario\":"+input.retornaInventario()+",\"total\":"+total.toPlainString()+"}");
        idempotencia.complete(p.tenantId(),p.empresaId(),OPERATION,input.claveOperacion(),"SALES_RETURN",devolucion.getId());
        metricas.counter("contacloud.sales.return","result","confirmed").increment();
        return new DevolucionVentaResultadoDto(devolucion.getId(),devolucion.getNumero(),nota.getNumero(),devolucion.getTotal(),devolucion.getMonedaCodigo());
    }

    private DevolucionVentaResultadoDto resultadoExistente(UUID id,TenantPrincipal p){DevolucionVenta d=devoluciones.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
            .orElseThrow(()->error(ErrorCode.RETURN_ALREADY_COMPLETED,"La devolución ya fue procesada."));NotaCreditoVenta n=notas.findByDevolucionId(d.getId()).orElseThrow();return new DevolucionVentaResultadoDto(d.getId(),d.getNumero(),n.getNumero(),d.getTotal(),d.getMonedaCodigo());}
    private void validarFactura(FacturaVenta f){if(!EmpresaContext.permiteSucursal(f.getSucursalId()))throw error(ErrorCode.RETURN_INVOICE_NOT_FOUND,"Factura no encontrada.");if(f.getStatus()!=EstadoFacturaVenta.CONFIRMED)throw error(ErrorCode.RETURN_INVOICE_NOT_ELIGIBLE,"La factura no está disponible para devolución.");}
    private String estadoDerivado(FacturaVenta f){List<LineaFacturaVenta>ls=lineasFactura.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(f.getId(),f.getTenantId(),f.getEmpresaId());boolean alguna=false,todas=!ls.isEmpty();for(var l:ls){BigDecimal d=lineasDevolucion.cantidadDevuelta(f.getTenantId(),f.getEmpresaId(),l.getId());alguna|=d.signum()>0;todas&=d.compareTo(l.getCantidad())>=0;}return todas?"DEVUELTA":alguna?"PARCIALMENTE DEVUELTA":"PAGADA";}
    private BigDecimal cantidadDevuelta(TenantPrincipal p,UUID linea){BigDecimal v=lineasDevolucion.cantidadDevuelta(p.tenantId(),p.empresaId(),linea);return v==null?BigDecimal.ZERO:v;}
    static Calculada calcular(LineaFacturaVenta l,BigDecimal cantidad){BigDecimal ratio=cantidad.divide(l.getCantidad(),12,RoundingMode.HALF_UP);BigDecimal bruto=proporcion(l.getSubtotalBruto(),ratio),descuento=proporcion(l.getDescuento(),ratio),recargo=proporcion(l.getRecargo(),ratio),impuesto=proporcion(l.getImpuesto(),ratio),total=proporcion(l.getTotal(),ratio);return new Calculada(l,cantidad,bruto,descuento,recargo,bruto.subtract(descuento).add(recargo),impuesto,total);}
    private static BigDecimal proporcion(BigDecimal valor,BigDecimal ratio){return (valor==null?BigDecimal.ZERO:valor).multiply(ratio).setScale(4,RoundingMode.HALF_UP);}
    private static BigDecimal suma(List<Calculada>ls,java.util.function.Function<Calculada,BigDecimal>fn){return ls.stream().map(fn).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(4,RoundingMode.HALF_UP);}
    private String siguiente(String tabla,String prefijo){Long n=jdbc.queryForObject("INSERT INTO "+tabla+"(tenant_id,empresa_id,last_number) VALUES(?,?,1) ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET last_number="+tabla+".last_number+1 RETURNING last_number",Long.class,TenantContext.principalActual().tenantId(),TenantContext.principalActual().empresaId());return prefijo+String.format(Locale.ROOT,"%06d",n);}
    private static String limpiar(String valor,int max){String v=valor==null?"":valor.trim();return v.length()>max?v.substring(0,max):v;}
    private static BigDecimal escala(BigDecimal v){return v.setScale(4,RoundingMode.HALF_UP);}
    private static ReglaNegocioException error(ErrorCode code,String mensaje){return new ReglaNegocioException(code,mensaje);}
    record Calculada(LineaFacturaVenta original,BigDecimal cantidad,BigDecimal bruto,BigDecimal descuento,BigDecimal recargo,BigDecimal base,BigDecimal impuesto,BigDecimal total){}
}
