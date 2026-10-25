package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.services.PosPaginationPolicy;

import java.util.function.Consumer;

/** Paginación estándar para tablas incluidas en ventanas emergentes del POS. */
public final class PosDialogPagination extends AppPagination {
    public static final int DEFAULT_PAGE_SIZE = PosPaginationPolicy.DIALOG_DEFAULT_PAGE_SIZE;

    public PosDialogPagination(Consumer<PageRequest> onChange) {
        super(onChange, DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE);
    }

    private PosDialogPagination(Consumer<PageRequest> onChange, boolean showPageSizeSelector) {
        super(onChange, DEFAULT_PAGE_SIZE, showPageSizeSelector, DEFAULT_PAGE_SIZE);
    }

    /** Paginación fija de cinco filas, sin selector visual de tamaño de página. */
    public static PosDialogPagination fixed(Consumer<PageRequest> onChange) {
        return new PosDialogPagination(onChange, false);
    }

    /** Permite una excepción local explícita sin modificar el valor predeterminado del POS. */
    public PosDialogPagination(Consumer<PageRequest> onChange, int defaultPageSize,
                               Integer... availablePageSizes) {
        super(onChange, defaultPageSize, availablePageSizes);
    }
}
