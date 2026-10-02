package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import com.citacloud.springboot.contacloud.app.models.MedioPagoMovimiento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record MovimientoFinancieroInput(LocalDate fecha, TipoCuentaDinero tipoCuenta, UUID cuentaId,
    String concepto, BigDecimal monto, String referencia, String descripcion, MedioPagoMovimiento medioPago) {
    public MovimientoFinancieroInput(LocalDate fecha,TipoCuentaDinero tipoCuenta,UUID cuentaId,String concepto,
            BigDecimal monto,String referencia,String descripcion){
        this(fecha,tipoCuenta,cuentaId,concepto,monto,referencia,descripcion,
            tipoCuenta==TipoCuentaDinero.CASH_REGISTER?MedioPagoMovimiento.CASH:null);
    }
}
