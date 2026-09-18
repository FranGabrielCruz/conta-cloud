package com.citacloud.springboot.contacloud.app.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import com.vaadin.flow.server.VaadinServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route("login") @PageTitle("Iniciar sesion | ContaCloud") @PermitAll
public class LoginView extends VerticalLayout implements BeforeEnterObserver {
    private static final Logger log = LoggerFactory.getLogger(LoginView.class);
    private static final String EMPRESA_STORAGE_KEY = "contacloud.login.empresa";
    private static final String USUARIO_STORAGE_KEY = "contacloud.login.usuario";
    private final TextField empresa = new TextField("Empresa");
    private final TextField usuario = new TextField("Usuario");
    private final PasswordField password = new PasswordField("Contraseña");
    private final AuthenticationManager authenticationManager;

    public LoginView(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        addClassName("login-view"); setSizeFull(); setAlignItems(Alignment.CENTER); setJustifyContentMode(JustifyContentMode.CENTER);
        var marca = new H1("ContaCloud"); var subtitulo = new Paragraph("Administracion contable clara y segura");
        empresa.setRequired(true); usuario.setRequired(true); password.setRequired(true);
        empresa.getElement().setProperty("autocomplete", "organization");
        usuario.getElement().setProperty("autocomplete", "username");
        password.getElement().setProperty("autocomplete", "current-password");
        empresa.setWidthFull(); usuario.setWidthFull(); password.setWidthFull();
        var ingresar = new Button("Ingresar", e -> ingresar()); ingresar.addThemeName("primary"); ingresar.setWidthFull();
        password.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> ingresar());
        var tarjeta = new VerticalLayout(marca, subtitulo, empresa, usuario, password, ingresar);
        tarjeta.addClassName("login-card");
        tarjeta.setWidth("min(92vw, 420px)"); tarjeta.getStyle().set("padding", "2rem").set("border-radius", "16px")
            .set("box-shadow", "0 16px 48px rgba(15,23,42,.14)");
        add(tarjeta);
        addAttachListener(event -> restaurarDatosRecordados());
    }
    private void ingresar() {
        try {
            var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(empresa.getValue() + "|" + usuario.getValue(), password.getValue()));
            var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            VaadinServletRequest.getCurrent().getHttpServletRequest().getSession(true)
                .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            var ui = UI.getCurrent();
            ui.getPage().executeJs("""
                localStorage.setItem($0, $1);
                localStorage.setItem($2, $3);
                return true;
                """, EMPRESA_STORAGE_KEY, empresa.getValue().trim(),
                USUARIO_STORAGE_KEY, usuario.getValue().trim())
                .then(Boolean.class, guardado -> ui.navigate("dashboard"));
        } catch (AuthenticationException ex) {
            var n = Notification.show("Empresa, usuario o contraseña incorrectos"); n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (RuntimeException ex) {
            log.error("No fue posible completar el inicio de sesion", ex);
            var n = Notification.show("No fue posible iniciar sesion. Intente nuevamente");
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void restaurarDatosRecordados() {
        empresa.getElement().executeJs("return localStorage.getItem($0) || ''", EMPRESA_STORAGE_KEY)
            .then(String.class, valor -> {
                if (valor != null && !valor.isBlank()) empresa.setValue(valor);
            });
        usuario.getElement().executeJs("return localStorage.getItem($0) || ''", USUARIO_STORAGE_KEY)
            .then(String.class, valor -> {
                if (valor != null && !valor.isBlank()) usuario.setValue(valor);
            });
    }

    @Override public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("timeout"))
            Notification.show("Tu sesión se cerró por inactividad.");
        else if (event.getLocation().getQueryParameters().getParameters().containsKey("error"))
            Notification.show("No fue posible iniciar sesion");
    }
}
