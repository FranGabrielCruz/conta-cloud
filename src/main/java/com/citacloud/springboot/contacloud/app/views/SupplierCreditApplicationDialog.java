package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.math.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class SupplierCreditApplicationDialog {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final SupplierCreditNoteService service;
    private final NotaCreditoProveedorDto note;
    private final Runnable afterApply;
    private final Map<UUID,BigDecimal> allocations=new LinkedHashMap<>();
    private final Map<UUID,FacturaAplicacionCreditoDto> knownInvoices=new HashMap<>();
    private final Set<UUID> invalidAmounts=new HashSet<>();
    private final Grid<FacturaAplicacionCreditoDto> grid=new Grid<>(FacturaAplicacionCreditoDto.class,false);
    private final TextField search=new TextField();
    private final Span totalValue=new Span();
    private final Span remainingValue=new Span();
    private final AppActionButton applyButton;
    private final AppPagination[] pagination=new AppPagination[1];
    private Dialog dialog;

    SupplierCreditApplicationDialog(SupplierCreditNoteService service,NotaCreditoProveedorDto note,Runnable afterApply) {
        this.service=service;this.note=note;this.afterApply=afterApply;
        this.applyButton=new AppActionButton(ActionType.APPLY_CREDIT,ButtonSize.MAIN,"Aplicar crédito",event->confirm());
        configureGrid();
    }

    void open() {
        dialog=new Dialog();dialog.setHeaderTitle("APLICAR NOTA DE CRÉDITO");dialog.setWidth("min(1180px,96vw)");dialog.setHeight("min(880px,94vh)");
        var info=new AppDetailSection("INFORMACIÓN DE LA NOTA")
            .field("Proveedor",note.proveedor()).field("Nota de crédito",note.numero())
            .field("No. nota proveedor",text(note.numeroProveedor())).field("Moneda",note.moneda())
            .field("Crédito original",currency(note.total())).field("Aplicado",currency(note.aplicado()))
            .field("CRÉDITO DISPONIBLE",currency(note.disponible()));
        search.setPlaceholder("Buscar factura...");search.setClearButtonVisible(true);search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event->{if(event.isFromClient()){pagination[0].reset();loadPage();}});
        Button automatic=new Button("Aplicar automáticamente",VaadinIcon.MAGIC.create(),event->automatic());
        Button clear=new Button("Limpiar",VaadinIcon.ERASER.create(),event->{allocations.clear();invalidAmounts.clear();refreshGridAndSummary();});
        automatic.getElement().getThemeList().add("primary");clear.getElement().getThemeList().add("tertiary");
        HorizontalLayout tools=new HorizontalLayout(search,automatic,clear);tools.setWidthFull();tools.setAlignItems(FlexComponent.Alignment.END);tools.expand(search);
        pagination[0]=new AppPagination(request->loadPage(),5,5,10,25,50);
        VerticalLayout pending=new VerticalLayout(new H3("FACTURAS PENDIENTES"),tools,grid,pagination[0]);pending.setPadding(false);pending.setSpacing(true);pending.setSizeFull();pending.expand(grid);
        dialog.add(info,pending);
        Div summary=summary();summary.getStyle().set("margin-right","auto");
        dialog.getFooter().getElement().getStyle().set("width","100%");dialog.getFooter().add(summary,applyButton,new AppActionButton(ActionType.CLOSE,ButtonSize.MAIN,"Cerrar",event->dialog.close()));
        refreshSummary();loadPage();dialog.open();
    }

    private void configureGrid() {
        grid.setWidthFull();grid.setHeight("360px");
        grid.addComponentColumn(this::selection).setHeader("").setWidth("64px").setFlexGrow(0);
        grid.addColumn(FacturaAplicacionCreditoDto::numeroInterno).setHeader("Factura").setAutoWidth(true);
        grid.addColumn(FacturaAplicacionCreditoDto::numeroProveedor).setHeader("No. proveedor").setAutoWidth(true);
        grid.addColumn(invoice->DATE.format(invoice.fecha())).setHeader("Fecha").setAutoWidth(true);
        grid.addColumn(invoice->currency(invoice.saldo())).setHeader("Saldo").setAutoWidth(true);
        grid.addComponentColumn(this::amountField).setHeader("Aplicar").setWidth("160px").setFlexGrow(0);
        grid.setEmptyStateText("No hay facturas pendientes para aplicar el crédito.");
    }

    private Checkbox selection(FacturaAplicacionCreditoDto invoice) {
        Checkbox selected=new Checkbox();selected.setValue(amount(invoice.facturaId()).signum()>0);
        selected.setAriaLabel("Seleccionar "+invoice.numeroInterno());
        selected.addValueChangeListener(event->{if(!event.isFromClient())return;if(event.getValue()){
            BigDecimal proposal=SupplierCreditAllocationCalculator.amountOnSelection(note.disponible(),allocations,invoice.facturaId(),invoice.saldo());
            if(proposal.signum()>0)allocations.put(invoice.facturaId(),proposal);else Notification.show("No queda crédito disponible para esta factura.");
        }else allocations.remove(invoice.facturaId());refreshGridAndSummary();});
        return selected;
    }

    private BigDecimalField amountField(FacturaAplicacionCreditoDto invoice) {
        invalidAmounts.remove(invoice.facturaId());
        BigDecimalField field=new BigDecimalField();field.setWidth("130px");field.setValue(amount(invoice.facturaId()));
        field.setAriaLabel("Importe para "+invoice.numeroInterno());field.getElement().setAttribute("min","0");field.getElement().setAttribute("step","0.01");
        field.addValueChangeListener(event->{if(!event.isFromClient())return;BigDecimal value=event.getValue()==null?BigDecimal.ZERO:event.getValue();
            BigDecimal allowed=SupplierCreditAllocationCalculator.amountOnSelection(note.disponible(),allocations,invoice.facturaId(),invoice.saldo());
            if(value.signum()<0){field.setInvalid(true);field.setErrorMessage("El importe no puede ser negativo.");invalidAmounts.add(invoice.facturaId());refreshSummary();}
            else if(value.compareTo(invoice.saldo())>0){field.setInvalid(true);field.setErrorMessage("Supera el saldo de la factura.");invalidAmounts.add(invoice.facturaId());refreshSummary();}
            else if(value.compareTo(allowed)>0){field.setInvalid(true);field.setErrorMessage("Supera el crédito restante.");invalidAmounts.add(invoice.facturaId());refreshSummary();}
            else{field.setInvalid(false);invalidAmounts.remove(invoice.facturaId());if(value.signum()==0)allocations.remove(invoice.facturaId());else allocations.put(invoice.facturaId(),value);refreshGridAndSummary();}
        });
        field.addBlurListener(event->{BigDecimal value=field.getValue();if(value!=null&&!field.isInvalid())field.setValue(value.setScale(2,RoundingMode.HALF_UP));});
        return field;
    }

    private void loadPage() {
        var request=pagination[0].currentRequest();var page=service.searchPendingInvoices(note.id(),search.getValue(),request.page(),request.size());
        page.forEach(invoice->knownInvoices.put(invoice.facturaId(),invoice));grid.setItems(page.getContent());pagination[0].setTotal(page.getTotalElements());
    }

    private void automatic() {
        allocations.clear();invalidAmounts.clear();service.proposeApplications(note.id()).forEach(item->allocations.put(item.facturaId(),item.monto()));refreshGridAndSummary();
    }

    private void refreshGridAndSummary(){grid.getDataProvider().refreshAll();refreshSummary();}
    private void refreshSummary(){BigDecimal total=SupplierCreditAllocationCalculator.total(allocations);totalValue.setText(currency(total));remainingValue.setText(currency(note.disponible().subtract(total)));applyButton.setEnabled(total.signum()>0&&valid());}
    private boolean valid(){BigDecimal total=SupplierCreditAllocationCalculator.total(allocations);if(!invalidAmounts.isEmpty()||total.signum()<=0||total.compareTo(note.disponible())>0)return false;return allocations.entrySet().stream().allMatch(entry->entry.getValue()!=null&&entry.getValue().signum()>0&&(!knownInvoices.containsKey(entry.getKey())||entry.getValue().compareTo(knownInvoices.get(entry.getKey()).saldo())<=0));}

    private Div summary(){Div box=new Div();box.addClassName("cc-invoice-totals");box.getStyle().set("display","grid").set("grid-template-columns","repeat(3,minmax(150px,1fr))").set("gap","24px").set("flex","1");box.add(metric("Crédito disponible inicial",currency(note.disponible())),metric("Total a aplicar",totalValue),metric("Crédito restante",remainingValue));return box;}
    private static Div metric(String label,String value){return metric(label,new Span(value));}
    private static Div metric(String label,Span value){Div div=new Div(new Span(label),value);div.getStyle().set("display","flex").set("flex-direction","column").set("gap","4px");value.getStyle().set("font-weight","700");return div;}

    private void confirm(){if(!valid())return;BigDecimal total=SupplierCreditAllocationCalculator.total(allocations);long count=allocations.size();String message="¿Desea aplicar "+currency(total)+" de la Nota de Crédito "+note.numero()+" a "+count+(count==1?" factura?":" facturas?")+"\n\nCrédito disponible: "+currency(note.disponible())+"\nTotal a aplicar: "+currency(total)+"\nCrédito restante: "+currency(note.disponible().subtract(total));ConfirmDialog confirmation=new ConfirmDialog("CONFIRMAR APLICACIÓN",message,"Confirmar",event->apply(),"Cancelar",event->{});confirmation.open();}
    private void apply(){try{var items=allocations.entrySet().stream().map(entry->new AplicarCreditoProveedorInput.Aplicacion(entry.getKey(),entry.getValue())).toList();service.apply(note.id(),new AplicarCreditoProveedorInput(items,note.version()));dialog.close();afterApply.run();Notification.show("Crédito aplicado correctamente.");}catch(RuntimeException exception){Notification.show(exception instanceof ReglaNegocioException||exception instanceof RecursoNoEncontradoException?exception.getMessage():"No fue posible aplicar el crédito. Actualiza los saldos e inténtalo nuevamente.");loadPage();}}
    private BigDecimal amount(UUID invoiceId){return allocations.getOrDefault(invoiceId,BigDecimal.ZERO);}
    private String currency(BigDecimal value){String symbol="DOP".equalsIgnoreCase(note.moneda())?"RD$":text(note.moneda());return symbol+" "+String.format(Locale.US,"%,.2f",value==null?BigDecimal.ZERO:value);}
    private static String text(String value){return value==null||value.isBlank()?"—":value;}
}
