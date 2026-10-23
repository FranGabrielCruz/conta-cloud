package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.textfield.BigDecimalField;

import java.math.BigDecimal;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Unico teclado numerico del POS, renderizable fijo o como popover contextual. */
public final class PosNumericKeypad extends Div {
    public enum Mode { FIXED, POPOVER }

    private final Mode mode;
    private final ActiveNumericTarget active = new ActiveNumericTarget();
    private final Map<BigDecimalField, FieldTarget> targets = new IdentityHashMap<>();
    private final Map<BigDecimalField, Button> triggers = new IdentityHashMap<>();
    private final Div keys = new Div();
    private final Button decimal = key(".", "Separador decimal", () -> input("."));
    private final Button backspace = new Button(VaadinIcon.BACKSPACE.create(), event -> input("Backspace"));
    private final Popover popover;
    private BigDecimalField popoverField;

    public PosNumericKeypad() {
        this(Mode.FIXED);
    }

    public PosNumericKeypad(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
        keys.addClassName("cc-pos-keypad");
        for (String value : new String[]{"7", "8", "9", "4", "5", "6", "1", "2", "3", "0"}) {
            keys.add(key(value, accessibleName(value), () -> input(value)));
        }
        decimal.addClassName("cc-pos-keypad-decimal");
        backspace.addClassNames("cc-pos-keypad-key", "cc-pos-keypad-backspace");
        backspace.setTooltipText("Borrar último dígito");
        backspace.getElement().setAttribute("aria-label", "Borrar último dígito");
        backspace.getElement().setAttribute("tabindex", "-1");
        keys.add(decimal, backspace);
        if (mode == Mode.FIXED) {
            Span title = new Span("🔢 TECLADO NUMÉRICO");
            title.addClassName("cc-pos-keypad-title");
            add(title, keys);
            addClassNames("cc-pos-keypad-block", "cc-pos-keypad-fixed");
            popover = null;
        } else {
            Div content = new Div(keys);
            content.addClassName("cc-pos-keypad-popover-content");
            popover = new Popover(content);
            popover.setPosition(PopoverPosition.BOTTOM_END);
            popover.setOpenOnClick(false);
            popover.setOpenOnFocus(false);
            popover.setOpenOnHover(false);
            popover.setCloseOnEsc(true);
            popover.setCloseOnOutsideClick(true);
            popover.setAutofocus(false);
            popover.setTabFocusEnabled(false);
            popover.setWidth("min(250px, calc(100vw - 24px))");
            popover.addOpenedChangeListener(event -> {
                if (!event.isOpened()) {
                    active.finish();
                    popoverField = null;
                    refreshTriggers();
                    refreshState();
                }
            });
            add(popover);
            addClassName("cc-pos-keypad-popover-host");
        }
        refreshState();
    }

    /** Vincula un campo y devuelve su accion compacta para abrir el modo emergente. */
    public Button bind(BigDecimalField field, Object owner, Options options, Consumer<BigDecimal> onCommit) {
        Objects.requireNonNull(field);
        FieldTarget target = new FieldTarget(field, owner, options, onCommit);
        targets.put(field, target);
        field.addFocusListener(event -> {
            if (mode == Mode.FIXED) activate(field);
            else if (isPopoverOpen() && field != popoverField) close();
        });
        field.addValueChangeListener(event -> {
            if (event.isFromClient() && !target.updating) {
                target.remember(event.getValue());
                onCommit.accept(event.getValue());
            }
        });
        field.addDetachListener(event -> {
            if (field == popoverField) close();
            if (mode == Mode.FIXED) {
                targets.remove(field);
                triggers.remove(field);
            }
        });
        installPhysicalKeyboard(field, target);
        if (mode == Mode.POPOVER) {
            Button trigger = new Button(VaadinIcon.KEYBOARD.create(), event -> toggle(field));
            trigger.addClassName("cc-numeric-keypad-toggle");
            trigger.setTooltipText("Abrir teclado numérico");
            trigger.getElement().setAttribute("aria-label", "Abrir teclado numérico");
            triggers.put(field, trigger);
            refreshTrigger(field);
            return trigger;
        }
        return null;
    }

    public void activate(BigDecimalField field) {
        FieldTarget target = targets.get(field);
        if (target == null) return;
        active.activate(target);
        target.captureOriginal();
        refreshState();
    }

    public void finishAndClear() {
        active.finish();
        closePopover();
        refreshState();
    }

    public void clearTarget() {
        finishAndClear();
    }

    public void clearTarget(Object owner) {
        active.finishIfOwner(owner);
        if (!active.hasTarget()) closePopover();
        refreshState();
    }

    public void refresh(BigDecimalField field) {
        if (mode == Mode.POPOVER && field == popoverField && !isEditable(field)) close();
        refreshTrigger(field);
    }

    public void setOwner(BigDecimalField field, Object owner) {
        FieldTarget target = targets.get(field);
        if (target != null) target.owner = owner;
    }

    /** Cierra el popover al pasar a controles que no son targets numericos. */
    public void closeOnFocus(Component... components) {
        if (mode != Mode.POPOVER) return;
        for (Component component : components) {
            component.getElement().addEventListener("focusin", event -> closeIfOpen());
        }
    }

    public boolean closeIfOpen() {
        if (!isPopoverOpen()) return false;
        close();
        return true;
    }

    public void close() {
        active.finish();
        closePopover();
        refreshState();
    }

    public boolean isPopoverOpen() {
        return popover != null && popover.isOpened();
    }

    public Mode mode() { return mode; }

    private void toggle(BigDecimalField field) {
        if (!isEditable(field)) {
            refreshTrigger(field);
            return;
        }
        if (field == popoverField && isPopoverOpen()) {
            close();
            return;
        }
        activate(field);
        popoverField = field;
        popover.setTarget(triggers.get(field));
        popover.open();
        refreshTriggers();
    }

    private void closePopover() {
        if (popover != null && popover.isOpened()) popover.close();
        popoverField = null;
        refreshTriggers();
    }

    private void input(String value) {
        active.input(value);
        refreshState();
    }

    private Button key(String text, String ariaLabel, Runnable action) {
        Button button = new Button(text, event -> action.run());
        button.addClassName("cc-pos-keypad-key");
        button.getElement().setAttribute("aria-label", ariaLabel);
        button.getElement().setAttribute("tabindex", "-1");
        return button;
    }

    private void refreshState() {
        boolean enabled = active.hasTarget();
        keys.getChildren().filter(Button.class::isInstance).map(Button.class::cast)
                .forEach(button -> button.setEnabled(enabled));
        decimal.setEnabled(enabled && active.decimalEnabled());
        setClassName("cc-pos-keypad-disabled", mode == Mode.FIXED && !enabled);
    }

    private void refreshTriggers() {
        triggers.keySet().forEach(this::refreshTrigger);
    }

    private void refreshTrigger(BigDecimalField field) {
        Button trigger = triggers.get(field);
        if (trigger == null) return;
        boolean editable = isEditable(field);
        trigger.setVisible(editable);
        boolean openedHere = editable && field == popoverField && isPopoverOpen();
        String label = openedHere ? "Cerrar teclado numérico" : "Abrir teclado numérico";
        trigger.setTooltipText(label);
        trigger.getElement().setAttribute("aria-label", label);
        trigger.getElement().setAttribute("aria-pressed", Boolean.toString(openedHere));
    }

    private static boolean isEditable(BigDecimalField field) {
        return field.isVisible() && field.isEnabled() && !field.isReadOnly();
    }

    private void installPhysicalKeyboard(BigDecimalField field, FieldTarget target) {
        field.getElement().addEventListener("cc-numeric-key", event -> {
            activate(field);
            String key = event.getEventData().get("event.detail.key").asText();
            if ("Enter".equals(key)) {
                finishAndClear();
                target.options.onEnter().run();
            } else input(key);
        }).addEventData("event.detail.key");
        field.getElement().addEventListener("cc-numeric-blur", event -> {
            if (active.hasTarget() && !isPopoverOpen()) finishAndClear();
        });
        field.addAttachListener(event -> field.getElement().executeJs("""
                const host = this;
                if (host.__ccNumericKeyHandler) return;
                host.__ccNumericKeyHandler = event => {
                  const accepted = /^[0-9]$/.test(event.key) || event.key === '.' || event.key === ','
                    || event.key === 'Backspace' || event.key === 'Enter';
                  if (!accepted || event.ctrlKey || event.metaKey || event.altKey) return;
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  host.dispatchEvent(new CustomEvent('cc-numeric-key', {detail: {key: event.key}}));
                };
                host.__ccNumericBlurHandler = () => setTimeout(() => {
                  const focused = document.activeElement;
                  if (focused?.closest?.('.cc-pos-keypad') || focused?.closest?.('.cc-numeric-keypad-toggle')) return;
                  host.dispatchEvent(new CustomEvent('cc-numeric-blur'));
                }, 0);
                host.addEventListener('keydown', host.__ccNumericKeyHandler, true);
                host.addEventListener('focusout', host.__ccNumericBlurHandler);
                """));
        field.addDetachListener(event -> field.getElement().executeJs("""
                if (this.__ccNumericKeyHandler) this.removeEventListener('keydown', this.__ccNumericKeyHandler, true);
                if (this.__ccNumericBlurHandler) this.removeEventListener('focusout', this.__ccNumericBlurHandler);
                this.__ccNumericKeyHandler = null;
                this.__ccNumericBlurHandler = null;
                """));
    }

    private static String accessibleName(String digit) {
        return switch (digit) {
            case "0" -> "Cero"; case "1" -> "Uno"; case "2" -> "Dos"; case "3" -> "Tres";
            case "4" -> "Cuatro"; case "5" -> "Cinco"; case "6" -> "Seis"; case "7" -> "Siete";
            case "8" -> "Ocho"; case "9" -> "Nueve"; default -> digit;
        };
    }

    public record Options(boolean decimalAllowed, int scale, int maxDigits, boolean livePreview, Runnable onEnter) {
        public Options {
            if (scale < 0 || maxDigits < 1) throw new IllegalArgumentException("Configuración numérica inválida.");
            onEnter = onEnter == null ? () -> {} : onEnter;
        }
        public static Options quantity(boolean fractional, Runnable onEnter) {
            return new Options(fractional, fractional ? 4 : 0, 12, false, onEnter);
        }
        public static Options money(Runnable onEnter) {
            return new Options(true, 4, 16, false, onEnter);
        }
        public static Options moneyLive(Runnable onEnter) {
            return new Options(true, 4, 16, true, onEnter);
        }
    }

    private static final class FieldTarget implements NumericTarget {
        private final BigDecimalField field;
        private Object owner;
        private final Options options;
        private final Consumer<BigDecimal> onCommit;
        private BigDecimal original;
        private boolean updating;

        private FieldTarget(BigDecimalField field, Object owner, Options options, Consumer<BigDecimal> onCommit) {
            this.field = field;
            this.owner = owner;
            this.options = Objects.requireNonNull(options);
            this.onCommit = Objects.requireNonNull(onCommit);
            captureOriginal();
        }

        void captureOriginal() { original = field.getValue(); }
        void remember(BigDecimal value) { original = value; }
        @Override public Object owner() { return owner; }
        @Override public String currentRawValue() { return raw(field.getValue()); }
        @Override public boolean isValid() { return field.isAttached() && field.isVisible() && field.isEnabled() && !field.isReadOnly(); }
        @Override public boolean decimalAllowed() { return options.decimalAllowed(); }
        @Override public int scale() { return options.scale(); }
        @Override public int maxDigits() { return options.maxDigits(); }
        @Override public void preview(String rawValue) {
            if (options.livePreview()) setRaw(rawValue);
            else field.getElement().setProperty("value", rawValue == null ? "" : rawValue);
        }
        @Override public void commit(String rawValue) {
            BigDecimal value = parse(rawValue);
            if (value == null) { restore(); return; }
            setValue(value);
            onCommit.accept(value);
            original = value;
        }
        @Override public void restore() { setValue(original); }

        private void setRaw(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                setValue(null);
                return;
            }
            BigDecimal parsed = parse(rawValue);
            if (parsed != null) setValue(parsed);
            if (rawValue.endsWith(".")) field.getElement().setProperty("value", rawValue);
        }

        private void setValue(BigDecimal value) {
            updating = true;
            try { field.setValue(value); }
            finally { updating = false; }
        }

        private static BigDecimal parse(String rawValue) {
            if (rawValue == null || rawValue.isBlank() || ".".equals(rawValue)) return null;
            try { return new BigDecimal(rawValue.endsWith(".") ? rawValue.substring(0, rawValue.length() - 1) : rawValue); }
            catch (NumberFormatException ignored) { return null; }
        }
        private static String raw(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }
    }
}
