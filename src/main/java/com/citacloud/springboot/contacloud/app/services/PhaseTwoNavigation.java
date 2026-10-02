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
                "conciliacion_bancaria.ver", "Administra la conciliación de las cuentas bancarias.")))
    );

    public static Optional<Item> byRoute(String route) {
        String normalized = route == null ? "" : route.replaceFirst("^/+", "").split("/", 2)[0];
        return SECTIONS.stream().flatMap(section -> section.items().stream())
            .filter(item -> item.route().equals(normalized)).findFirst();
    }

    private static Item item(String title, String route, String icon, String module,
                             String permission, String description) {
        return new Item(title, route, icon, module, permission, description);
    }

    public record Section(String title, List<Item> items) {}
    public record Item(String title, String route, String icon, String module,
                       String permission, String description) {}
}
