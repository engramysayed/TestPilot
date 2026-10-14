package delivery.mobile.runner.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Normalized UiAutomator / accessibility node. Selectors preserve original attribute bytes
 * (Unicode). Password nodes keep a {@code password=true} flag; text/value is not echoed for secrets.
 */
public final class MobileSnapshotNode {
    private final int index;
    private final Integer parentIndex;
    private final String className;
    private final String packageName;
    private final String resourceId;
    private final String contentDesc;
    private final String text;
    private final String hint;
    private final MobileBounds bounds;
    private final boolean enabled;
    private final boolean displayed;
    private final boolean clickable;
    private final boolean focusable;
    private final boolean selected;
    private final boolean password;
    private final boolean checkable;
    private final boolean checked;
    private final boolean scrollable;
    private final boolean longClickable;
    private final String windowId;
    private final List<Integer> childIndexes;

    public MobileSnapshotNode(
            int index,
            Integer parentIndex,
            String className,
            String packageName,
            String resourceId,
            String contentDesc,
            String text,
            String hint,
            MobileBounds bounds,
            boolean enabled,
            boolean displayed,
            boolean clickable,
            boolean focusable,
            boolean selected,
            boolean password,
            boolean checkable,
            boolean checked,
            boolean scrollable,
            boolean longClickable,
            String windowId,
            List<Integer> childIndexes
    ) {
        this.index = index;
        this.parentIndex = parentIndex;
        this.className = nullToEmpty(className);
        this.packageName = nullToEmpty(packageName);
        this.resourceId = nullToEmpty(resourceId);
        this.contentDesc = nullToEmpty(contentDesc);
        this.text = nullToEmpty(text);
        this.hint = nullToEmpty(hint);
        this.bounds = bounds;
        this.enabled = enabled;
        this.displayed = displayed;
        this.clickable = clickable;
        this.focusable = focusable;
        this.selected = selected;
        this.password = password;
        this.checkable = checkable;
        this.checked = checked;
        this.scrollable = scrollable;
        this.longClickable = longClickable;
        this.windowId = nullToEmpty(windowId);
        this.childIndexes = childIndexes == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(childIndexes));
    }

    public int index() { return index; }
    public Integer parentIndex() { return parentIndex; }
    public String className() { return className; }
    public String packageName() { return packageName; }
    public String resourceId() { return resourceId; }
    public String contentDesc() { return contentDesc; }
    /** Visible text; empty when {@link #password()} for redaction safety in logs. */
    public String text() { return password ? "" : text; }
    /** Raw text including password fields — for binder tests only; never log. */
    public String rawText() { return text; }
    public String hint() { return hint; }
    public MobileBounds bounds() { return bounds; }
    public boolean enabled() { return enabled; }
    public boolean displayed() { return displayed; }
    public boolean clickable() { return clickable; }
    public boolean focusable() { return focusable; }
    public boolean selected() { return selected; }
    public boolean password() { return password; }
    public boolean checkable() { return checkable; }
    public boolean checked() { return checked; }
    public boolean scrollable() { return scrollable; }
    public boolean longClickable() { return longClickable; }
    public String windowId() { return windowId; }
    public List<Integer> childIndexes() { return childIndexes; }

    /** Newline-normalized content-desc (Flutter merged cards often embed {@code \\n}). */
    public String contentDescNormalized() {
        return contentDesc.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    public boolean looksLikeSystemUi() {
        String p = packageName.toLowerCase();
        return p.contains("systemui") || p.contains("inputmethod") || "com.android.systemui".equals(p);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobileSnapshotNode n)) return false;
        return index == n.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }
}
