package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.services.PosPaginationPolicy;

import java.util.function.Consumer;

/** Paginación estándar para tablas incluidas en ventanas emergentes del POS. */
public final class PosDialogPagination extends AppPagination {
    public static final int DEFAULT_PAGE_SIZE = PosPaginationPolicy.DIALOG_DEFAULT_PAGE_SIZE;

    public PosDialogPagination(Consumer<PageRequest> onChange) {
        super(onChange, DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE);
    }

    /** Permite una excepción local explícita sin modificar el valor predeterminado del POS. */
    public PosDialogPagination(Consumer<PageRequest> onChange, int defaultPageSize,
                               Integer... availablePageSizes) {
        super(onChange, defaultPageSize, availablePageSizes);
    }
}
