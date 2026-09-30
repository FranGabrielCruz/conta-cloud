package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.models.TipoMovimientoFinanciero;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
@Route(value="ingresos",layout=MainLayout.class) @PageTitle("Ingresos | ContaCloud") @PermitAll
public class IngresosView extends FinancialMovementsView {
    public IngresosView(FinancialMovementService service,EmpresaModuloService modulos){super(service,modulos,TipoMovimientoFinanciero.INCOME);}
}
