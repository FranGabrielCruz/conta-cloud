package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import static com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo.*;
import java.util.*;

/** Una clave identifica una función compartida, nunca una cuenta por su código comercial. */
public final class FuncionesContables {
 private FuncionesContables() {}
 private static Funcion f(String clave,String nombre,Set<Modulo> modulos,boolean obligatoria,String... clases) {
  return new Funcion(clave,nombre,modulos,obligatoria?modulos:Set.of(),Set.of(clases));
 }
 public static final List<Funcion> TODAS=List.of(
  f("ACCOUNTS_RECEIVABLE_DEFAULT","Cuentas por cobrar",Set.of(VENTAS,CXC),true,"ACTIVO"),
  f("SALES_REVENUE_DEFAULT","Ingresos por ventas",Set.of(VENTAS),true,"INGRESOS"),
  f("SALES_TAX_PAYABLE","Impuesto por pagar",Set.of(VENTAS),false,"PASIVO"),
  f("SALES_DISCOUNTS","Descuentos sobre ventas",Set.of(VENTAS),false,"INGRESOS","GASTOS"),
  f("ACCOUNTS_PAYABLE_DEFAULT","Cuentas por pagar",Set.of(COMPRAS,CXP),true,"PASIVO"),
  f("PURCHASE_EXPENSE","Compras / gastos",Set.of(COMPRAS),true,"GASTOS"),
  new Funcion("INVENTORY_DEFAULT","Inventario",Set.of(COMPRAS,INVENTARIO,VENTAS),Set.of(INVENTARIO),Set.of("ACTIVO")),
  f("RECOVERABLE_TAX","Impuesto recuperable",Set.of(COMPRAS),false,"ACTIVO"),
  f("PURCHASE_DISCOUNTS","Descuentos sobre compras",Set.of(COMPRAS),false,"COSTOS","GASTOS","INGRESOS"),
  new Funcion("COGS_DEFAULT","Costo de ventas",Set.of(INVENTARIO,VENTAS),Set.of(INVENTARIO),Set.of("COSTOS")),
  f("INVENTORY_GAIN","Ajuste positivo de inventario",Set.of(INVENTARIO),false,"INGRESOS"),
  f("INVENTORY_LOSS","Ajuste negativo de inventario",Set.of(INVENTARIO),false,"COSTOS","GASTOS"),
  f("CUSTOMER_ADVANCES","Anticipos de clientes",Set.of(CXC),false,"PASIVO"),
  f("RECEIVABLE_ADJUSTMENT","Diferencias de cobros",Set.of(CXC),false,"GASTOS","INGRESOS"),
  f("SUPPLIER_ADVANCES","Anticipos a proveedores",Set.of(CXP),false,"ACTIVO"),
  f("PAYABLE_ADJUSTMENT","Diferencias de pagos",Set.of(CXP),false,"GASTOS","INGRESOS"),
  f("DEPRECIATION_EXPENSE_DEFAULT","Gasto por depreciación",Set.of(ACTIVOS_FIJOS),true,"GASTOS"),
  f("ACCUMULATED_DEPRECIATION","Depreciación acumulada",Set.of(ACTIVOS_FIJOS),true,"ACTIVO"),
  f("GAIN_ASSET_DISPOSAL_DEFAULT","Ganancia en baja de activos",Set.of(ACTIVOS_FIJOS),true,"INGRESOS"),
  f("LOSS_ASSET_DISPOSAL_DEFAULT","Pérdida en baja de activos",Set.of(ACTIVOS_FIJOS),true,"GASTOS"),
  f("FISCAL_RESULT_DEFAULT","Resultado del ejercicio",Set.of(CIERRE),true,"PATRIMONIO"),
  f("RETAINED_EARNINGS","Resultados acumulados",Set.of(CIERRE),false,"PATRIMONIO")
 );
 public static Funcion funcion(String clave) {
  return TODAS.stream().filter(f->f.clave().equals(clave)).findFirst()
   .orElseThrow(()->new ReglaNegocioException("La función contable no es válida."));
 }
 public static boolean compatible(Funcion f,Cuenta c) {
  return c!=null && c.activa() && "MOVIMIENTO".equals(c.tipo()) && f.clasificaciones().contains(c.clasificacion())
   && (!f.clave().equals("FISCAL_RESULT_DEFAULT") || c.resultado() && !"OBLIGATORIO".equals(c.centroCosto()));
 }
 public static List<EstadoModulo> estados(Map<String,Cuenta> cuentas,Map<Modulo,Boolean> flags,boolean global) {
  return Arrays.stream(Modulo.values()).map(m->{
   var faltan=TODAS.stream().filter(f->f.modulos().contains(m) &&
     f.obligatoria().contains(m) && !compatible(f,cuentas.get(f.clave())))
    .map(Funcion::nombre).toList();
   boolean automatico=m!=CIERRE && Boolean.TRUE.equals(flags.get(m));
   return new EstadoModulo(m,faltan.isEmpty(),automatico,global&&automatico&&faltan.isEmpty(),faltan);
  }).toList();
 }
}

