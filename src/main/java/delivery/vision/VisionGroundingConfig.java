package delivery.vision;

import utils.PropertyReader;

public final class VisionGroundingConfig {

    private static final String DEFAULT_GROUNDING_PROVIDER = "uitars";
    private static final String DEFAULT_ASSERT_PROVIDER = "qwen";
    private static final String DEFAULT_UITARS_MODEL = "ui-tars";
    private static final String DEFAULT_QWEN_MODEL = AssertVisionProvider.QWEN7B_MODEL;

    private VisionGroundingConfig() {
    }

    public static boolean enabled() {
        return booleanProp("delivery.vision.grounding.enabled", false);
    }

    public static boolean assertionsEnabled() {
        return booleanProp("delivery.vision.assertions.enabled", false);
    }

    public static boolean healHintsEnabled() {
        String p = firstProp("delivery.vision.heal-hints.enabled");
        if (p == null || p.isBlank()) {
            return true;
        }
        String v = p.trim();
        return !"false".equalsIgnoreCase(v) && !"0".equals(v);
    }

    /** Legacy / grounding-oriented provider id (logs, heal). */
    public static String providerId() {
        return groundingProviderId();
    }

    /** Legacy / grounding-oriented model (logs, heal). */
    public static String model() {
        return groundingModel();
    }

    public static String groundingProviderId() {
        String split = firstProp("delivery.vision.grounding.provider");
        if (split != null && !split.isBlank()) {
            return split.trim();
        }
        String legacy = firstProp("delivery.vision.provider");
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }
        return DEFAULT_GROUNDING_PROVIDER;
    }

    public static String groundingModel() {
        String split = firstProp("delivery.vision.grounding.model");
        if (split != null && !split.isBlank()) {
            return split.trim();
        }
        String legacy = firstProp("delivery.vision.model");
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }
        return defaultModelFor(groundingProviderId());
    }

    /** Selected assertion model family ({@code delivery.vision.assert.provider}); see {@link AssertVisionProvider}. */
    public static AssertVisionProvider assertVisionProvider() {
        // per-job override > -D (any key) > application.properties > default uitars
        return AssertVisionProvider.resolveConfigured();
    }

    public static String assertProviderId() {
        return assertVisionProvider().engineId;
    }

    /**
     * Model for the selected assertion provider. {@code delivery.vision.assert.model} still overrides
     * the tag, except that a non-commercial qwen2.5vl:3b pin is replaced by the 7B default.
     */
    public static String assertModel() {
        AssertVisionProvider p = assertVisionProvider();
        String split = firstProp("delivery.vision.assert.model");
        if (split != null && !split.isBlank()) {
            String m = split.trim();
            boolean qwenTag = m.toLowerCase().startsWith("qwen");
            if (p == AssertVisionProvider.QWEN && qwenTag && !m.equalsIgnoreCase("qwen2.5vl:3b")) return m;
            if (p == AssertVisionProvider.UITARS && !qwenTag) return m;
        }
        return p.defaultModel;
    }

    public static VisionGroundingProvider createProvider() {
        if (!enabled()) {
            return new FakeVisionGroundingProvider();
        }
        return createLlmProvider(groundingProviderId(), groundingModel());
    }

    public static VisionGroundingProvider createAssertionProvider() {
        if (!assertionsEnabled()) {
            return new FakeVisionGroundingProvider();
        }
        return createLlmProvider(assertProviderId(), assertModel());
    }

    private static VisionGroundingProvider createLlmProvider(String providerId, String model) {
        String id = providerId == null ? "" : providerId.trim().toLowerCase();
        String m = model == null ? "" : model.trim();
        return switch (id) {
            case "qwen" -> QwenVisionProvider.fromConfig(m);
            case "uitars" -> UiTarsVisionProvider.fromConfig(m);
            default -> new FakeVisionGroundingProvider();
        };
    }

    private static String defaultModelFor(String providerId) {
        String id = providerId == null ? "" : providerId.trim().toLowerCase();
        return switch (id) {
            case "qwen" -> DEFAULT_QWEN_MODEL;
            case "uitars" -> DEFAULT_UITARS_MODEL;
            default -> DEFAULT_UITARS_MODEL;
        };
    }

    private static boolean booleanProp(String key, boolean defaultFalse) {
        String p = firstProp(key);
        if (p == null || p.isBlank()) {
            return defaultFalse;
        }
        return "true".equalsIgnoreCase(p.trim());
    }

    private static String firstProp(String key) {
        String p = System.getProperty(key);
        if (p == null || p.isBlank()) {
            p = PropertyReader.getProperty(key);
        }
        return p;
    }
}
