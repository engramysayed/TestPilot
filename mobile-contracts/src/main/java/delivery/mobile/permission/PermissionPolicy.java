package delivery.mobile.permission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit permission policy for a case/job (M3-05).
 * {@code allowBroadGrantAll} is always refused — overrides must be named.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionPolicy(
        List<PermissionSpec> defaults,
        List<PermissionSpec> caseOverrides,
        boolean permissionTestingCase
) {
    public static final String PERMISSION_UNSUPPORTED = "PERMISSION_UNSUPPORTED";

    public PermissionPolicy {
        defaults = defaults == null ? List.of() : List.copyOf(defaults);
        caseOverrides = caseOverrides == null ? List.of() : List.copyOf(caseOverrides);
    }

    public static PermissionPolicy empty() {
        return new PermissionPolicy(List.of(), List.of(), false);
    }

    /** Effective named grants: case overrides win; never invents grant-all. */
    public Map<String, PermissionGrant> effectiveGrants() {
        Map<String, PermissionGrant> m = new LinkedHashMap<>();
        for (PermissionSpec s : defaults) {
            m.put(s.name(), s.grant());
        }
        for (PermissionSpec s : caseOverrides) {
            m.put(s.name(), s.grant());
        }
        return Map.copyOf(m);
    }

    public List<PermissionSpec> unsupportedSpecials() {
        return effectiveSpecs().stream()
                .filter(s -> s.special() && s.grant() != PermissionGrant.UNCHANGED)
                .filter(s -> !isSupportedSpecial(s.name()))
                .toList();
    }

    public List<PermissionSpec> effectiveSpecs() {
        Map<String, PermissionSpec> m = new LinkedHashMap<>();
        for (PermissionSpec s : defaults) {
            m.put(s.name(), s);
        }
        for (PermissionSpec s : caseOverrides) {
            m.put(s.name(), s);
        }
        return List.copyOf(m.values());
    }

    /** Supported specials are an allowlist; everything else → PERMISSION_UNSUPPORTED. */
    public static boolean isSupportedSpecial(String name) {
        if (name == null) return false;
        return switch (name) {
            case "android.permission.ACCESS_NOTIFICATION_POLICY",
                 "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" -> true;
            default -> false;
        };
    }
}
