package com.citacloud.springboot.contacloud.app.views;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
@Route(value="configuracion/facturacion/impresion",layout=MainLayout.class) @PageTitle("Impresión de Facturación | ContaCloud") @PermitAll
public class ConfiguracionImpresionFacturacionView extends VerticalLayout implements BeforeEnterObserver {
 private final BillingPrintService service; private final Checkbox mostrar=new Checkbox("Mostrar PDF al imprimir");
 public ConfiguracionImpresionFacturacionView(BillingPrintService service){this.service=service;addClassName("cc-page");add(new AppPageHeader("IMPRESIÓN DE FACTURACIÓN","Configura la visualización automática. Ver PDF continúa disponible manualmente."),mostrar,new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",ev->{try{service.configurar(mostrar.getValue());Notification.show("Configuración guardada.");}catch(RuntimeException ex){Notification.show("No fue posible guardar la configuración.");}}));}
 public void beforeEnter(BeforeEnterEvent e){if(!TenantContext.principalActual().permisos().contains("facturas.configurar_impresion")){e.rerouteTo(DashboardView.class);return;}mostrar.setValue(service.mostrarPdf());}
}
