package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.textfield.TextField;
import java.util.UUID;

public final class InputFieldSupport {
    private InputFieldSupport() {}

    public static void desactivarAutocompletado(Component campo, String proposito) {
        configurarAutocompletado(campo, proposito, "off");
    }

    public static void nuevaContrasena(Component campo, String proposito) {
        configurarAutocompletado(campo, proposito, "new-password");
    }

    public static void aplicarFormatoTelefono(TextField campo) {
        campo.addBlurListener(event -> campo.setValue(formatearTelefono(campo.getValue())));
    }

    public static void aplicarFormatoRnc(TextField campo) {
        campo.addBlurListener(event -> campo.setValue(formatearRnc(campo.getValue())));
    }

    public static String formatearTelefono(String valor) {
        String digitos = soloDigitos(valor);
        if (digitos.isEmpty()) return "";
        if (digitos.length() != 10) return valor == null ? "" : valor.trim();
        return digitos.substring(0, 3) + "-" + digitos.substring(3, 6) + "-" + digitos.substring(6);
    }

    public static String formatearRnc(String valor) {
        String digitos = soloDigitos(valor);
        if (digitos.isEmpty()) return "";
        if (digitos.length() != 9) return valor == null ? "" : valor.trim();
        return digitos.substring(0, 1) + "-" + digitos.substring(1, 3) + "-"
            + digitos.substring(3, 8) + "-" + digitos.substring(8);
    }

    private static void configurarAutocompletado(Component campo, String proposito, String autocomplete) {
        String nombre = proposito + "-" + UUID.randomUUID();
        campo.getElement().setAttribute("autocomplete", autocomplete);
        campo.getElement().setAttribute("name", nombre);
        campo.addAttachListener(event -> campo.getElement().executeJs(
            "const input=this.inputElement; if(input){input.setAttribute('autocomplete',$0);input.setAttribute('name',$1);}",
            autocomplete, nombre));
    }

    private static String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("[^0-9]", "");
    }
}
