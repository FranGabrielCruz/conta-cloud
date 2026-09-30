package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.models.TipoMovimientoFinanciero;
import com.citacloud.springboot.contacloud.app.services.*;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
@Route(value="egresos",layout=MainLayout.class) @PageTitle("Egresos | ContaCloud") @PermitAll
public class EgresosView extends FinancialMovementsView {
    public EgresosView(FinancialMovementService service,EmpresaModuloService modulos){super(service,modulos,TipoMovimientoFinanciero.EXPENSE);}
}
