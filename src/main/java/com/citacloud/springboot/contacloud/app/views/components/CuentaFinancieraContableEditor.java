package com.citacloud.springboot.contacloud.app.views.components;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Cuenta;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import com.citacloud.springboot.contacloud.app.services.CuentaFinancieraContableService;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import java.util.UUID;
public final class CuentaFinancieraContableEditor {
 private CuentaFinancieraContableEditor() {}
 public static AppActionButton boton(CuentaFinancieraContableService servicio,TipoCuentaDinero tipo,UUID id) {
  return new AppActionButton(ActionType.CONFIGURE,ButtonSize.MAIN,"Configurar cuenta contable",e->{
   try {
    Cuenta anterior=servicio.obtener(tipo,id);
    var dialogo=new Dialog();dialogo.setHeaderTitle("Cuenta contable del recurso financiero");dialogo.setWidth("min(560px,96vw)");
    var cuenta=new ComboBox<Cuenta>("Cuenta contable");cuenta.setWidthFull();cuenta.setClearButtonVisible(true);
    cuenta.setItemLabelGenerator(Cuenta::etiqueta);
    cuenta.setItems(q->servicio.buscar(tipo,q.getFilter().orElse(""),q.getOffset(),q.getLimit()).stream());
    cuenta.setValue(anterior);cuenta.setHelperText("Selecciona una cuenta de activo, activa y de movimiento. La asociación se aplica a operaciones futuras.");
    dialogo.add(cuenta);
    dialogo.getFooter().add(new AppActionButton(ActionType.SAVE,ButtonSize.MAIN,"Guardar",ev->{
     try {servicio.guardar(tipo,id,anterior==null?null:anterior.id(),cuenta.getValue()==null?null:cuenta.getValue().id());
      dialogo.close();Notification.show("Cuenta contable asociada.");}
     catch(RuntimeException ex){error(ex);}
    }),new AppActionButton(ActionType.CANCEL,ButtonSize.MAIN,"Cancelar",ev->dialogo.close()));
    dialogo.open();
   }catch(RuntimeException ex){error(ex);}
  });
 }
 private static void error(RuntimeException ex) {
  com.citacloud.springboot.contacloud.app.errors.UiErrorHandler.show(ex,"No fue posible configurar la cuenta contable del recurso.");
 }
}

