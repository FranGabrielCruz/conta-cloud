package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.EmpresaDto;
import com.citacloud.springboot.contacloud.app.services.EmpresaService;
import com.citacloud.springboot.contacloud.app.services.CompanyConfigurationService;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.views.components.AppDetailSection;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.citacloud.springboot.contacloud.app.views.components.CompanyLogoAvatar;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

@Route(value = "empresa", layout = MainLayout.class) @PageTitle("Empresa | ContaCloud") @PermitAll
public class EmpresaView extends VerticalLayout {
    public EmpresaView(EmpresaService service, CompanyConfigurationService companyConfiguration) {
        EmpresaDto empresa = service.obtenerActual();
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        var details = new AppDetailSection("Información general")
            .field("Nombre", empresa.nombre())
            .field("Identificación fiscal", empresa.identificacionFiscal())
            .field("País", empresa.paisCodigo())
            .field("Zona horaria", empresa.zonaHoraria())
            .field("Código", empresa.codigo())
            .field("Estado", empresa.activo() ? "Activa" : "Inactiva");
        String initial = empresa.nombre().isBlank() ? "C" : empresa.nombre().substring(0, 1).toUpperCase();
        var preview = new CompanyLogoAvatar(initial, true);
        preview.refresh(companyConfiguration.currentLogoAvailable());
        var buffer = new MemoryBuffer();
        var upload = new Upload(buffer);
        upload.setMaxFiles(1);
        upload.setMaxFileSize(CompanyConfigurationService.MAX_LOGO_BYTES);
        upload.setAcceptedFileTypes("image/png", "image/jpeg", "image/webp", ".png", ".jpg", ".jpeg", ".webp");
        upload.setUploadButton(new Button("Reemplazar logo"));
        upload.setDropLabel(new Div("PNG, JPG o WEBP · máximo 2 MB"));
        upload.addSucceededListener(event -> {
            try {
                companyConfiguration.replaceCompanyLogo(buffer.getInputStream(), event.getFileName(), event.getMIMEType());
                preview.refresh(true);
                UI.getCurrent().getActiveRouterTargetsChain().stream()
                    .filter(MainLayout.class::isInstance).map(MainLayout.class::cast)
                    .forEach(MainLayout::refreshCompanyLogo);
                Notification.show("Logo actualizado correctamente");
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            } catch (RuntimeException ex) {
                Notification.show("No fue posible guardar el logo. El logo anterior se conservó.");
            }
            upload.clearFileList();
        });
        upload.addFileRejectedListener(event -> Notification.show("Logo rechazado: verifique el formato y el tamaño máximo de 2 MB"));
        var logoRow = new HorizontalLayout(preview);
        logoRow.setAlignItems(Alignment.CENTER);
        if (TenantContext.principalActual().permisos().contains("EMPRESA_EDITAR")) logoRow.add(upload);
        var logoSection = new VerticalLayout(new H3("Logo de la empresa"), logoRow);
        logoSection.addClassName("cc-card");
        logoSection.setPadding(true);
        logoSection.setWidthFull();
        add(new AppPageHeader("Datos de empresa", "Información general de la empresa activa."), logoSection, details);
    }
}
