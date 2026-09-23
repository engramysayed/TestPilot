package delivery.codegen;

import delivery.job.TcOutcome;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Package-wide data keys keyed by the original owner step (login vs body), not by the
 * leaf's emitted walk. Setup inlining looks up the prerequisite's canonical slot.
 */
final class CodegenDataKeys {
    static final String PHASE_LOGIN = "login";
    static final String PHASE_BODY = "body";
    static final String PHASE_SETUP = "setup";

    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final Map<String, String> slots = new LinkedHashMap<>();
    private final Map<String, String> valuesByKey = new LinkedHashMap<>();
    private final PageAccumulator pages;
    private final String forcedKey;

    private CodegenDataKeys(PageAccumulator pages) {
        this(pages, null);
    }

    private CodegenDataKeys(PageAccumulator pages, String forcedKey) {
        this.pages = pages;
        this.forcedKey = forcedKey;
    }

    /** Test hook: every typed/select step resolves to {@code key} so conflict reject can be proven. */
    static CodegenDataKeys forcingKey(PageAccumulator pages, String key) {
        return new CodegenDataKeys(pages == null ? new PageAccumulator() : pages, key);
    }

    static CodegenDataKeys assign(List<TcOutcome> outcomes, PageAccumulator pages) {
        CodegenDataKeys keys = new CodegenDataKeys(pages == null ? new PageAccumulator() : pages);
        if (outcomes == null) {
            return keys;
        }
        for (TcOutcome outcome : outcomes) {
            keys.indexList(outcome.tcId(), PHASE_LOGIN, outcome.loginSteps());
            keys.indexList(outcome.tcId(), PHASE_BODY, outcome.provenSteps());
        }
        return keys;
    }

    String keyFor(String fallbackTcId, String emitPhase, ProvenStep step) {
        String value = step.value() == null ? "" : step.value();
        String special = CodeWriter.specialPropKey(value);
        if (special != null) {
            return special;
        }
        if (value.isBlank()) {
            return "";
        }
        if (forcedKey != null && !forcedKey.isBlank()) {
            return forcedKey;
        }
        String owner = ownerId(fallbackTcId, step);
        String method = pages.actionSymbol(step);
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

    Map<String, String> valuesByKey() {
        return valuesByKey;
    }

    private void indexList(String tcId, String phase, List<ProvenStep> steps) {
        if (steps == null) {
            return;
        }
        for (ProvenStep step : steps) {
            String action = step.action() == null ? "" : step.action().toLowerCase(Locale.ROOT);
            if (!"type".equals(action) && !"select".equals(action)) {
                continue;
            }
            keyFor(tcId, phase, step);
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
            throw new IllegalStateException(
                    "Conflicting test data for key '" + key + "': already '" + previous + "', new '" + value + "'");
        }
        return key;
    }

    private String lookup(String ownerId, String phase, String fingerprint) {
        return slots.get(slotId(ownerId, phase, fingerprint));
    }

    private static String slotId(String ownerId, String phase, String fingerprint) {
        return sanitizeId(ownerId) + '\0' + phase + '\0' + fingerprint;
    }

    private static String fingerprint(ProvenStep step, String method) {
        return Objects.toString(method, "")
                + '\0'
                + Objects.toString(step.locatorStrategy(), "")
                + '\0'
                + Objects.toString(step.locatorValue(), "")
                + '\0'
                + Objects.toString(step.value(), "");
    }

    private static String ownerId(String fallbackTcId, ProvenStep step) {
        if (step.tcId() != null && !step.tcId().isBlank()) {
            return step.tcId();
        }
        return fallbackTcId == null || fallbackTcId.isBlank() ? "TC" : fallbackTcId;
    }

    private static String sanitizeId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return "TC";
        }
        return ownerId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Isolated helper for tests that still call {@link CodeWriter#propKeyFor}. */
    String next(String ownerId, String method, String value) {
        String special = CodeWriter.specialPropKey(value);
        if (special != null) {
            return special;
        }
        if (value == null || value.isBlank()) {
            return "";
        }
        String id = sanitizeId(ownerId);
        String methodPart = method == null || method.isBlank() ? "value" : method.trim();
        String base = id + "." + PHASE_BODY + "." + methodPart;
        int n = counts.merge(base, 1, Integer::sum);
        return base + "." + n;
    }
}
