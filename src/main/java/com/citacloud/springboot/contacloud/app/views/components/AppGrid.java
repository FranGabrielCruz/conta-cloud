package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.grid.Grid;

/**
 * Tabla estándar de ContaCloud. Su altura corresponde únicamente a las filas
 * cargadas para la página actual; la paginación continúa siendo responsabilidad
 * de la vista y del backend.
 */
public class AppGrid<T> extends Grid<T> {

    public AppGrid(Class<T> beanType) {
        super(beanType, false);
        setAllRowsVisible(true);
        addClassName("cc-auto-grid");
        setWidthFull();
    }
}
