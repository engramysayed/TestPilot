package delivery.portal.roles;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Allowlist of permission codes. Primary feature codes are {@code feature.*};
 * legacy {@code job.*} codes are accepted as aliases and normalized on write.
 */
public final class PermissionCodes {
    public static final String ROLES_MANAGE = "roles.manage";
    public static final String CREDITS_ADMIN = "credits.admin";
    public static final String CREDITS_VIEW_SELF = "credits.view_self";
    public static final String FEATURE_GENERATE = "feature.generate";
    public static final String FEATURE_EXECUTE = "feature.execute";
    public static final String FEATURE_AUTOMATE = "feature.automate";
    public static final String FEATURE_HUNT = "feature.hunt";
    public static final String PROJECT_READ = "project.read";
    public static final String PROJECT_OPERATE = "project.operate";
    public static final String ADMIN_OPS = "admin.ops";

    private static final Map<String, String> ALIASES = Map.of(
            "job.generate", FEATURE_GENERATE,
            "job.execute", FEATURE_EXECUTE,
            "job.automate", FEATURE_AUTOMATE,
            "job.hunt", FEATURE_HUNT
    );

    private static final List<CatalogEntry> CATALOG = List.of(
            entry(ROLES_MANAGE, "Manage roles", "Roles"),
            entry(CREDITS_ADMIN, "Admin credits", "Credits"),
            entry(CREDITS_VIEW_SELF, "View own credits", "Credits"),
            entry(FEATURE_GENERATE, "Start Generate", "Features"),
            entry(FEATURE_EXECUTE, "Start Execute", "Features"),
            entry(FEATURE_AUTOMATE, "Start Automate", "Features"),
            entry(FEATURE_HUNT, "Start Bug Hunter", "Features"),
            entry(PROJECT_READ, "Read project", "Projects"),
            entry(PROJECT_OPERATE, "Operate project", "Projects"),
            entry(ADMIN_OPS, "Admin ops", "Admin")
    );

    private static final Set<String> ALLOWED;
    static {
        Set<String> s = new LinkedHashSet<>();
        for (CatalogEntry e : CATALOG) {
            s.add(e.code());
        }
        ALLOWED = Collections.unmodifiableSet(s);
    }

    private PermissionCodes() {
    }

    public record CatalogEntry(String code, String label, String group) {
    }

    private static CatalogEntry entry(String code, String label, String group) {
        return new CatalogEntry(code, label, group);
    }

    public static List<CatalogEntry> catalog() {
        return CATALOG;
    }

    public static Set<String> all() {
        return ALLOWED;
    }

    /** Normalize alias → primary; empty if unknown. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String code = raw.trim().toLowerCase(Locale.ROOT);
        String aliased = ALIASES.getOrDefault(code, code);
        return ALLOWED.contains(aliased) ? Optional.of(aliased) : Optional.empty();
    }

    public static String requireKnown(String raw) {
        return normalize(raw).orElseThrow(() ->
                new IllegalArgumentException("Unknown permission code: " + raw));
    }

    public static Set<String> normalizeAll(Iterable<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String r : raw) {
            out.add(requireKnown(r));
        }
        return out;
    }

    public static String forJobKind(String kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        return switch (kind.trim().toUpperCase(Locale.ROOT)) {
            case "GENERATE", "GENERATE_BATCH" -> FEATURE_GENERATE;
            case "EXECUTE" -> FEATURE_EXECUTE;
            case "AUTOMATE", "CONVERT" -> FEATURE_AUTOMATE;
            case "HUNT" -> FEATURE_HUNT;
            default -> throw new IllegalArgumentException("Unsupported job kind: " + kind);
        };
    }

    public static Set<String> ownerAll() {
        return ALLOWED;
    }

    public static Set<String> adminDefaults() {
        Set<String> s = new LinkedHashSet<>();
        s.add(CREDITS_VIEW_SELF);
        s.add(FEATURE_GENERATE);
        s.add(FEATURE_EXECUTE);
        s.add(FEATURE_AUTOMATE);
        s.add(FEATURE_HUNT);
        s.add(PROJECT_READ);
        s.add(PROJECT_OPERATE);
        s.add(ADMIN_OPS);
        return s;
    }

    public static Set<String> memberDefaults() {
        Set<String> s = new LinkedHashSet<>();
        s.add(CREDITS_VIEW_SELF);
        s.add(FEATURE_GENERATE);
        s.add(FEATURE_EXECUTE);
        s.add(FEATURE_AUTOMATE);
        s.add(FEATURE_HUNT);
        s.add(PROJECT_READ);
        return s;
    }

    public static Map<String, Object> catalogPayload() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("permissions", CATALOG.stream()
                .map(e -> Map.of("code", e.code(), "label", e.label(), "group", e.group()))
                .toList());
        return body;
    }
}
