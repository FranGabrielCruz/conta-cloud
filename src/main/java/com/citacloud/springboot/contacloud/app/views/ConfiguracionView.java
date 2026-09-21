package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import com.citacloud.springboot.contacloud.app.views.components.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Set;

@Route(value = "configuracion", layout = MainLayout.class)
@PageTitle("Configuración | ContaCloud") @PermitAll
public class ConfiguracionView extends VerticalLayout {
    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionView.class);
    private final CompanyConfigurationService companyConfiguration;
    private final CompanyCurrencyService currencies;
    private final BranchService branches;
    private final Set<String> permissions = TenantContext.principalActual().permisos();
    private final AppGrid<SucursalDto> branchGrid = new AppGrid<>(SucursalDto.class);
    private final TextField branchSearch = searchField("Buscar sucursal...");
    private final ComboBox<String> branchStatus = statusFilter();
    private AppPagination branchPagination;
    private final AppGrid<MonedaDto> currencyGrid = new AppGrid<>(MonedaDto.class);
    private final TextField currencySearch = searchField("Buscar moneda...");
    private final ComboBox<String> currencyStatus = statusFilter();
    private AppPagination currencyPagination;
    private ComboBox<MonedaDto> baseCurrency;

    public ConfiguracionView(CompanyConfigurationService companyConfiguration, CompanyCurrencyService currencies,
                             BranchService branches) {
        this.companyConfiguration = companyConfiguration; this.currencies = currencies;
        this.branches = branches;
        addClassName("cc-page"); setPadding(false); setSpacing(false); setWidthFull();
        add(new AppPageHeader("CONFIGURACIÓN",
            "Configura los datos generales, sucursales y monedas utilizadas por la empresa."));
        if (has("EMPRESA_VER")) add(companySection());
        if (has("SUCURSAL_VER")) add(branchSection());
        if (has("MONEDA_VER")) add(currencySection());
        if (!has("EMPRESA_VER") && !has("SUCURSAL_VER") && !has("MONEDA_VER"))
            add(new Paragraph("No hay opciones disponibles para tus permisos actuales."));
    }

    private Component companySection() {
        ConfiguracionEmpresaDto configuration = companyConfiguration.getConfiguration();
        boolean canEdit = has("EMPRESA_EDITAR");
        var commercialName = new TextField("Nombre comercial"); commercialName.setRequired(true); commercialName.setMaxLength(150);
        var legalName = new TextField("Razón social");
        var rnc = new TextField("RNC / Identificación"); rnc.setPlaceholder("000-00000-0");
        rnc.setPattern("(?:\\d{9}|\\d{3}-\\d{5}-\\d)?"); rnc.setErrorMessage("Introduzca un RNC válido.");
        var phone = new TextField("Teléfono principal"); phone.setPlaceholder("(000) 000-0000");
        phone.setPattern("(?:\\d{10}|\\(\\d{3}\\) \\d{3}-\\d{4})?"); phone.setErrorMessage("Introduzca un teléfono válido.");
        var email = new EmailField("Correo electrónico"); email.setErrorMessage("Introduzca un correo electrónico válido.");
        baseCurrency = new ComboBox<>("Moneda base"); baseCurrency.setRequired(true);
        baseCurrency.setItemLabelGenerator(m -> m.codigoIso() + " - " + m.nombre());
        var address = new TextArea("Dirección"); address.addClassName("cc-config-span-2");
        commercialName.setValue(orEmpty(configuration.nombreComercial())); legalName.setValue(orEmpty(configuration.razonSocial()));
        rnc.setValue(formatRnc(configuration.rnc())); phone.setValue(formatPhone(configuration.telefono()));
        email.setValue(orEmpty(configuration.correo())); address.setValue(orEmpty(configuration.direccion()));
        refreshBaseCurrencies(configuration.monedaBaseId());
        commercialName.setReadOnly(!canEdit); legalName.setReadOnly(!canEdit); rnc.setReadOnly(!canEdit);
        phone.setReadOnly(!canEdit); email.setReadOnly(!canEdit); baseCurrency.setReadOnly(!canEdit); address.setReadOnly(!canEdit);
        var form = new Div(commercialName, legalName, rnc, phone, email, baseCurrency, address);
        form.addClassName("cc-config-form");
        var save = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, event -> {
            event.getSource().setEnabled(false);
            try {
                var saved = companyConfiguration.updateConfiguration(new ConfiguracionEmpresaDto(
                    commercialName.getValue(), legalName.getValue(), rnc.getValue(), phone.getValue(), email.getValue(),
                    baseCurrency.getValue() == null ? null : baseCurrency.getValue().id(), address.getValue()));
                commercialName.setValue(saved.nombreComercial()); rnc.setValue(formatRnc(saved.rnc()));
                phone.setValue(formatPhone(saved.telefono())); activeLayout().ifPresent(MainLayout::refreshCompanyContext);
                Notification.show("Configuración actualizada correctamente.");
            } catch (ReglaNegocioException ex) { Notification.show(ex.getMessage()); }
            catch (RuntimeException ex) { Notification.show("No fue posible actualizar la configuración."); }
            finally { event.getSource().setEnabled(true); }
        });
        save.setVisible(canEdit);
        var actions = new HorizontalLayout(save); actions.addClassName("cc-config-save");
        return section("DATOS DE LA EMPRESA", null, form, logoEditor(configuration.nombreComercial()), actions);
    }

    private Component logoEditor(String companyName) {
        String initial = companyName == null || companyName.isBlank() ? "C" : companyName.substring(0, 1).toUpperCase();
        boolean available = companyConfiguration.currentLogoAvailable();
        var preview = new CompanyLogoAvatar(initial, true); preview.refresh(available);
        var row = new HorizontalLayout(preview); row.setAlignItems(Alignment.CENTER); row.addClassName("cc-logo-editor");
        if (has("EMPRESA_EDITAR")) {
            var buffer = new MemoryBuffer(); var upload = new Upload(buffer);
            upload.setMaxFiles(1); upload.setMaxFileSize(CompanyConfigurationService.MAX_LOGO_BYTES);
            upload.setAcceptedFileTypes("image/png", "image/jpeg", "image/webp", ".png", ".jpg", ".jpeg", ".webp");
            var uploadButton = new com.vaadin.flow.component.button.Button(available ? "Reemplazar logo" : "Subir logo");
            upload.setUploadButton(uploadButton); upload.setDropLabel(new Div("PNG, JPG o WEBP · máximo 2 MB"));
            upload.addSucceededListener(event -> {
                upload.setVisible(false);
                try {
                    companyConfiguration.replaceCompanyLogo(buffer.getInputStream(), event.getFileName(), event.getMIMEType());
                    preview.refresh(true); uploadButton.setText("Reemplazar logo");
                    activeLayout().ifPresent(MainLayout::refreshCompanyLogo); Notification.show("Logo actualizado correctamente.");
                } catch (IllegalArgumentException ex) { Notification.show(ex.getMessage()); }
                catch (RuntimeException ex) { Notification.show("No fue posible guardar el logo."); }
                finally { upload.clearFileList(); upload.setVisible(true); }
            });
            upload.addFileRejectedListener(event -> Notification.show("Formato inválido o archivo mayor de 2 MB."));
            row.add(upload);
        }
        var wrapper = new VerticalLayout(new Span("Logo de la empresa"), row);
        wrapper.addClassName("cc-logo-field"); wrapper.setPadding(false); wrapper.setSpacing(false); return wrapper;
    }

    private Component branchSection() {
        branchGrid.addColumn(SucursalDto::nombre).setHeader("Nombre").setAutoWidth(true).setFlexGrow(1);
        branchGrid.addColumn(s -> orEmpty(s.direccion())).setHeader("Dirección").setAutoWidth(true).setFlexGrow(2);
        branchGrid.addColumn(s -> formatPhone(s.telefono())).setHeader("Teléfono").setAutoWidth(true);
        branchGrid.addColumn(s -> s.activo() ? "Activa" : "Inactiva").setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        branchGrid.addComponentColumn(this::branchActions).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        branchGrid.setEmptyStateText("No hay sucursales registradas.");
        branchPagination = new AppPagination(request -> loadBranches());
        branchSearch.addValueChangeListener(event -> { branchPagination.reset(); reloadBranches(); });
        branchStatus.addValueChangeListener(event -> { branchPagination.reset(); reloadBranches(); });
        var add = has("SUCURSAL_CREAR") ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nueva sucursal", e -> branchDialog(null)) : null;
        reloadBranches();
        return section("SUCURSALES", "Administra las sucursales de la empresa.", toolbar(branchSearch, branchStatus, add), branchGrid, branchPagination);
    }

    private Component branchActions(SucursalDto dto) {
        var actions = gridActions();
        actions.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION, "Ver sucursal", e -> viewBranch(dto)));
        if (has("SUCURSAL_EDITAR")) actions.add(new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION, "Editar sucursal", e -> branchDialog(dto)));
        return actions;
    }

    private boolean loadBranches() {
        if (branchPagination == null) return false;
        try {
            var request = branchPagination.currentRequest();
            var page = branches.search(branchSearch.getValue(), statusValue(branchStatus), PageRequest.of(request.page(), request.size(), Sort.by("nombre")));
            branchGrid.setItems(page.getContent()); branchPagination.setTotal(page.getTotalElements());
            return true;
        } catch (RuntimeException ex) {
            LOG.error("No fue posible cargar las sucursales del tenant autenticado", ex);
            return false;
        }
    }

    private void reloadBranches() {
        if (!loadBranches()) Notification.show("No fue posible cargar las sucursales.");
    }

    private void branchDialog(SucursalDto current) {
        var dialog = new Dialog(); dialog.setHeaderTitle(current == null ? "Nueva sucursal" : "Editar sucursal");
        var name = new TextField("Nombre"); name.setRequired(true); var phone = new TextField("Teléfono");
        phone.setPlaceholder("(000) 000-0000"); var address = new TextArea("Dirección");
        if (current != null) { name.setValue(current.nombre()); phone.setValue(formatPhone(current.telefono())); address.setValue(orEmpty(current.direccion())); }
        address.addClassName("cc-dialog-span-2");
        var form = new Div(name, phone, address); form.addClassName("cc-dialog-form"); dialog.add(form);
        var cancel = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, e -> dialog.close());
        var save = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, e -> {
            try {
                var dto = new SucursalDto(current == null ? null : current.id(), name.getValue(), address.getValue(),
                    CompanyConfigurationService.normalizePhone(phone.getValue()), current != null && current.principal(), current == null || current.activo());
                if (current == null) branches.create(dto); else branches.update(current.id(), dto);
                dialog.close();
                Notification.show(loadBranches() ? "Sucursal guardada correctamente."
                    : "Sucursal guardada, pero no fue posible actualizar la lista.");
            } catch (ReglaNegocioException ex) { Notification.show(ex.getMessage()); }
            catch (RuntimeException ex) { Notification.show("No fue posible guardar la sucursal."); }
        });
        dialog.getFooter().add(save);
        if (current != null && current.activo() && !current.principal() && has("SUCURSAL_DESACTIVAR"))
            dialog.getFooter().add(new AppActionButton(ActionType.DEACTIVATE, ButtonSize.MAIN, "Desactivar sucursal", e -> {
                try {
                    branches.disable(current.id()); dialog.close();
                    Notification.show(loadBranches() ? "Sucursal desactivada."
                        : "Sucursal desactivada, pero no fue posible actualizar la lista.");
                }
                catch (ReglaNegocioException ex) { Notification.show(ex.getMessage()); }
            }));
        dialog.getFooter().add(cancel);
        dialog.open();
    }

    private void viewBranch(SucursalDto dto) {
        var dialog = new Dialog(); dialog.setHeaderTitle("Detalle de sucursal");
        dialog.add(new AppDetailSection("Información general").field("Nombre", dto.nombre()).field("Estado", dto.activo() ? "Activa" : "Inactiva")
            .field("Teléfono", formatPhone(dto.telefono())).field("Dirección", dto.direccion()));
        dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN, e -> dialog.close())); dialog.open();
    }

    private Component currencySection() {
        currencyGrid.addColumn(MonedaDto::codigoIso).setHeader("Código").setAutoWidth(true).setFlexGrow(0);
        currencyGrid.addColumn(MonedaDto::nombre).setHeader("Nombre").setAutoWidth(true).setFlexGrow(1);
        currencyGrid.addColumn(MonedaDto::simbolo).setHeader("Símbolo").setAutoWidth(true).setFlexGrow(0);
        currencyGrid.addColumn(m -> m.activo() ? "Activa" : "Inactiva").setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        currencyGrid.addComponentColumn(this::currencyActions).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);
        currencyGrid.setEmptyStateText("No hay monedas registradas.");
        currencyPagination = new AppPagination(request -> loadCurrencies());
        currencySearch.addValueChangeListener(event -> { currencyPagination.reset(); loadCurrencies(); });
        currencyStatus.addValueChangeListener(event -> { currencyPagination.reset(); loadCurrencies(); });
        var add = has("MONEDA_CREAR") ? new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nueva moneda", e -> currencyCreateDialog()) : null;
        loadCurrencies();
        return section("MONEDAS", "Administra las monedas disponibles para las operaciones de la empresa.", toolbar(currencySearch, currencyStatus, add), currencyGrid, currencyPagination);
    }

    private Component currencyActions(MonedaDto dto) {
        var actions = gridActions();
        actions.add(new AppActionButton(ActionType.VIEW, ButtonSize.GRID_ACTION, "Ver moneda", e -> viewCurrency(dto)));
        if (has("MONEDA_EDITAR")) actions.add(new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION, "Editar moneda", e -> currencyEditDialog(dto)));
        return actions;
    }

    private void loadCurrencies() {
        if (currencyPagination == null) return;
        try {
            var request = currencyPagination.currentRequest();
            var page = currencies.search(currencySearch.getValue(), statusValue(currencyStatus), PageRequest.of(request.page(), request.size(), Sort.by("codigoIso")));
            currencyGrid.setItems(page.getContent()); currencyPagination.setTotal(page.getTotalElements());
        } catch (RuntimeException ex) {
            LOG.error("No fue posible cargar las monedas del tenant autenticado", ex);
            Notification.show("No fue posible cargar las monedas.");
        }
    }

    private void currencyCreateDialog() {
        var dialog = new Dialog(); dialog.setHeaderTitle("Nueva moneda");
        var code = new TextField("Código"); code.setRequired(true); code.setMaxLength(3);
        code.setPattern("[A-Za-z]{3}"); code.setPlaceholder("DOP, USD o EUR");
        code.setHelperText("Ejemplos: DOP, USD, EUR");
        code.setErrorMessage("Escribe un código de tres letras, por ejemplo DOP, USD o EUR.");
        var name = new TextField("Nombre"); name.setRequired(true); name.setMaxLength(80);
        var symbol = new TextField("Símbolo"); symbol.setRequired(true); symbol.setMaxLength(10);
        var decimals = decimalField();
        var form = new Div(code, name, symbol, decimals); form.addClassName("cc-dialog-form"); dialog.add(form);
        var save = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, e -> {
            try {
                currencies.create(new NuevaMonedaDto(code.getValue(), name.getValue(), symbol.getValue(), decimals.getValue().shortValue()));
                dialog.close();
                refreshBaseCurrencies(baseCurrency.getValue() == null ? null : baseCurrency.getValue().id()); loadCurrencies();
                Notification.show("Moneda creada correctamente.");
            } catch (ReglaNegocioException ex) { Notification.show(ex.getMessage()); }
            catch (RuntimeException ex) { Notification.show("No fue posible crear la moneda."); }
        });
        var cancel = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, e -> dialog.close());
        dialog.getFooter().add(save, cancel); dialog.open();
    }

    private void currencyEditDialog(MonedaDto current) {
        var dialog = new Dialog(); dialog.setHeaderTitle("Editar moneda");
        var code = new TextField("Código"); code.setValue(current.codigoIso()); code.setReadOnly(true);
        var name = new TextField("Nombre"); name.setValue(current.nombre()); var symbol = new TextField("Símbolo"); symbol.setValue(current.simbolo());
        var decimals = decimalField(); decimals.setValue((int) current.decimales());
        var form = new Div(code, name, symbol, decimals); form.addClassName("cc-dialog-form"); dialog.add(form);
        var save = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, e -> {
            try {
                currencies.update(current.id(), new MonedaDto(current.id(), current.codigoIso(), name.getValue(), symbol.getValue(), decimals.getValue().shortValue(), current.monedaBase(), current.activo()));
                dialog.close(); loadCurrencies(); refreshBaseCurrencies(baseCurrency.getValue().id()); Notification.show("Moneda actualizada correctamente.");
            } catch (ReglaNegocioException ex) { Notification.show(ex.getMessage()); }
        });
        var cancel = new AppActionButton(ActionType.CANCEL, ButtonSize.MAIN, e -> dialog.close());
        dialog.getFooter().add(save);
        if (current.activo() && has("MONEDA_DESACTIVAR")) dialog.getFooter().add(new AppActionButton(ActionType.DEACTIVATE, ButtonSize.MAIN, "Desactivar moneda", e -> {
            try { currencies.disable(current.id()); dialog.close(); loadCurrencies(); Notification.show("Moneda desactivada."); }
            catch (ReglaNegocioException ex) { Notification.show(ex.getMessage()); }
        }));
        dialog.getFooter().add(cancel);
        dialog.open();
    }

    private void viewCurrency(MonedaDto dto) {
        var dialog = new Dialog(); dialog.setHeaderTitle("Detalle de moneda");
        dialog.add(new AppDetailSection("Información general").field("Código", dto.codigoIso()).field("Estado", dto.activo() ? "Activa" : "Inactiva")
            .field("Nombre", dto.nombre()).field("Símbolo", dto.simbolo()));
        dialog.getFooter().add(new AppActionButton(ActionType.CLOSE, ButtonSize.MAIN, e -> dialog.close())); dialog.open();
    }

    private void refreshBaseCurrencies(java.util.UUID selectedId) {
        if (baseCurrency == null) return;
        var items = currencies.activeCurrencies(); baseCurrency.setItems(items);
        items.stream().filter(m -> m.id().equals(selectedId)).findFirst().ifPresent(baseCurrency::setValue);
    }

    private Component section(String title, String description, Component... content) {
        var section = new VerticalLayout(); section.addClassName("cc-config-section"); section.setPadding(false); section.setSpacing(false); section.setWidthFull();
        var heading = new H2(title); heading.addClassName("cc-config-section-title"); section.add(heading);
        if (description != null) { var text = new Paragraph(description); text.addClassName("cc-subtitle"); section.add(text); }
        section.add(content); return section;
    }
    private HorizontalLayout toolbar(Component search, Component status, Component action) {
        var toolbar = new HorizontalLayout(search, status); toolbar.addClassName("cc-config-toolbar"); toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.END); toolbar.expand(search); if (action != null) toolbar.add(action); return toolbar;
    }
    private static HorizontalLayout gridActions() {
        var actions = new HorizontalLayout(); actions.addClassName("cc-grid-actions"); actions.setPadding(false); actions.setSpacing(false); return actions;
    }
    private static TextField searchField(String placeholder) {
        var field = new TextField(); field.setPlaceholder(placeholder); field.setClearButtonVisible(true);
        field.setValueChangeMode(ValueChangeMode.LAZY); field.setValueChangeTimeout(350); return field;
    }
    private static ComboBox<String> statusFilter() {
        var filter = new ComboBox<String>("Estado"); filter.setItems("Todos", "Activas", "Inactivas"); filter.setValue("Todos"); filter.setWidth("160px"); return filter;
    }
    private static IntegerField decimalField() {
        var field = new IntegerField("Decimales"); field.setMin(0); field.setMax(6); field.setStepButtonsVisible(true); field.setValue(2);
        return field;
    }
    static Boolean statusValue(ComboBox<String> filter) {
        if ("Activas".equals(filter.getValue())) return Boolean.TRUE;
        if ("Inactivas".equals(filter.getValue())) return Boolean.FALSE;
        return null;
    }
    private boolean has(String permission) { return permissions.contains(permission); }
    private java.util.Optional<MainLayout> activeLayout() {
        return UI.getCurrent().getActiveRouterTargetsChain().stream().filter(MainLayout.class::isInstance).map(MainLayout.class::cast).findFirst();
    }
    private static String orEmpty(String value) { return value == null ? "" : value; }
    private static String formatRnc(String value) {
        if (value == null || !value.matches("\\d{9}")) return orEmpty(value);
        return value.substring(0,3) + "-" + value.substring(3,8) + "-" + value.substring(8);
    }
    private static String formatPhone(String value) {
        if (value == null || !value.matches("\\d{10}")) return orEmpty(value);
        return "(" + value.substring(0,3) + ") " + value.substring(3,6) + "-" + value.substring(6);
    }
}
