package delivery.job;

import java.util.Locale;
import java.util.Set;

/**
 * Decides how a "selected value" assert is proven.
 *
 * <p>Native {@code <select>}, checkbox, and radio keep {@code isSelected()}. A custom picker
 * (ARIA combobox / listbox / aria-haspopup) never sets selected on the widget — the value is
 * the text it shows. That path is used only when both are true: the control is a picker, and
 * the Excel line named a value.
 */
public final class SelectedValueCheck {
    private static final Set<String> PICKER_ROLES = Set.of("combobox", "listbox");
    private static final Set<String> HASPOPUP = Set.of("listbox", "menu", "true");
    private static final Set<String> CHECKABLE_TYPES = Set.of("checkbox", "radio");

    private SelectedValueCheck() {
    }

    public static boolean isCustomPicker(String tag, String role, String ariaHaspopup, String inputType) {
        String t = lower(tag);
        if ("select".equals(t) || "option".equals(t)) {
            return false;
        }
        if ("input".equals(t) && CHECKABLE_TYPES.contains(lower(inputType))) {
            return false;
        }
        if (PICKER_ROLES.contains(lower(role))) {
            return true;
        }
        return HASPOPUP.contains(lower(ariaHaspopup));
    }

    /**
     * @return null when the assert holds, otherwise a one-line failure reason
     */
    public static String evaluate(
            boolean wantSelected,
            String expected,
            String tag,
            String role,
            String ariaHaspopup,
            String inputType,
            boolean htmlSelected,
            String visibleText,
            String ariaValueText,
            String ariaLabel
    ) {
        String want = expected == null ? "" : expected.trim();
        if (isCustomPicker(tag, role, ariaHaspopup, inputType) && wantSelected && !want.isBlank()) {
            if (displayedValueContains(want, visibleText, ariaValueText)) {
                return null;
            }
            return "false -> displayed value did not contain: " + want;
        }
        if (!want.isBlank()) {
            boolean textOk = visibleText != null && visibleText.contains(want);
            if (htmlSelected == wantSelected && textOk) {
                return null;
            }
            return "false -> selected-state/text mismatch for: " + want;
        }
        if (htmlSelected == wantSelected) {
            return null;
        }
        return "false -> expected " + (wantSelected ? "selected/checked" : "unchecked")
                + " but was " + (htmlSelected ? "selected" : "unselected");
    }

    static boolean displayedValueContains(String expected, String visibleText, String ariaValueText) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        if (visibleText != null && visibleText.contains(expected)) {
            return true;
        }
        return ariaValueText != null && ariaValueText.contains(expected);
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
