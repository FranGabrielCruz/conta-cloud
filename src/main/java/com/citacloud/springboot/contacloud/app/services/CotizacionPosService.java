package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class CotizacionPosService {
    private static final int SCALE=4;
    private final FacturacionPosService facturacion;
    private final CotizacionRepository cotizaciones;
    private final ClienteRepository clientes;
    private final ProductRepository productos;
    private final InventoryMovementRepository existencias;
    private final JdbcTemplate jdbc;

    public CotizacionPosService(FacturacionPosService facturacion,CotizacionRepository cotizaciones,
            ClienteRepository clientes,ProductRepository productos,InventoryMovementRepository existencias,JdbcTemplate jdbc) {
        this.facturacion=facturacion;this.cotizaciones=cotizaciones;this.clientes=clientes;
        this.productos=productos;this.existencias=existencias;this.jdbc=jdbc;
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.crear') and hasAuthority('cotizaciones.ver')")
    public CotizacionPosCargaDto cargar(String token,UUID almacenId,UUID cotizacionId) {
        if(cotizacionId==null)throw new ReglaNegocioException("Selecciona una cotización válida.");
        var principal=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        TerminalPosDto terminal=facturacion.terminal(token)
            .orElseThrow(()->new ReglaNegocioException("Esta terminal no está configurada o fue desactivada."));
        if(almacenId==null||!almacenId.equals(terminal.almacenId()))
            throw new ReglaNegocioException("La configuración del almacén cambió. Reinicia la venta antes de continuar.");
        Cotizacion cotizacion=cotizaciones.bloquear(cotizacionId,principal.tenantId(),empresa)
            .orElseThrow(()->new RecursoNoEncontradoException("Cotización no encontrada."));
        if(!EmpresaContext.permiteSucursal(cotizacion.getSucursalId()))
            throw new RecursoNoEncontradoException("Cotización no encontrada.");
        if(!cotizacion.getSucursalId().equals(terminal.sucursalId()))
            throw new ReglaNegocioException("La cotización pertenece a una sucursal distinta a la caja configurada.");
        if(cotizacion.getValidaHasta().isBefore(LocalDate.now()))
            throw new ReglaNegocioException("La cotización venció y ya no está disponible para cargar.");
        if(cotizacion.getEstado()!=EstadoCotizacion.ENVIADA&&cotizacion.getEstado()!=EstadoCotizacion.ACEPTADA)
            throw new ReglaNegocioException("La cotización ya no está disponible para cargar.");
        if(!terminal.monedaCodigo().equalsIgnoreCase(cotizacion.getMoneda().getCodigoIso()))
            throw new ReglaNegocioException("La moneda de la cotización no coincide con la moneda configurada en la caja.");
        Cliente cliente=clientes.findByIdAndTenantIdAndEmpresaId(cotizacion.getClienteId(),principal.tenantId(),empresa)
            .filter(Cliente::isActivo).orElseThrow(()->new ReglaNegocioException("El cliente de la cotización ya no está disponible."));
        ClientePosDto clienteDto=new ClientePosDto(cliente.getId(),cliente.getCodigo(),nombre(cliente),
            cliente.getTipoIdentificacion(),cliente.getIdentificacion(),cliente.getTelefono());
        List<CotizacionPosCargaDto.Linea> salida=new ArrayList<>();
        for(LineaCotizacion linea:cotizacion.getLineas()) {
            BigDecimal convertida=jdbc.queryForObject("""
                select coalesce(sum(c.converted_quantity),0) from sales_quote_line_conversion c
                where c.tenant_id=? and c.empresa_id=? and c.sales_quote_line_id=?
                """,BigDecimal.class,principal.tenantId(),empresa,linea.getId());
            BigDecimal pendiente=linea.getCantidad().subtract(convertida==null?BigDecimal.ZERO:convertida).setScale(SCALE,RoundingMode.HALF_UP);
            if(pendiente.signum()<=0)continue;
            Producto producto=productos.findByIdAndTenantIdAndEmpresaId(linea.getProductoId(),principal.tenantId(),empresa)
                .filter(Producto::isActivo).orElseThrow(()->new ReglaNegocioException("El producto "+linea.getDescripcion()+" ya no está disponible."));
            if(!producto.getMonedaId().equals(cotizacion.getMonedaId()))
                throw new ReglaNegocioException("El producto "+producto.getNombre()+" utiliza una moneda distinta a la cotización.");
            if(linea.getUnidadMedidaId()!=null&&!linea.getUnidadMedidaId().equals(producto.getUnidadMedidaId()))
                throw new ReglaNegocioException("La unidad de "+producto.getNombre()+" cambió. Revisa la cotización antes de cargarla.");
            BigDecimal tasa=producto.getImpuestoVenta()!=null&&producto.getImpuestoVenta().isActivo()
                ?producto.getImpuestoVenta().getPorcentaje():BigDecimal.ZERO;
            if(tasa.compareTo(linea.getTasaImpuesto())!=0)
                throw new ReglaNegocioException("El impuesto de "+producto.getNombre()+" cambió. Revisa la cotización antes de cargarla.");
            if(linea.getDescuento().signum()>0&&!producto.isPermiteDescuentos())
                throw new ReglaNegocioException("El producto "+producto.getNombre()+" ya no permite descuentos.");
            if(linea.getDescuento().signum()>0&&!principal.permisos().contains("facturas.descuentos.aplicar"))
                throw new ReglaNegocioException("No tienes permiso para cargar una cotización con descuentos.");
            BigDecimal disponible=producto.isControlaExistencia()
                ?existencias.saldo(principal.tenantId(),empresa,almacenId,producto.getId()):BigDecimal.ZERO;
            if(producto.isControlaExistencia()&&!producto.isPermiteExistenciaNegativa()&&disponible.compareTo(pendiente)<0)
                throw new ReglaNegocioException("Disponibilidad insuficiente para "+producto.getNombre()+". Cotizado: "+
                    pendiente.stripTrailingZeros().toPlainString()+", disponible: "+disponible.stripTrailingZeros().toPlainString()+".");
            BigDecimal descuentoUnitario=linea.getCantidad().signum()==0?BigDecimal.ZERO:
                linea.getDescuento().divide(linea.getCantidad(),SCALE,RoundingMode.HALF_UP);
            ProductoPosDto productoDto=new ProductoPosDto(producto.getId(),producto.getCodigo(),producto.getNombre(),
                producto.getCodigoBarras(),producto.getUnidadMedida().getAbreviatura(),linea.getPrecioUnitario(),tasa,
                disponible,producto.isControlaExistencia(),producto.isPermiteExistenciaNegativa(),
                producto.isPermiteDescuentos(),producto.isPermiteRecargos());
            salida.add(new CotizacionPosCargaDto.Linea(linea.getId(),productoDto,pendiente,descuentoUnitario));
        }
        if(salida.isEmpty())throw new ReglaNegocioException("La cotización ya no tiene cantidades pendientes para facturar.");
        return new CotizacionPosCargaDto(cotizacion.getId(),cotizacion.getNumero(),clienteDto,
            cotizacion.getMoneda().getCodigoIso(),List.copyOf(salida));
    }

    private static String nombre(Cliente c){return c.getTipoPersona()==TipoPersonaCliente.JURIDICA?c.getRazonSocial():
        ((c.getNombres()==null?"":c.getNombres())+" "+(c.getApellidos()==null?"":c.getApellidos())).trim();}
}
