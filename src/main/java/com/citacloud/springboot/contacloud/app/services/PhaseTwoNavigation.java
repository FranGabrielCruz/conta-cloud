package com.citacloud.springboot.contacloud.app.services;

import java.util.List;
import java.util.Optional;

/**
 * Definición única de la navegación preparada para la Fase 2.
 * Mantiene alineados menú, rutas, módulos y permisos sin incorporar lógica comercial.
 */
public final class PhaseTwoNavigation {
    private PhaseTwoNavigation() {}

    public static final String VENTAS = "VENTAS";
    public static final String COMPRAS = "COMPRAS";
    public static final String CAJA_BANCOS = "CAJA_BANCOS";
    public static final String INVENTARIO = "INVENTARIO";
    public static final String CONTABILIDAD = "CONTABILIDAD";
    public static final String FISCAL = "FISCAL";
    public static final String ACTIVOS_FIJOS = "ACTIVOS_FIJOS";
    public static final String FINANZAS = "FINANZAS";
    public static final String REPORTES = "REPORTES";

    public static final List<Section> SECTIONS = List.of(
        new Section("VENTAS", List.of(
            item("Clientes", "clientes", "USERS", VENTAS, "clientes.ver",
                "Administra los clientes de la empresa."),
            item("Cotizaciones", "cotizaciones", "CLIPBOARD_TEXT", VENTAS, "cotizaciones.ver",
                "Administra las cotizaciones realizadas a clientes."),
            item("Facturas", "facturas", "INVOICE", VENTAS, "facturas.ver",
                "Administra las facturas emitidas a clientes."),
            item("Notas de crédito", "notas-credito", "FILE_REMOVE", VENTAS, "notas_credito.ver",
                "Administra las notas de crédito emitidas a clientes."),
            item("Notas de débito", "notas-debito", "FILE_ADD", VENTAS, "notas_debito.ver",
                "Administra las notas de débito emitidas a clientes."),
            item("Recibos", "recibos", "RECORDS", VENTAS, "recibos.ver",
                "Administra los recibos y cobros realizados a clientes."),
            item("Cuentas por cobrar", "cuentas-por-cobrar", "MONEY_DEPOSIT", VENTAS, "cuentas_cobrar.ver",
                "Consulta y administra las cuentas pendientes de cobro."))),
        new Section("COMPRAS", List.of(
            item("Proveedores", "proveedores", "TRUCK", COMPRAS, "proveedores.ver",
                "Administra los proveedores de la empresa."),
            item("Órdenes de compra", "ordenes-compra", "CART", COMPRAS, "ordenes_compra.ver",
                "Administra las órdenes de compra realizadas a proveedores."),
            item("Recepciones", "recepciones", "STOCK", COMPRAS, "recepciones.ver",
                "Confirma la recepción de productos y sus entradas al inventario."),
            item("Facturas de proveedores", "facturas-proveedores", "INVOICE", COMPRAS, "facturas_proveedores.ver",
                "Administra las facturas recibidas de proveedores."),
            item("Notas de crédito", "notas-credito-proveedores", "FILE_REMOVE", COMPRAS,
                "notas_credito_proveedores.ver", "Administra las notas de crédito recibidas de proveedores."),
            item("Pagos", "pagos-proveedores", "MONEY_WITHDRAW", COMPRAS, "pagos_proveedores.ver",
                "Administra los pagos realizados a proveedores."),
            item("Cuentas por pagar", "cuentas-por-pagar", "MONEY", COMPRAS, "cuentas_pagar.ver",
                "Consulta y administra las cuentas pendientes de pago."))),
        new Section("CAJA Y BANCOS", List.of(
            item("Cajas", "cajas", "CASH", CAJA_BANCOS, "cajas.ver",
                "Administra las cajas utilizadas por la empresa."),
            item("Operaciones de caja", "operaciones-caja", "CALC_BOOK", CAJA_BANCOS, "operaciones_caja.ver",
                "Abre, opera, cierra y consulta los turnos de caja."),
            item("Cuentas bancarias", "cuentas-bancarias", "CREDIT_CARD", CAJA_BANCOS,
                "cuentas_bancarias.ver", "Administra las cuentas bancarias de la empresa."),
            item("Ingresos", "ingresos", "MONEY_DEPOSIT", CAJA_BANCOS, "ingresos.ver",
                "Administra los movimientos de ingreso de caja y bancos."),
            item("Egresos", "egresos", "MONEY_WITHDRAW", CAJA_BANCOS, "egresos.ver",
                "Administra los movimientos de egreso de caja y bancos."),
            item("Transferencias", "transferencias", "EXCHANGE", CAJA_BANCOS, "transferencias.ver",
                "Administra las transferencias entre cajas y cuentas bancarias."),
            item("Conciliación bancaria", "conciliacion-bancaria", "SCALE", CAJA_BANCOS,
                "conciliacion_bancaria.ver", "Administra la conciliación de las cuentas bancarias."))),
        new Section("INVENTARIO", List.of(
            item("Productos", "productos", "CUBE", INVENTARIO, "productos.ver", placeholder()),
            item("Categorías", "categorias", "TAG", INVENTARIO, "categorias.ver",
                "Administra las categorías utilizadas para clasificar productos y servicios."),
            item("Unidades de medida", "unidades-medida", "SCALE", INVENTARIO, "unidades_medida.ver",
                "Administra las unidades de medida utilizadas en productos, servicios y operaciones de inventario."),
            item("Almacenes", "almacenes", "DATABASE", INVENTARIO, "almacenes.ver", placeholder()),
            item("Movimientos", "inventario/movimientos", "EXCHANGE", INVENTARIO, "inventario_movimientos.ver", placeholder()),
            item("Transferencias", "inventario/transferencias", "ARROWS_LONG_H", INVENTARIO, "inventario_transferencias.ver", placeholder()),
            item("Ajustes", "inventario/ajustes", "SLIDERS", INVENTARIO, "inventario_ajustes.ver", placeholder()),
            item("Kardex", "kardex", "BOOK", INVENTARIO, "kardex.ver", placeholder()),
            item("Conteo físico", "inventario/conteo-fisico", "CHECK_SQUARE_O", INVENTARIO, "conteo_fisico.ver", placeholder()))),
        new Section("CONTABILIDAD", List.of(
            item("Catálogo de cuentas", "catalogo-cuentas", "BOOK_DOLLAR", CONTABILIDAD, "catalogo_cuentas.ver", placeholder("Catálogo de cuentas")),
            item("Asientos", "asientos", "EDIT", CONTABILIDAD, "asientos.ver", placeholder("Asientos")),
            item("Libro diario", "libro-diario", "BOOK", CONTABILIDAD, "libro_diario.ver", placeholder("Libro diario")),
            item("Libro mayor", "libro-mayor", "BOOK", CONTABILIDAD, "libro_mayor.ver", placeholder("Libro mayor")),
            item("Balanza", "balanza", "SCALE", CONTABILIDAD, "balanza.ver", placeholder("Balanza")),
            item("Centros de costos", "centros-costos", "GROUP", CONTABILIDAD, "centros_costos.ver", placeholder("Centros de costos")),
            item("Cierres", "cierres-contables", "LOCK", CONTABILIDAD, "cierres_contables.ver", placeholder("Cierres")))),
        new Section("FISCAL", List.of(
            item("NCF", "fiscal/ncf", "FILE_TEXT_O", FISCAL, "fiscal_ncf.ver", placeholder("NCF")),
            item("e-CF", "fiscal/ecf", "FILE", FISCAL, "fiscal_ecf.ver", placeholder("e-CF")),
            item("Impuestos", "fiscal/impuestos", "MONEY", FISCAL, "fiscal_impuestos.ver", placeholder("Impuestos")),
            item("Retenciones", "fiscal/retenciones", "MONEY_WITHDRAW", FISCAL, "fiscal_retenciones.ver", placeholder("Retenciones")),
            item("606", "fiscal/606", "TABLE", FISCAL, "fiscal_606.ver", placeholder("606")),
            item("607", "fiscal/607", "TABLE", FISCAL, "fiscal_607.ver", placeholder("607")),
            item("608", "fiscal/608", "TABLE", FISCAL, "fiscal_608.ver", placeholder("608")),
            item("609", "fiscal/609", "TABLE", FISCAL, "fiscal_609.ver", placeholder("609")),
            item("IT-1", "fiscal/it-1", "CLIPBOARD_TEXT", FISCAL, "fiscal_it1.ver", placeholder("IT-1")),
            item("IR-17", "fiscal/ir-17", "CLIPBOARD_TEXT", FISCAL, "fiscal_ir17.ver", placeholder("IR-17")))),
        new Section("ACTIVOS FIJOS", List.of(
            item("Activos", "activos-fijos", "BUILDING", ACTIVOS_FIJOS, "activos_fijos.ver", placeholder("Activos")),
            item("Depreciaciones", "activos-fijos/depreciaciones", "TRENDING_DOWN", ACTIVOS_FIJOS, "depreciaciones.ver", placeholder("Depreciaciones")),
            item("Mantenimiento", "activos-fijos/mantenimiento", "WRENCH", ACTIVOS_FIJOS, "mantenimiento_activos.ver", placeholder("Mantenimiento")),
            item("Bajas", "activos-fijos/bajas", "TRASH", ACTIVOS_FIJOS, "bajas_activos.ver", placeholder("Bajas")))),
        new Section("FINANZAS", List.of(
            item("Presupuestos", "presupuestos", "CALC_BOOK", FINANZAS, "presupuestos.ver", placeholder("Presupuestos")),
            item("Flujo de efectivo", "flujo-efectivo", "EXCHANGE", FINANZAS, "flujo_efectivo.ver", placeholder("Flujo de efectivo")),
            item("Proyecciones", "proyecciones", "LINE_CHART", FINANZAS, "proyecciones.ver", placeholder("Proyecciones")),
            item("Rentabilidad", "rentabilidad", "TRENDING_UP", FINANZAS, "rentabilidad.ver", placeholder("Rentabilidad")))),
        new Section("REPORTES", List.of(
            item("Financieros", "reportes/financieros", "CHART", REPORTES, "reportes_financieros.ver", placeholder("Reportes financieros")),
            item("Ventas", "reportes/ventas", "CHART_GRID", REPORTES, "reportes_ventas.ver", placeholder("Reportes de ventas")),
            item("Compras", "reportes/compras", "CHART_GRID", REPORTES, "reportes_compras.ver", placeholder("Reportes de compras")),
            item("Inventario", "reportes/inventario", "CHART_GRID", REPORTES, "reportes_inventario.ver", placeholder("Reportes de inventario")),
            item("Fiscal", "reportes/fiscal", "CHART_GRID", REPORTES, "reportes_fiscal.ver", placeholder("Reportes fiscales")),
            item("Cuentas por cobrar", "reportes/cuentas-por-cobrar", "MONEY_DEPOSIT", REPORTES, "reportes_cuentas_cobrar.ver", placeholder("Reportes de cuentas por cobrar")),
            item("Cuentas por pagar", "reportes/cuentas-por-pagar", "MONEY_WITHDRAW", REPORTES, "reportes_cuentas_pagar.ver", placeholder("Reportes de cuentas por pagar"))))
    );

    public static Optional<Item> byRoute(String route) {
        String normalized = route == null ? "" : route.replaceFirst("^/+", "").replaceFirst("/+$", "");
        return SECTIONS.stream().flatMap(section -> section.items().stream())
            .filter(item -> normalized.equals(item.route()) || normalized.startsWith(item.route() + "/"))
            .max(java.util.Comparator.comparingInt(item -> item.route().length()));
    }

    private static Item item(String title, String route, String icon, String module,
                             String permission, String description) {
        return new Item(title, route, icon, module, permission, description);
    }

    private static String placeholder() {
        return "Esta funcionalidad estará disponible en una próxima etapa.";
    }

    private static String placeholder(String ignored) {
        return placeholder();
    }

    public record Section(String title, List<Item> items) {}
    public record Item(String title, String route, String icon, String module,
                       String permission, String description) {
        public String placeholderTitle() {
            return switch (route) {
                case "fiscal/ecf" -> "e-CF";
                case "reportes/financieros" -> "REPORTES FINANCIEROS";
                case "reportes/ventas" -> "REPORTES DE VENTAS";
                case "reportes/compras" -> "REPORTES DE COMPRAS";
                case "reportes/inventario" -> "REPORTES DE INVENTARIO";
                case "reportes/fiscal" -> "REPORTES FISCALES";
                case "reportes/cuentas-por-cobrar" -> "REPORTES DE CUENTAS POR COBRAR";
                case "reportes/cuentas-por-pagar" -> "REPORTES DE CUENTAS POR PAGAR";
                default -> title.toUpperCase(java.util.Locale.ROOT);
            };
        }
    }
}
