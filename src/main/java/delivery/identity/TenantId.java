package delivery.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable customer workspace / tenant identifier. The same model is used for
 * personal workspaces ({@code ws_user_{id}}) and dedicated installations
 * ({@code ws_install_{slug}}).
 */
public record TenantId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^ws_(user_\\d+|install_[a-z0-9]+|[a-z0-9]{8,32})$");

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

    public static TenantId personal(long ownerUserId) {
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("owner user id must be positive");
        }
        return new TenantId("ws_user_" + ownerUserId);
    }

    public static TenantId dedicated(String installationSlug) {
        if (installationSlug == null || installationSlug.isBlank()) {
            throw new IllegalArgumentException("installation slug is required");
        }
        String slug = installationSlug.trim().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("invalid installation slug: " + installationSlug);
        }
        return new TenantId("ws_install_" + slug);
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
