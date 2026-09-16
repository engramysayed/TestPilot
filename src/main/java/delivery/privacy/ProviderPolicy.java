package delivery.privacy;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Deployment/tenant provider allowlist. Engine choice (Keel vs Precision) is not a
 * privacy control. Missing allowlist fails closed.
 */
public final class ProviderPolicy {
    public enum Kind {
        OLLAMA, CURSOR, AGENTROUTER, VISION
    }

    public record Decision(Kind kind, boolean allowed, String reason) {
    }

    private final Set<Kind> allowed;

    public ProviderPolicy(Set<Kind> allowed) {
        this.allowed = allowed == null ? Set.of() : Set.copyOf(allowed);
    }

    public static ProviderPolicy fromEnvironment() {
        try {
            utils.PropertyReader.loadProperties();
        } catch (Exception ignored) {
            // env/system properties still apply
        }
        String raw = System.getProperty("delivery.provider.allowlist", "");
        if (raw == null || raw.isBlank()) {
            String env = System.getenv("DELIVERY_PROVIDER_ALLOWLIST");
            raw = env == null ? "" : env;
        }
        return parse(raw);
    }

    public static ProviderPolicy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ProviderPolicy(Set.of());
        }
        Set<Kind> kinds = new LinkedHashSet<>();
        for (String part : raw.split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            Kind k = parseKind(part);
            if (k != null) {
                kinds.add(k);
            }
        }
        return new ProviderPolicy(kinds);
    }

    public Decision inspect(Kind kind) {
        if (kind == null) {
            return new Decision(null, false, "unknown provider");
        }
        if (allowed.isEmpty()) {
            return new Decision(kind, false, "provider allowlist empty; fail closed");
        }
        if (allowed.contains(kind)) {
            return new Decision(kind, true, "allowed");
        }
        return new Decision(kind, false, kind.name().toLowerCase(Locale.ROOT) + " not on allowlist");
    }

    public void require(Kind kind) {
        Decision d = inspect(kind);
        if (!d.allowed()) {
            throw new ProviderDisallowedException(d.reason());
        }
    }

    public boolean allows(Kind kind) {
        return inspect(kind).allowed();
    }

    /** Stable, secret-free snapshot for job input identity. Empty means fail closed. */
    public String snapshot() {
        if (allowed.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Kind k : allowed) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(k.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static Kind parseKind(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "ollama", "local", "llm" -> Kind.OLLAMA;
            case "cursor" -> Kind.CURSOR;
            case "agentrouter", "agent-router" -> Kind.AGENTROUTER;
            case "vision", "uitars", "qwen" -> Kind.VISION;
            default -> null;
        };
    }
}
