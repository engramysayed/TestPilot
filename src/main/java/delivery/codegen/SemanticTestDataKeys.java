package delivery.codegen;

import java.util.Locale;

/** Human-readable property keys for generated test data (optional project env prefix). */
final class SemanticTestDataKeys {
    private SemanticTestDataKeys() {
    }

    static String mapPlaceholder(String value, String envPrefix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("${TARGET_USERNAME}".equals(value)) {
            return prefixed(envPrefix, "username");
        }
        if ("${TARGET_PASSWORD}".equals(value)) {
            return prefixed(envPrefix, "password");
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            return value.substring(2, value.length() - 1);
        }
        return null;
    }

    static String mapInventedField(ProvenStep step, String envPrefix) {
        if (step == null) {
            return null;
        }
        String method = CodegenNaming.actionMethodName(step).toLowerCase(Locale.ROOT);
        if (method.contains("otp") || method.contains("passcode") || method.contains("one_time")) {
            return prefixed(envPrefix, "otp");
        }
        if (method.contains("password")) {
            return prefixed(envPrefix, "password");
        }
        if (method.contains("username") || method.endsWith("_user") || method.contains("_user_")) {
            return prefixed(envPrefix, "username");
        }
        return null;
    }

    private static String prefixed(String envPrefix, String leaf) {
        String env = envPrefix == null ? "" : envPrefix.trim().toLowerCase(Locale.ROOT);
        if (env.isBlank()) {
            return leaf;
        }
        String safe = env.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (safe.isBlank()) {
            return leaf;
        }
        return safe + "_" + leaf;
    }
}
