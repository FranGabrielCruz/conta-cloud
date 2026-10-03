package com.citacloud.springboot.contacloud.app.services;

import java.util.Locale;
import java.util.Optional;

public final class PermisoModuloPolicy {
    private PermisoModuloPolicy() {}

    public static Optional<String> moduloRequerido(String codigo, String recurso) {
        String clave = normalizar(recurso);
        if (clave.isEmpty()) clave = recursoDesdeCodigo(codigo);
        return Optional.ofNullable(switch (clave) {
            case "empresa" -> "CONFIGURACION";
            case "usuario", "usuarios" -> "USUARIOS";
            case "rol", "roles" -> "ROLES";
            case "sucursal", "sucursales" -> "CONFIGURACION";
            case "moneda", "monedas" -> "CONFIGURACION";
            case "tasa_cambio", "tasas_cambio" -> "TASAS_CAMBIO";
            case "impuesto", "impuestos" -> "IMPUESTOS";
            case "comprobante", "comprobantes", "comprobantes_fiscales" -> "COMPROBANTES_FISCALES";
            case "secuencia", "secuencias" -> "SECUENCIAS";
            case "condicion_pago", "condiciones_pago" -> "CONDICIONES_PAGO";
            case "periodo", "periodos", "periodos_fiscales" -> "CONFIGURACION_CONTABLE";
            case "configuracion_contable" -> "CONFIGURACION_CONTABLE";
            case "clientes", "cotizaciones", "facturas", "notas_credito", "notas_debito",
                 "recibos", "cuentas_cobrar" -> "VENTAS";
            case "proveedores", "ordenes_compra", "facturas_proveedores",
                 "notas_credito_proveedores", "pagos_proveedores", "cuentas_pagar" -> "COMPRAS";
            case "cajas", "operaciones_caja", "cuentas_bancarias", "ingresos", "egresos", "transferencias",
                 "conciliacion_bancaria" -> "CAJA_BANCOS";
            case "productos", "categorias", "unidades_medida", "almacenes", "inventario_movimientos",
                 "inventario_transferencias", "inventario_ajustes", "kardex", "conteo_fisico" -> "INVENTARIO";
            case "catalogo_cuentas", "asientos", "libro_diario", "libro_mayor", "balanza",
                 "centros_costos", "cierres_contables" -> "CONTABILIDAD";
            case "fiscal_ncf", "fiscal_ecf", "fiscal_impuestos", "fiscal_retenciones", "fiscal_606",
                 "fiscal_607", "fiscal_608", "fiscal_609", "fiscal_it1", "fiscal_ir17" -> "FISCAL";
            case "activos_fijos", "depreciaciones", "mantenimiento_activos", "bajas_activos" -> "ACTIVOS_FIJOS";
            case "presupuestos", "flujo_efectivo", "proyecciones", "rentabilidad" -> "FINANZAS";
            case "reportes_financieros", "reportes_ventas", "reportes_compras", "reportes_inventario",
                 "reportes_fiscal", "reportes_cuentas_cobrar", "reportes_cuentas_pagar" -> "REPORTES";
            default -> null;
        });
    }

    private static String recursoDesdeCodigo(String codigo) {
        String valor = normalizar(codigo);
        if (valor.startsWith("configuracion_contable_")) return "configuracion_contable";
        if (valor.startsWith("condicion_pago_")) return "condicion_pago";
        if (valor.startsWith("tasa_cambio_")) return "tasa_cambio";
        int separador = valor.indexOf('_');
        return separador < 0 ? valor : valor.substring(0, separador);
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }
}
