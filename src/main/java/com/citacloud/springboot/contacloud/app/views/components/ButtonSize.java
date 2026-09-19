package com.citacloud.springboot.contacloud.app.views.components;

public enum ButtonSize {
    MAIN("main"), GRID_ACTION("grid");

    private final String cssClass;

    ButtonSize(String cssClass) { this.cssClass = cssClass; }

    public String cssClass() { return cssClass; }
}
