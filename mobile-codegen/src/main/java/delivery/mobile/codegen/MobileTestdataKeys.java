package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileInput;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobileStep;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owner+phase data keys for mobile codegen (web 0061 / CodegenDataKeys mirror).
 * Login and body namespaces stay separate; conflicting values for the same key fail closed.
 * Secret refs are tracked but never published as property values.
 */
public final class MobileTestdataKeys {

    public static final String PHASE_LOGIN = "login";
    public static final String PHASE_BODY = "body";
    public static final String PHASE_SETUP = "setup";

    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, String> slots = new LinkedHashMap<>();
    private final Map<String, String> valuesByKey = new LinkedHashMap<>();
    private final Set<String> secretKeys = new LinkedHashSet<>();
    private final String forcedKey;
    private boolean allocatingSecret;

    private MobileTestdataKeys(String forcedKey) {
        this.forcedKey = forcedKey;
    }

    public static MobileTestdataKeys empty() {
        return new MobileTestdataKeys(null);
    }

    /** Test hook: every typed step resolves to {@code key} so conflict reject can be proven. */
    public static MobileTestdataKeys forcingKey(String key) {
        return new MobileTestdataKeys(key);
    }

    public static MobileTestdataKeys assign(List<MobileCaseIr> cases, Set<String> loginTcIds) {
        MobileTestdataKeys keys = empty();
        if (cases == null) {
            return keys;
        }
        Set<String> login = loginTcIds == null ? Set.of() : loginTcIds;
        for (MobileCaseIr c : cases) {
            if (c == null) {
                continue;
            }
            String phase = login.contains(c.tcId()) ? PHASE_LOGIN : PHASE_BODY;
            keys.indexCase(c, phase);
        }
        return keys;
    }

    public boolean isSecretKey(String key) {
        return secretKeys.contains(key);
    }

    public Map<String, String> valuesByKey() {
        return Map.copyOf(valuesByKey);
    }

    public Set<String> secretKeys() {
        return Set.copyOf(secretKeys);
    }

    /**
     * Resolve property key for a typed step. SECRET_REF returns empty (runtime SecretResolver);
     * LITERAL/DATA_REF allocate owner+phase keys.
     */
    public String keyFor(String ownerTcId, String emitPhase, MobileStep step) {
        if (step == null || step.operation() != MobileOperation.TYPE) {
            return keyForAssertOrCapture(ownerTcId, emitPhase, step);
        }
        MobileInput input = step.input();
        if (input == null) {
            return "";
        }
        if (MobileCodeSecrets.isSecretKind(input.kind())) {
            // Secret refs stay out of testdata properties — runtime SecretResolver only.
            secretKeys.add(input.key() == null ? "" : input.key().trim());
            return "";
        }
        allocatingSecret = Boolean.TRUE.equals(input.sensitive());
        try {
            return keyForTyped(ownerTcId, emitPhase, step, input);
        } finally {
            allocatingSecret = false;
        }
    }

    private String keyForAssertOrCapture(String ownerTcId, String emitPhase, MobileStep step) {
        if (step == null) {
            return "";
        }
        if (step.operation() == MobileOperation.ASSERT
                && step.assertType() != null
                && step.assertType().toLowerCase(Locale.ROOT).contains("text")
                && step.input() != null
                && step.input().value() != null
                && !step.input().value().isBlank()) {
            return allocate(
                    ownerId(ownerTcId, step),
                    phaseOf(emitPhase),
                    "assertExpected",
                    fingerprint(step, "assertExpected"),
                    step.input().value());
        }
        return "";
    }

    private String keyForTyped(String ownerTcId, String emitPhase, MobileStep step, MobileInput input) {
        if ("DATA_REF".equals(input.kind()) && input.key() != null && !input.key().isBlank()) {
            // Keep explicit DATA_REF key as-is (still owner-scoped collision check via values map).
            String explicit = input.key().trim();
            String value = input.value() == null ? "" : input.value();
            if (!value.isBlank()) {
                String previous = valuesByKey.put(explicit, value);
                if (previous != null && !previous.equals(value)) {
                    throw conflict(explicit, previous, value);
                }
            }
            return explicit;
        }
        String value = input.value() == null ? "" : input.value();
        if (value.isBlank() && !"DATA_REF".equals(input.kind())) {
            return "";
        }
        if (forcedKey != null && !forcedKey.isBlank()) {
            String previous = valuesByKey.put(forcedKey, value);
            if (previous != null && !previous.equals(value)) {
                throw conflict(forcedKey, previous, value);
            }
            if (allocatingSecret) {
                secretKeys.add(forcedKey);
            }
            return forcedKey;
        }
        String owner = ownerId(ownerTcId, step);
        String method = methodPart(step);
        String fingerprint = fingerprint(step, method);
        if (PHASE_SETUP.equals(emitPhase)) {
            String found = lookup(owner, PHASE_BODY, fingerprint);
            if (found == null) {
                found = lookup(owner, PHASE_LOGIN, fingerprint);
            }
            if (found != null) {
                return found;
            }
            return allocate(owner, PHASE_BODY, method, fingerprint, value);
        }
        String phase = PHASE_LOGIN.equals(emitPhase) ? PHASE_LOGIN : PHASE_BODY;
        String found = lookup(owner, phase, fingerprint);
        if (found != null) {
            return found;
        }
        return allocate(owner, phase, method, fingerprint, value);
    }

    private void indexCase(MobileCaseIr c, String phase) {
        if (c.steps() == null) {
            return;
        }
        for (MobileStep step : c.steps()) {
            if (step != null && step.operation() == MobileOperation.TYPE) {
                keyFor(c.tcId(), phase, step);
            }
        }
    }

    private String allocate(String ownerId, String phase, String method, String fingerprint, String value) {
        String id = sanitizeId(ownerId);
        String methodPart = method == null || method.isBlank() ? "value" : method.trim();
        String base = id + "." + phase + "." + methodPart;
        int n = counts.merge(base, 1, Integer::sum);
        String key = base + "." + n;
        slots.put(slotId(ownerId, phase, fingerprint), key);
        String previous = valuesByKey.put(key, value);
        if (previous != null && !previous.equals(value)) {
            throw conflict(key, previous, value);
        }
        if (allocatingSecret) {
            secretKeys.add(key);
        }
        return key;
    }

    private IllegalStateException conflict(String key, String previous, String value) {
        boolean redact = secretKeys.contains(key) || allocatingSecret;
        return new IllegalStateException(
                "Conflicting test data for key '" + key + "': already '"
                        + MobileCodeSecrets.displayValue(previous, redact)
                        + "', new '"
                        + MobileCodeSecrets.displayValue(value, redact)
                        + "'");
    }

    private String lookup(String ownerId, String phase, String fingerprint) {
        return slots.get(slotId(ownerId, phase, fingerprint));
    }

    private static String slotId(String ownerId, String phase, String fingerprint) {
        return sanitizeId(ownerId) + '\0' + phase + '\0' + fingerprint;
    }

    private static String fingerprint(MobileStep step, String method) {
        String strat = step.target() == null ? "" : Objects.toString(step.target().strategy(), "");
        String tval = step.target() == null ? "" : Objects.toString(step.target().value(), "");
        String ival = step.input() == null ? "" : Objects.toString(step.input().value(), "");
        return Objects.toString(method, "") + '\0' + strat + '\0' + tval + '\0' + ival;
    }

    private static String methodPart(MobileStep step) {
        String name = MobileCodegenNaming.actionMethodName(step, null);
        if (name == null || name.isBlank()) {
            return "type_value";
        }
        return name;
    }

    private static String ownerId(String fallbackTcId, MobileStep step) {
        return fallbackTcId == null || fallbackTcId.isBlank() ? "TC" : fallbackTcId;
    }

    private static String phaseOf(String emitPhase) {
        if (PHASE_LOGIN.equals(emitPhase)) {
            return PHASE_LOGIN;
        }
        if (PHASE_SETUP.equals(emitPhase)) {
            return PHASE_SETUP;
        }
        return PHASE_BODY;
    }

    static String sanitizeId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return "TC";
        }
        return ownerId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
