package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.AplicacionCobroInput;
import com.citacloud.springboot.contacloud.app.models.*;import com.citacloud.springboot.contacloud.app.repositories.*;
import java.math.*;import java.util.*;
/** Shared validation for immediate sales collections: POS invoices and debit notes. */
public final class CobroContadoValidacion {
 private static final int SCALE=4;private CobroContadoValidacion(){}
    public static List<AplicacionValidada> validar(List<AplicacionCobroInput> entradas,BigDecimal total,UUID tenant,UUID empresa,UUID moneda,Set<String> permisos,MetodoPagoVentaRepository metodosPago,BankAccountRepository cuentasBancarias){
        if(entradas==null||entradas.isEmpty())throw new ReglaNegocioException("Agrega al menos un método de pago.");
        if(entradas.size()>20)throw new ReglaNegocioException("El cobro excede el máximo de 20 aplicaciones.");
        List<AplicacionValidada> resultado=new ArrayList<>();int efectivo=0;
        for(AplicacionCobroInput entrada:entradas){
            if(entrada==null||entrada.metodoPagoId()==null)throw new ReglaNegocioException("Selecciona un método de pago válido.");
            MetodoPagoVenta metodo=metodosPago.bloquearActivo(entrada.metodoPagoId(),tenant,empresa)
                .orElseThrow(()->new ReglaNegocioException("El método de pago ya no está disponible."));
            if(metodo.getType()==TipoMetodoPagoVenta.BANK_TRANSFER&&!permisos.contains("facturas.cobrar_transferencia"))
                throw new ReglaNegocioException("No tienes permiso para cobrar mediante transferencia.");
            if(metodo.getType()==TipoMetodoPagoVenta.CHECK&&!permisos.contains("facturas.cobrar_cheque"))
                throw new ReglaNegocioException("No tienes permiso para cobrar mediante cheque.");
            BigDecimal monto=escala(entrada.monto());
            if(monto.signum()<=0)throw new ReglaNegocioException("Todos los montos aplicados deben ser mayores que cero.");
            String referencia=texto(entrada.referencia(),100);String banco=texto(entrada.banco(),120);
            if(metodo.isRequiresReference()&&referencia==null)throw new ReglaNegocioException("Indica la referencia de "+metodo.getName()+".");
            CuentaBancaria cuenta=null;
            if(entrada.cuentaBancariaId()!=null)cuenta=cuentasBancarias.findByIdAndTenantIdAndEmpresaId(entrada.cuentaBancariaId(),tenant,empresa)
                .filter(CuentaBancaria::isActivo).orElseThrow(()->new ReglaNegocioException("La cuenta bancaria ya no está disponible."));
            if(cuenta==null&&metodo.getFinancialBankAccountId()!=null)cuenta=cuentasBancarias.findByIdAndTenantIdAndEmpresaId(
                metodo.getFinancialBankAccountId(),tenant,empresa).filter(CuentaBancaria::isActivo)
                .orElseThrow(()->new ReglaNegocioException("La cuenta financiera configurada para "+metodo.getName()+" ya no está disponible."));
            if(metodo.isRequiresBankAccount()&&cuenta==null)throw new ReglaNegocioException("Selecciona la cuenta destino.");
            if(cuenta!=null&&!moneda.equals(cuenta.getMonedaId()))throw new ReglaNegocioException("La cuenta destino utiliza una moneda distinta a la venta.");
            BigDecimal recibido=null,cambio=BigDecimal.ZERO.setScale(SCALE);
            if(metodo.isCash()){
                if(++efectivo>1)throw new ReglaNegocioException("El efectivo debe registrarse en una sola aplicación.");
                recibido=escala(entrada.recibido()==null?monto:entrada.recibido());
                if(recibido.compareTo(monto)<0)throw new ReglaNegocioException("El efectivo recibido no puede ser menor que el monto aplicado.");
                cambio=CobroVentaCalculo.cambio(monto,recibido);
            }else if(entrada.recibido()!=null&&escala(entrada.recibido()).compareTo(monto)!=0){
                throw new ReglaNegocioException("Solo el efectivo puede generar cambio.");
            }
            resultado.add(new AplicacionValidada(metodo,monto,recibido,cambio,cuenta,referencia,banco));
        }
        BigDecimal aplicado=CobroVentaCalculo.totalAplicado(resultado.stream().map(AplicacionValidada::monto).toList());
        int comparacion=aplicado.compareTo(total.setScale(SCALE,RoundingMode.HALF_UP));
        if(comparacion<0)throw new ReglaNegocioException("El cobro está incompleto. Queda un saldo pendiente de "+total.subtract(aplicado).setScale(2,RoundingMode.HALF_UP)+".");
        if(comparacion>0)throw new ReglaNegocioException("El total aplicado no puede superar el total de la venta.");
        return resultado;
    }


 public record AplicacionValidada(MetodoPagoVenta metodo,BigDecimal monto,BigDecimal recibido,BigDecimal cambio,CuentaBancaria cuenta,String referencia,String banco){}
 private static BigDecimal escala(BigDecimal x){return (x==null?BigDecimal.ZERO:x).setScale(SCALE,RoundingMode.HALF_UP);}
 private static String texto(String x,int max){String s=x==null?"":x.trim();if(s.length()>max)throw new ReglaNegocioException("El dato ingresado excede el máximo permitido.");return s.isEmpty()?null:s;}
}
