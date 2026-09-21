package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;

import java.util.Objects;
import java.util.function.Consumer;

/** Estado visual de paginación; la consulta paginada debe realizarla el servicio backend. */
public class AppPagination extends Div {
    public record PageRequest(int page, int size) {}

    private final Consumer<PageRequest> onChange;
    private final Select<Integer> pageSize = new Select<>();
    private final Span summary = new Span();
    private final Button previous = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private int page;
    private long total;

    public AppPagination(Consumer<PageRequest> onChange) {
        this.onChange = Objects.requireNonNull(onChange);
        addClassName("cc-pagination");
        pageSize.setLabel("Registros por página");
        pageSize.setItems(10, 25, 50, 100);
        pageSize.setValue(10);
        pageSize.addValueChangeListener(event -> {
            if (!event.isFromClient()) return;
            page = 0;
            refresh();
            notifyChange();
        });
        previous.getElement().setAttribute("aria-label", "Página anterior");
        previous.setTooltipText("Página anterior");
        previous.addClickListener(event -> {
            if (page <= 0) return;
            page--;
            refresh();
            notifyChange();
        });
        next.getElement().setAttribute("aria-label", "Página siguiente");
        next.setTooltipText("Página siguiente");
        next.addClickListener(event -> {
            if ((long) (page + 1) * pageSize.getValue() >= total) return;
            page++;
            refresh();
            notifyChange();
        });
        var navigation = new Div(summary, previous, next);
        navigation.addClassName("cc-pagination-navigation");
        add(pageSize, navigation);
        refresh();
    }

    public void setTotal(long total) {
        if (total < 0) throw new IllegalArgumentException("El total no puede ser negativo");
        this.total = total;
        int lastPage = total == 0 ? 0 : (int) ((total - 1) / pageSize.getValue());
        if (page > lastPage) {
            page = lastPage;
            notifyChange();
        }
        refresh();
    }

    public PageRequest currentRequest() { return new PageRequest(page, pageSize.getValue()); }
    public void reset() { page = 0; refresh(); }

    private void refresh() {
        long first = total == 0 ? 0 : (long) page * pageSize.getValue() + 1;
        long last = Math.min((long) (page + 1) * pageSize.getValue(), total);
        summary.setText(first + "–" + last + " de " + total);
        previous.setEnabled(page > 0);
        next.setEnabled(last < total);
    }

    private void notifyChange() { onChange.accept(currentRequest()); }
}
