package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class ActivoFijoDto {
    private ActivoFijoDto() {}

    public enum Estado {
        ACTIVO("Activo"), INACTIVO("Inactivo"), BAJA("Dado de baja");

        private final String etiqueta;
        Estado(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public enum Metodo {
        LINEA_RECTA("Línea recta");

        private final String etiqueta;
        Metodo(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public enum MovimientoTipo {
        DEPRECIACION("Depreciación"), MANTENIMIENTO("Mantenimiento"), AJUSTE("Ajuste"), BAJA("Baja");

        private final String etiqueta;
        MovimientoTipo(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
    }

    public record Filtro(String categoria, UUID sucursal, String ubicacion, Estado estado, String buscar) {}
    public record Categoria(String codigo, String nombre) {}
    public record Opcion(UUID id, String nombre) {}
    public record Moneda(UUID id, String codigo, boolean base) {}
    public record Factura(UUID id, UUID proveedorId, String numero) {}
    public record Catalogos(List<Categoria> categorias, List<Opcion> sucursales, List<String> ubicaciones,
                            List<Moneda> monedas, List<Opcion> proveedores, List<Opcion> responsables) {}
    public record Depreciacion(boolean depreciable, Metodo metodo, Integer vidaMeses, BigDecimal residual, LocalDate inicio) {}
    public record Adquisicion(LocalDate fecha, BigDecimal costo, UUID moneda, UUID proveedor, UUID factura, String referencia) {}
    public record Asignacion(UUID sucursal, String ubicacion, UUID responsable) {}
    public record Datos(String nombre, String categoria, String marca, String modelo, String serie,
                        Adquisicion adquisicion, Asignacion asignacion, Depreciacion depreciacion, String observacion) {}
    public record Resumen(UUID id, String codigo, String nombre, String categoria, String sucursal, String ubicacion,
                          Estado estado, String condicion, LocalDate fecha, String moneda,
                          BigDecimal costo, BigDecimal depreciacion, BigDecimal valorLibros) {}
    public record Totales(String moneda, long cantidad, long vigentes, long mantenimiento,
                          BigDecimal costo, BigDecimal depreciacion, BigDecimal valorLibros) {}
    public record Listado(Page<Resumen> activos, List<Totales> totales) {}
    public record Destino(String sucursal, String ubicacion, String responsable) {}
    public record HistorialAsignacion(OffsetDateTime fecha, String usuario, Destino anterior, Destino nueva) {}
    public record Movimiento(MovimientoTipo tipo, LocalDate fecha, String descripcion, BigDecimal importe) {}
    public record Registro(Resumen resumen, long version, Datos datos, Destino destino, String proveedor,
                           String factura, boolean finanzasBloqueadas) {}
    public record Detalle(Registro activo, List<HistorialAsignacion> asignaciones, List<Movimiento> movimientos) {}
    public record Reporte(String empresa, String rnc, String direccion, String telefono, String pais, String logo,
                          LocalDate fecha, List<String> filtros, List<Resumen> activos, List<Totales> totales) {}
}
