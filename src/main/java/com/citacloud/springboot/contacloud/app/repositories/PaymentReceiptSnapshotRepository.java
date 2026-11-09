package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.dto.CobroClienteDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument.*;
import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import com.citacloud.springboot.contacloud.app.services.RecursoNoEncontradoException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;

/** Historical printing data; never computes or modifies financial effects. */
@Repository
public class PaymentReceiptSnapshotRepository {
    private final JdbcTemplate jdbc;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public PaymentReceiptSnapshotRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Stored(UUID branchId,UUID cashRegisterId,String status,String json) {}
    public Stored obtener(UUID id,UUID tenant,UUID empresa){
        return jdbc.query("SELECT branch_id,pos_cash_register_id,status,pos_print_snapshot FROM customer_receipt WHERE id=? AND tenant_id=? AND empresa_id=?",
            rs->rs.next()?new Stored(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4)):null,id,tenant,empresa);
    }
    public PaymentReceiptPrintDocument documento(Stored s){
        if(s==null)throw new RecursoNoEncontradoException("Recibo no encontrado.");
        if(s.json()==null)throw new ReglaNegocioException("Este recibo anterior no conserva los datos históricos necesarios para el ticket POS.");
        try{return JSON.readValue(s.json(),PaymentReceiptPrintDocument.class);}
        catch(RuntimeException ex){throw new ReglaNegocioException("No fue posible leer los datos históricos del recibo.");}
    }
    public void conservar(CobroClienteDto d,UUID tenant,UUID empresa,UUID caja,BigDecimal anterior,BigDecimal pendiente){
        var methods=jdbc.query("SELECT method_name,amount,reference,method_type FROM customer_receipt_payment WHERE customer_receipt_id=? AND tenant_id=? AND empresa_id=? ORDER BY line_order",
            (rs,n)->new PaymentMethod(clean(rs.getString(1)),rs.getBigDecimal(2),safeReference(rs.getString(3),rs.getString(4))),d.id(),tenant,empresa);
        conservar(d,tenant,empresa,caja,anterior,pendiente,methods);
    }
    public void conservar(CobroClienteDto d,UUID tenant,UUID empresa,UUID caja,BigDecimal anterior,BigDecimal pendiente,List<PaymentMethod> methods){
        String cashName=null;
        if(caja!=null){
            cashName=jdbc.query("SELECT nombre FROM cajas WHERE tenant_id=? AND empresa_id=? AND sucursal_id=? AND id=?",rs->rs.next()?rs.getString(1):null,tenant,empresa,d.sucursalId(),caja);
            if(cashName==null)throw new RecursoNoEncontradoException("Caja no encontrada.");
        }
        var header=jdbc.query("""
            SELECT coalesce(nullif(de.nombre_comercial,''),e.nombre),e.identificacion_fiscal,de.direccion,de.telefono,
                   coalesce(e.pais_codigo,'XX'),e.zona_horaria,e.logo_object_key
              FROM empresas e LEFT JOIN datos_empresa de ON de.empresa_id=e.id
             WHERE e.id=? AND e.tenant_id=?
            """,rs->{if(!rs.next())throw new RecursoNoEncontradoException("Empresa no encontrada.");return new String[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)};},empresa,tenant);
        var doc=new PaymentReceiptPrintDocument(new CompanySnapshot(clean(header[0]),clean(header[1]),clean(header[2]),clean(header[3]),header[4],header[6]),
            d.numero(),d.confirmadoEn().atZoneSameInstant(ZoneId.of(header[5])).toLocalDateTime(),new CustomerSnapshot(d.clienteId()==null?"Consumidor Final":clean(d.cliente()),clean(d.identificacion())),
            clean(d.sucursal()),clean(cashName),clean(d.usuario()),d.observacion()==null||d.observacion().isBlank()?"Abono a cuenta":clean(d.observacion()),d.moneda(),
            d.aplicaciones().stream().filter(a->!a.revertida()).map(a->new PaidDocument(clean(a.factura()),a.monto(),a.fechaFactura(),a.totalDocumento(),
                a.notaDebitoId()==null?"INVOICE":"DEBIT_NOTE",a.notaDebitoId()==null?a.facturaId():a.notaDebitoId())).toList(),
            d.aplicado(),methods,d.recibido(),anterior,d.aplicado(),pendiente,List.of("Cobro recibido","Conserve este comprobante"));
        jdbc.update("UPDATE customer_receipt SET pos_print_snapshot=?,pos_cash_register_id=? WHERE id=? AND tenant_id=? AND empresa_id=? AND pos_print_snapshot IS NULL",
            JSON.writeValueAsString(doc),caja,d.id(),tenant,empresa);
    }
    public static String safeReference(String raw,String kind){
        String s=clean(raw);if(s==null||s.isBlank())return null;
        if(s.matches("(?is).*(?:cvv|cvc|pin|token|password)\\s*[:=].*"))return null;
        if(kind!=null&&kind.contains("CARD")){
            var masked=java.util.regex.Pattern.compile("(?:\\*{2,}|[xX]{2,})\\s*(\\d{4})(?!\\d)").matcher(s);
            return masked.find()?"**** "+masked.group(1):null;
        }
        if(s.matches("(?s).*(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d).*"))return null;
        return s;
    }
    private static String clean(String s){return s==null?null:s.replaceAll("[\\p{Cntrl}&&[^\\n]]"," ");}
}
