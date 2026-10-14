package delivery.mobile.runner.snapshot;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Screen-space axis-aligned bounds from UiAutomator {@code bounds="[l,t][r,b]"}. */
public record MobileBounds(int left, int top, int right, int bottom) {
    private static final Pattern BOUNDS = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    public MobileBounds {
        if (right < left || bottom < top) {
            throw new IllegalArgumentException("invalid bounds: [" + left + "," + top + "][" + right + "," + bottom + "]");
        }
    }

    public int width() { return right - left; }
    public int height() { return bottom - top; }
    public int centerX() { return left + width() / 2; }
    public int centerY() { return top + height() / 2; }
    public boolean isEmpty() { return width() <= 0 || height() <= 0; }

    public static MobileBounds parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = BOUNDS.matcher(raw.trim());
        if (!m.matches()) {
            throw new MobileSnapshotException(MobileSnapshotException.Code.BAD_BOUNDS,
                    "unparseable bounds: " + raw);
        }
        return new MobileBounds(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4)));
    }

    @Override
    public String toString() {
        return "[" + left + "," + top + "][" + right + "," + bottom + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobileBounds b)) return false;
        return left == b.left && top == b.top && right == b.right && bottom == b.bottom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, top, right, bottom);
    }
}
