package com.citacloud.springboot.contacloud.app.views;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.spring.security.AuthenticationContext;

import java.time.Duration;

/** Coordina la inactividad real del usuario sin tratar el heartbeat de Vaadin como actividad. */
final class InactivityGuard extends Div {

    private final AuthenticationContext authenticationContext;
    private ConfirmDialog advertencia;

    InactivityGuard(AuthenticationContext authenticationContext, Duration warningAfter, Duration expireAfter) {
        this.authenticationContext = authenticationContext;
        setVisible(false);
        getElement().executeJs("""
            const host = this;
            const warningMs = $0;
            const expireMs = $1;
            let warningTimer;
            let expireTimer;
            let lastServerSignal = 0;
            const channel = 'BroadcastChannel' in window ? new BroadcastChannel('contacloud-session') : null;

            const schedule = () => {
              clearTimeout(warningTimer);
              clearTimeout(expireTimer);
              warningTimer = setTimeout(() => host.$server.mostrarAdvertencia(), warningMs);
              expireTimer = setTimeout(() => {
                channel?.postMessage({type: 'expired'});
                host.$server.expirar();
              }, expireMs);
            };
            const activity = (fromOtherTab = false) => {
              schedule();
              if (!fromOtherTab) channel?.postMessage({type: 'activity'});
              const now = Date.now();
              if (now - lastServerSignal > 60000) {
                lastServerSignal = now;
                host.$server.registrarActividad();
              }
            };
            const listener = () => activity(false);
            ['pointerdown', 'keydown', 'touchstart', 'scroll'].forEach(name =>
              window.addEventListener(name, listener, {passive: true}));
            if (channel) channel.onmessage = event => {
              if (event.data?.type === 'activity') activity(true);
              if (event.data?.type === 'expired') host.$server.expirar();
            };
            schedule();
            this.__ccCleanup = () => {
              clearTimeout(warningTimer);
              clearTimeout(expireTimer);
              ['pointerdown', 'keydown', 'touchstart', 'scroll'].forEach(name =>
                window.removeEventListener(name, listener));
              channel?.close();
            };
            """, warningAfter.toMillis(), expireAfter.toMillis());
    }

    @ClientCallable
    public void registrarActividad() {
        if (advertencia != null && advertencia.isOpened()) advertencia.close();
    }

    @ClientCallable
    public void mostrarAdvertencia() {
        if (advertencia != null && advertencia.isOpened()) return;
        advertencia = new ConfirmDialog();
        advertencia.setHeader("Tu sesión está por vencer");
        advertencia.setText("Por seguridad, la sesión se cerrará pronto si no detectamos actividad.");
        advertencia.setConfirmText("Continuar sesión");
        advertencia.setCancelText("Cerrar sesión");
        advertencia.setCancelable(true);
        advertencia.addConfirmListener(event -> getElement().executeJs("""
            window.dispatchEvent(new Event('pointerdown'));
            """));
        advertencia.addCancelListener(event -> authenticationContext.logout());
        advertencia.open();
    }

    @ClientCallable
    public void expirar() {
        authenticationContext.logout();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        getElement().executeJs("this.__ccCleanup?.()");
        super.onDetach(detachEvent);
    }
}
