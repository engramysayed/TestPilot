package delivery.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Opaque, persisted workspace identifier. Account numbers and installation slugs
 * are membership or display metadata — they are not this id and must not appear
 * in storage paths.
 */
public record TenantId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^ws_[a-f0-9]{32}$");

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenant id is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.contains("..") || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException("tenant id must not contain path elements: " + value);
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid tenant id: " + value);
        }
    }

    public static TenantId parse(String raw) {
        return new TenantId(raw);
    }

    public static TenantId mint() {
        return new TenantId("ws_" + UUID.randomUUID().toString().replace("-", ""));
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TenantId other && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
