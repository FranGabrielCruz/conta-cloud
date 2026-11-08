package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.services.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class NotaCreditoVentaConsultaRepository {
    private final JdbcTemplate jdbc;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public NotaCreditoVentaConsultaRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Resumen(UUID id,String numero,LocalDate fecha,String cliente,String factura,String ncf,String moneda,BigDecimal total,String estado) {}
    public record FacturaOpcion(UUID id,String numero,String cliente,LocalDate fecha,String moneda,BigDecimal total,BigDecimal disponible) {}
    public record Consumido(BigDecimal cantidad,BigDecimal bruto,BigDecimal descuento,BigDecimal recargo,BigDecimal impuesto,BigDecimal total) {}
    public record Aplicacion(BigDecimal anterior,BigDecimal aplicado,BigDecimal posterior,BigDecimal favor) {}
    public Map<UUID,Consumido> consumidos(UUID factura){
        var p=TenantContext.principalActual();
        return jdbc.query("""
            SELECT linea,sum(q),sum(g),sum(d),sum(s),sum(t),sum(a) FROM (
              SELECT l.sales_invoice_line_id linea,l.returned_quantity q,l.gross_subtotal g,l.discount_amount d,l.surcharge_amount s,l.tax_amount t,l.total a
              FROM sales_return_line l JOIN sales_return r ON r.id=l.sales_return_id AND r.tenant_id=l.tenant_id AND r.empresa_id=l.empresa_id
              WHERE l.tenant_id=? AND l.empresa_id=? AND r.sales_invoice_id=? AND EXISTS(SELECT 1 FROM sales_credit_note n WHERE n.tenant_id=r.tenant_id AND n.empresa_id=r.empresa_id AND n.sales_return_id=r.id AND n.status='CONFIRMED')
              UNION ALL SELECT sales_invoice_line_id,credited_quantity,gross_amount,discount_amount,surcharge_amount,tax_amount,credited_amount
              FROM sales_credit_note_line cl WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=? AND EXISTS(SELECT 1 FROM sales_credit_note n WHERE n.id=cl.sales_credit_note_id AND n.tenant_id=cl.tenant_id AND n.empresa_id=cl.empresa_id AND n.status='CONFIRMED')
            ) x GROUP BY linea
            """,r->{Map<UUID,Consumido> result=new HashMap<>();while(r.next())result.put(uuid(r,1),new Consumido(r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getBigDecimal(5),r.getBigDecimal(6),r.getBigDecimal(7)));return result;},p.tenantId(),p.empresaId(),factura,p.tenantId(),p.empresaId(),factura);
    }

    public Page<Resumen> buscar(String texto,String cliente,LocalDate desde,LocalDate hasta,int pagina,int size){return buscar(texto,cliente,desde,hasta,pagina,size,"");}
    public Page<Resumen> buscar(String texto,String cliente,LocalDate desde,LocalDate hasta,int pagina,int size,String estado){
        var p=TenantContext.principalActual();String sql="""
            FROM sales_credit_note n JOIN sales_invoice f ON f.id=n.sales_invoice_id AND f.tenant_id=n.tenant_id AND f.empresa_id=n.empresa_id
            WHERE n.tenant_id=? AND n.empresa_id=? AND (? OR f.branch_id=ANY(?::uuid[]))
              AND (?='' OR lower(n.credit_note_number || ' ' || f.internal_number || ' ' || f.customer_name) LIKE ?)
              AND (?='' OR lower(f.customer_name) LIKE ?) AND (?::date IS NULL OR coalesce(n.issue_date,(n.confirmed_at AT TIME ZONE coalesce(f.pos_issuer_zone,'America/Santo_Domingo'))::date)>=?::date)
              AND (?::date IS NULL OR coalesce(n.issue_date,(n.confirmed_at AT TIME ZONE coalesce(f.pos_issuer_zone,'America/Santo_Domingo'))::date)<=?::date) AND (?='' OR n.status=?)
            """;
        Object[] args={p.tenantId(),p.empresaId(),p.accesoTodasSucursales(),"{"+p.sucursalIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","))+"}",texto,"%"+texto.toLowerCase(Locale.ROOT)+"%",cliente,"%"+cliente.toLowerCase(Locale.ROOT)+"%",desde,desde,hasta,hasta,estado,estado};
        long count=jdbc.queryForObject("SELECT count(*) "+sql,Long.class,args);
        var all=new ArrayList<>(Arrays.asList(args));all.add(size);all.add((long)pagina*size);
        var rows=jdbc.query("SELECT n.id,n.credit_note_number,coalesce(n.issue_date,(n.confirmed_at AT TIME ZONE coalesce(f.pos_issuer_zone,'America/Santo_Domingo'))::date),f.customer_name,f.internal_number,f.currency_code,n.amount,n.status "+sql+" ORDER BY n.confirmed_at DESC,n.id DESC LIMIT ? OFFSET ?",(r,i)->new Resumen(uuid(r,1),r.getString(2),r.getDate(3).toLocalDate(),r.getString(4),r.getString(5),null,r.getString(6),r.getBigDecimal(7),r.getString(8)),all.toArray());
        return new PageImpl<>(rows,PageRequest.of(pagina,size),count);
    }
    public Page<FacturaOpcion> facturas(String texto,int page,int size){
        var p=TenantContext.principalActual();String sql="""
            FROM sales_invoice f WHERE f.tenant_id=? AND f.empresa_id=? AND f.status='CONFIRMED'
              AND f.total>(SELECT coalesce(sum(n.amount),0) FROM sales_credit_note n WHERE n.tenant_id=f.tenant_id AND n.empresa_id=f.empresa_id AND n.sales_invoice_id=f.id AND n.status='CONFIRMED')
              AND EXISTS(SELECT 1 FROM sales_invoice_line il WHERE il.tenant_id=f.tenant_id AND il.empresa_id=f.empresa_id AND il.sales_invoice_id=f.id AND il.quantity>
                (SELECT coalesce(sum(rl.returned_quantity),0) FROM sales_return_line rl WHERE rl.tenant_id=f.tenant_id AND rl.empresa_id=f.empresa_id AND rl.sales_invoice_line_id=il.id AND EXISTS(SELECT 1 FROM sales_credit_note n WHERE n.sales_return_id=rl.sales_return_id AND n.tenant_id=rl.tenant_id AND n.empresa_id=rl.empresa_id AND n.status='CONFIRMED'))+
                (SELECT coalesce(sum(cl.credited_quantity),0) FROM sales_credit_note_line cl WHERE cl.tenant_id=f.tenant_id AND cl.empresa_id=f.empresa_id AND cl.sales_invoice_line_id=il.id AND EXISTS(SELECT 1 FROM sales_credit_note n WHERE n.id=cl.sales_credit_note_id AND n.tenant_id=cl.tenant_id AND n.empresa_id=cl.empresa_id AND n.status='CONFIRMED')))
              AND (? OR f.branch_id=ANY(?::uuid[])) AND (?='' OR lower(f.internal_number || ' ' || coalesce(nullif(trim(f.customer_name),''),'Consumidor Final')) LIKE ?)
            """;
        Object[] args={p.tenantId(),p.empresaId(),p.accesoTodasSucursales(),"{"+p.sucursalIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","))+"}",texto,"%"+texto.toLowerCase(Locale.ROOT)+"%"};
        long count=jdbc.queryForObject("SELECT count(*) "+sql,Long.class,args);var params=new ArrayList<>(Arrays.asList(args));params.add(size);params.add((long)page*size);
        return new PageImpl<>(jdbc.query("SELECT f.id,f.internal_number,coalesce(nullif(trim(f.customer_name),''),'Consumidor Final'),f.invoice_date,f.currency_code,f.total,f.total-(SELECT coalesce(sum(n.amount),0) FROM sales_credit_note n WHERE n.tenant_id=f.tenant_id AND n.empresa_id=f.empresa_id AND n.sales_invoice_id=f.id AND n.status='CONFIRMED') "+sql+" ORDER BY f.confirmed_at DESC,f.id DESC LIMIT ? OFFSET ?",(r,i)->new FacturaOpcion(uuid(r,1),r.getString(2),r.getString(3),r.getDate(4).toLocalDate(),r.getString(5),r.getBigDecimal(6),r.getBigDecimal(7)),params.toArray()),PageRequest.of(page,size),count);
    }
    public Consumido consumido(UUID facturaLinea){var p=TenantContext.principalActual();return jdbc.query("""
        SELECT coalesce(sum(q),0),coalesce(sum(g),0),coalesce(sum(d),0),coalesce(sum(s),0),coalesce(sum(t),0),coalesce(sum(a),0) FROM (
          SELECT returned_quantity q,gross_subtotal g,discount_amount d,surcharge_amount s,tax_amount t,total a
          FROM sales_return_line rl WHERE tenant_id=? AND empresa_id=? AND sales_invoice_line_id=? AND EXISTS(SELECT 1 FROM sales_credit_note n WHERE n.sales_return_id=rl.sales_return_id AND n.tenant_id=rl.tenant_id AND n.empresa_id=rl.empresa_id AND n.status='CONFIRMED')
          UNION ALL SELECT credited_quantity,gross_amount,discount_amount,surcharge_amount,tax_amount,credited_amount
          FROM sales_credit_note_line cl WHERE tenant_id=? AND empresa_id=? AND sales_invoice_line_id=? AND EXISTS(SELECT 1 FROM sales_credit_note n WHERE n.id=cl.sales_credit_note_id AND n.tenant_id=cl.tenant_id AND n.empresa_id=cl.empresa_id AND n.status='CONFIRMED')
        ) x
        """,r->{r.next();return new Consumido(r.getBigDecimal(1),r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getBigDecimal(5),r.getBigDecimal(6));},p.tenantId(),p.empresaId(),facturaLinea,p.tenantId(),p.empresaId(),facturaLinea);}
    public BigDecimal totalAcreditado(UUID factura){var p=TenantContext.principalActual();return jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM sales_credit_note WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=? AND status='CONFIRMED'",BigDecimal.class,p.tenantId(),p.empresaId(),factura);}
    public Aplicacion aplicar(UUID factura,UUID cliente,UUID moneda,BigDecimal amount){
        if(cliente==null)return new Aplicacion(null,BigDecimal.ZERO,null,BigDecimal.ZERO);
        var p=TenantContext.principalActual();
        jdbc.queryForObject("SELECT id FROM customer WHERE tenant_id=? AND empresa_id=? AND id=? FOR UPDATE",UUID.class,p.tenantId(),p.empresaId(),cliente);
        var row=jdbc.query("SELECT id,original_amount-applied_amount,status FROM customer_accounts_receivable WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=? AND customer_id=? AND currency_id=? FOR UPDATE",r->r.next()?new Object[]{uuid(r,1),r.getBigDecimal(2),r.getString(3)}:null,p.tenantId(),p.empresaId(),factura,cliente,moneda);
        BigDecimal anterior=row==null||"VOIDED".equals(row[2])?BigDecimal.ZERO:(BigDecimal)row[1];
        BigDecimal applied=anterior.min(amount);
        if(applied.signum()>0)jdbc.update("UPDATE customer_accounts_receivable SET applied_amount=applied_amount+?,status=CASE WHEN applied_amount+?=original_amount THEN 'PAID' ELSE 'PARTIALLY_PAID' END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND empresa_id=? AND id=?",applied,applied,p.tenantId(),p.empresaId(),row[0]);
        return new Aplicacion(anterior,applied,anterior.subtract(applied),amount.subtract(applied));
    }
    public String siguiente(){var p=TenantContext.principalActual();Long n=jdbc.queryForObject("INSERT INTO sales_credit_note_sequence(tenant_id,empresa_id,last_number) VALUES(?,?,1) ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET last_number=sales_credit_note_sequence.last_number+1 RETURNING last_number",Long.class,p.tenantId(),p.empresaId());return "NCV-"+String.format(Locale.ROOT,"%06d",n);}
    public void insertar(NotaCreditoVentaDocumento d,UUID factura,UUID moneda){
        var p=TenantContext.principalActual();
        jdbc.update("INSERT INTO sales_credit_note(id,tenant_id,empresa_id,credit_note_number,sales_invoice_id,customer_id,currency_id,amount,applied_receivable_amount,customer_credit_amount,status,confirmed_at,confirmed_by,origin_kind,issue_date,document_snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,'CONFIRMED',CURRENT_TIMESTAMP,?,'FINANCIAL',?,?)",d.id(),p.tenantId(),p.empresaId(),d.numero(),factura,d.clienteId(),moneda,d.total(),d.aplicadoCxC(),d.creditoCliente(),p.usuarioId(),d.fecha(),JSON.writeValueAsString(d));
        for(var l:d.lineas())jdbc.update("INSERT INTO sales_credit_note_line(tenant_id,empresa_id,sales_credit_note_id,sales_invoice_id,sales_invoice_line_id,credited_quantity,gross_amount,discount_amount,surcharge_amount,tax_amount,credited_amount) VALUES(?,?,?,?,?,?,?,?,?,?,?)",p.tenantId(),p.empresaId(),d.id(),factura,l.lineaFacturaId(),l.cantidad(),l.bruto(),l.descuento(),l.recargo(),l.impuesto(),l.total());
        if(d.creditoCliente().signum()>0)jdbc.update("INSERT INTO customer_credit_ledger(tenant_id,empresa_id,customer_id,currency_id,sales_credit_note_id,amount,available_amount,created_by) VALUES(?,?,?,?,?,?,?,?)",p.tenantId(),p.empresaId(),d.clienteId(),moneda,d.id(),d.creditoCliente(),d.creditoCliente(),p.usuarioId());
    }
    public NotaCreditoVentaDocumento documento(UUID id){
        var p=TenantContext.principalActual();return jdbc.query("SELECT n.document_snapshot,f.branch_id,n.sales_return_id,n.status FROM sales_credit_note n JOIN sales_invoice f ON f.id=n.sales_invoice_id AND f.tenant_id=n.tenant_id AND f.empresa_id=n.empresa_id WHERE n.id=? AND n.tenant_id=? AND n.empresa_id=?",r->{
            if(!r.next()||!EmpresaContext.permiteSucursal(uuid(r,2)))throw new RecursoNoEncontradoException("Nota de crédito no encontrada.");
            String snapshot=r.getString(1);if(snapshot!=null)return JSON.readValue(snapshot,NotaCreditoVentaDocumento.class).conEstado(r.getString(4));
            return historico(id,uuid(r,3));
        },id,p.tenantId(),p.empresaId());
    }
    public NotaCreditoVentaCicloDto ciclo(UUID id,boolean lock){
        var p=TenantContext.principalActual();return jdbc.query("""
            SELECT n.sales_invoice_id,n.sales_return_id,r.status,n.version,n.status,n.annulment_reason,n.annulled_by_name,n.annulled_at,n.annulled_zone,f.branch_id,
              (n.status='CONFIRMED' AND f.status='CONFIRMED' AND (n.applied_receivable_amount=0 OR EXISTS(SELECT 1 FROM customer_accounts_receivable a WHERE a.tenant_id=n.tenant_id AND a.empresa_id=n.empresa_id AND a.sales_invoice_id=n.sales_invoice_id AND a.status<>'VOIDED' AND a.applied_amount>=n.applied_receivable_amount)) AND (n.customer_credit_amount=0 OR EXISTS(SELECT 1 FROM customer_credit_ledger c WHERE c.tenant_id=n.tenant_id AND c.empresa_id=n.empresa_id AND c.sales_credit_note_id=n.id AND c.available_amount=c.amount AND c.amount=n.customer_credit_amount))) eligible
            FROM sales_credit_note n JOIN sales_invoice f ON f.id=n.sales_invoice_id AND f.tenant_id=n.tenant_id AND f.empresa_id=n.empresa_id
            LEFT JOIN sales_return r ON r.id=n.sales_return_id AND r.tenant_id=n.tenant_id AND r.empresa_id=n.empresa_id
            WHERE n.id=? AND n.tenant_id=? AND n.empresa_id=?
            """+(lock?" FOR UPDATE OF n":""),r->{
                if(!r.next()||!EmpresaContext.permiteSucursal(uuid(r,10)))throw new RecursoNoEncontradoException("Nota de crédito no encontrada.");
                var date=r.getObject(8,OffsetDateTime.class);return new NotaCreditoVentaCicloDto(uuid(r,1),uuid(r,2),r.getString(3),r.getLong(4),r.getString(5),r.getBoolean(11),r.getString(6),r.getString(7),date==null?null:date.atZoneSameInstant(ZoneId.of(r.getString(9))).toLocalDateTime());
            },id,p.tenantId(),p.empresaId());
    }
    public void revertir(NotaCreditoVentaDocumento d,UUID factura){
        var p=TenantContext.principalActual();
        if(d.clienteId()!=null)jdbc.queryForObject("SELECT id FROM customer WHERE tenant_id=? AND empresa_id=? AND id=? FOR UPDATE",UUID.class,p.tenantId(),p.empresaId(),d.clienteId());
        if(d.aplicadoCxC().signum()>0){
            var ar=jdbc.query("SELECT id,applied_amount,status FROM customer_accounts_receivable WHERE tenant_id=? AND empresa_id=? AND sales_invoice_id=? FOR UPDATE",r->r.next()?new Object[]{uuid(r,1),r.getBigDecimal(2),r.getString(3)}:null,p.tenantId(),p.empresaId(),factura);
            if(ar==null||"VOIDED".equals(ar[2])||((BigDecimal)ar[1]).compareTo(d.aplicadoCxC())<0)throw new ReglaNegocioException("La cuenta por cobrar cambió y requiere revisión antes de anular la nota.");
            jdbc.update("UPDATE customer_accounts_receivable SET applied_amount=applied_amount-?,status=CASE WHEN applied_amount-?=0 THEN 'PENDING' WHEN applied_amount-?=original_amount THEN 'PAID' ELSE 'PARTIALLY_PAID' END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND empresa_id=? AND id=?",d.aplicadoCxC(),d.aplicadoCxC(),d.aplicadoCxC(),p.tenantId(),p.empresaId(),ar[0]);
        }
        if(d.creditoCliente().signum()>0){
            var credit=jdbc.query("SELECT id,amount,available_amount FROM customer_credit_ledger WHERE tenant_id=? AND empresa_id=? AND sales_credit_note_id=? FOR UPDATE",r->r.next()?new Object[]{uuid(r,1),r.getBigDecimal(2),r.getBigDecimal(3)}:null,p.tenantId(),p.empresaId(),d.id());
            if(credit==null||((BigDecimal)credit[1]).compareTo(d.creditoCliente())!=0||((BigDecimal)credit[2]).compareTo(d.creditoCliente())!=0)throw new ReglaNegocioException("El saldo a favor ya fue utilizado o cambió. Revierta sus aplicaciones antes de anular la nota.");
            jdbc.update("UPDATE customer_credit_ledger SET available_amount=0 WHERE tenant_id=? AND empresa_id=? AND id=?",p.tenantId(),p.empresaId(),credit[0]);
        }
    }
    public void marcarAnulada(UUID id,long version,String motivo,UUID key,String zone){
        var p=TenantContext.principalActual();int changed=jdbc.update("UPDATE sales_credit_note SET status='VOIDED',version=version+1,annulled_at=CURRENT_TIMESTAMP,annulled_by=?,annulled_by_name=?,annulment_reason=?,annulment_key=?,annulled_zone=? WHERE tenant_id=? AND empresa_id=? AND id=? AND status='CONFIRMED' AND version=?",p.usuarioId(),p.nombre(),motivo,key,zone,p.tenantId(),p.empresaId(),id,version);
        if(changed!=1)throw new ReglaNegocioException("La nota cambió o ya fue anulada. Actualice la consulta.");
    }
    private NotaCreditoVentaDocumento historico(UUID id,UUID retorno){
        var p=TenantContext.principalActual();var lines=jdbc.query("SELECT l.sales_invoice_line_id,l.product_code,l.description,l.unit_name,l.returned_quantity,l.unit_price,l.gross_subtotal,l.discount_amount,l.surcharge_amount,il.tax_name,il.tax_rate,l.tax_amount,l.total FROM sales_return_line l JOIN sales_invoice_line il ON il.id=l.sales_invoice_line_id AND il.tenant_id=l.tenant_id AND il.empresa_id=l.empresa_id WHERE l.tenant_id=? AND l.empresa_id=? AND l.sales_return_id=? ORDER BY l.line_order",(r,i)->new NotaCreditoVentaDocumento.Linea(uuid(r,1),r.getString(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getBigDecimal(9),r.getString(10),r.getBigDecimal(11),r.getBigDecimal(12),r.getBigDecimal(13)),p.tenantId(),p.empresaId(),retorno);
        return jdbc.query("SELECT n.*,f.branch_id,f.customer_name,f.customer_identification,f.internal_number,f.invoice_date,f.payment_condition,f.currency_code,f.pos_issuer_name,f.pos_issuer_tax_id,f.pos_issuer_address,f.pos_issuer_phone,f.pos_issuer_zone,r.return_number,r.reason_name,r.observation,r.gross_subtotal,r.discount_total,r.surcharge_total,r.tax_total FROM sales_credit_note n JOIN sales_invoice f ON f.id=n.sales_invoice_id AND f.tenant_id=n.tenant_id AND f.empresa_id=n.empresa_id JOIN sales_return r ON r.id=n.sales_return_id AND r.tenant_id=n.tenant_id AND r.empresa_id=n.empresa_id WHERE n.tenant_id=? AND n.empresa_id=? AND n.id=?",rs->{if(!rs.next())throw new RecursoNoEncontradoException("Datos históricos de nota no encontrados.");return new NotaCreditoVentaDocumento(id,rs.getString("credit_note_number"),fechaHistorica(rs).toLocalDate(),fechaHistorica(rs),null,"SALES_RETURN",rs.getString("return_number"),rs.getString("status"),(UUID)rs.getObject("branch_id"),(UUID)rs.getObject("customer_id"),rs.getString("customer_name"),rs.getString("customer_identification"),null,rs.getString("pos_issuer_name"),rs.getString("pos_issuer_tax_id"),rs.getString("pos_issuer_address"),rs.getString("pos_issuer_phone"),rs.getString("internal_number"),rs.getDate("invoice_date").toLocalDate(),null,null,rs.getString("payment_condition"),rs.getString("currency_code"),rs.getString("reason_name"),rs.getString("observation"),null,lines,rs.getBigDecimal("gross_subtotal"),rs.getBigDecimal("discount_total"),rs.getBigDecimal("surcharge_total"),rs.getBigDecimal("tax_total"),rs.getBigDecimal("amount"),null,rs.getBigDecimal("applied_receivable_amount"),null,rs.getBigDecimal("customer_credit_amount"));},p.tenantId(),p.empresaId(),id);
    }
    private static LocalDateTime fechaHistorica(ResultSet r)throws SQLException{String zone=r.getString("pos_issuer_zone");return r.getObject("confirmed_at",OffsetDateTime.class).atZoneSameInstant(ZoneId.of(zone==null||zone.isBlank()?"America/Santo_Domingo":zone)).toLocalDateTime();}
    private static UUID uuid(ResultSet r,int column)throws SQLException{return r.getObject(column,UUID.class);}
}
