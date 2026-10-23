package com.citacloud.springboot.contacloud.app.views;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/** Coordina la inactividad real del usuario sin tratar el heartbeat de Vaadin como actividad. */
final class InactivityGuard extends Div {

    private static final Logger LOGGER = LoggerFactory.getLogger(InactivityGuard.class);
    private final AuthenticationContext authenticationContext;

    InactivityGuard(AuthenticationContext authenticationContext, Duration expireAfter) {
        this.authenticationContext = authenticationContext;
        validarConfiguracion(expireAfter);
        // Debe permanecer visible para Vaadin (aunque CSS no lo pinte): un componente
        // marcado setVisible(false) es inerte y sus llamadas @ClientCallable se rechazan.
        addClassName("cc-inactivity-guard");
        getElement().setAttribute("aria-hidden", "true");
        LOGGER.info("Monitor de inactividad iniciado expireAfter={}", expireAfter);
        getElement().executeJs("""
            const host = this;
            const expireMs = $0;
            let expireTimer;
            let lastServerSignal = 0;
            let expired = false;
            const channel = 'BroadcastChannel' in window ? new BroadcastChannel('contacloud-session') : null;
            const activityEvents = ['pointerdown', 'keydown', 'touchstart', 'scroll'];

            const schedule = () => {
              if (expired) return;
              clearTimeout(expireTimer);
              expireTimer = setTimeout(() => expire(true), expireMs);
            };
            const expire = (broadcast) => {
              if (expired) return;
              expired = true;
              clearTimeout(expireTimer);
              activityEvents.forEach(name => window.removeEventListener(name, listener));
              document.documentElement.classList.add('cc-session-expiring');
              sessionStorage.setItem('contacloud.session.expired', 'true');
              if (broadcast) channel?.postMessage({type: 'expired'});
              host.$server.expirar().catch(() => {
                window.location.assign(new URL('login', document.baseURI));
              });
            };
            const activity = (fromOtherTab = false) => {
              if (expired) return;
              schedule();
              if (!fromOtherTab) channel?.postMessage({type: 'activity'});
              const now = Date.now();
              if (now - lastServerSignal > 60000) {
                lastServerSignal = now;
                host.$server.registrarActividad();
              }
            };
            const listener = () => activity(false);
            activityEvents.forEach(name =>
              window.addEventListener(name, listener, {passive: true}));
            if (channel) channel.onmessage = event => {
              if (event.data?.type === 'activity') activity(true);
              if (event.data?.type === 'expired') expire(false);
            };
            schedule();
            this.__ccCleanup = () => {
              clearTimeout(expireTimer);
              activityEvents.forEach(name => window.removeEventListener(name, listener));
              channel?.close();
            };
            """, expireAfter.toMillis());
    }

    @ClientCallable
    public void registrarActividad() {
        // La propia llamada mantiene sincronizada la actividad humana con la sesión HTTP.
        // Heartbeat, polling y tareas de fondo no invocan este método.
    }

    @ClientCallable
    public void expirar() {
        LOGGER.info("Inactividad expirada; ejecutando cierre de sesión");
        authenticationContext.logout();
    }

    static void validarConfiguracion(Duration expireAfter) {
        if (expireAfter == null || expireAfter.isNegative() || expireAfter.isZero()) {
            throw new IllegalArgumentException("El tiempo de inactividad debe ser mayor que cero");
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        getElement().executeJs("this.__ccCleanup?.()");
        super.onDetach(detachEvent);
    }
}
