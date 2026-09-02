package delivery.vision;

import utils.PropertyReader;

public final class VisionGroundingConfig {

    private static final String DEFAULT_GROUNDING_PROVIDER = "uitars";
    private static final String DEFAULT_ASSERT_PROVIDER = "qwen";
    private static final String DEFAULT_UITARS_MODEL = "ui-tars";
    private static final String DEFAULT_QWEN_MODEL = "qwen2.5vl:3b";

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

    public static String assertProviderId() {
        String split = firstProp("delivery.vision.assert.provider");
        if (split != null && !split.isBlank()) {
            return split.trim();
        }
        String legacy = firstProp("delivery.vision.provider");
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }
        return DEFAULT_ASSERT_PROVIDER;
    }

    public static String assertModel() {
        String split = firstProp("delivery.vision.assert.model");
        if (split != null && !split.isBlank()) {
            return split.trim();
        }
        String legacyModel = firstProp("delivery.vision.model");
        String legacyProvider = firstProp("delivery.vision.provider");
        String assertProv = assertProviderId();
        // Only inherit legacy model when assert provider matches explicit legacy provider.
        if (legacyModel != null && !legacyModel.isBlank()
                && legacyProvider != null && !legacyProvider.isBlank()
                && assertProv.equalsIgnoreCase(legacyProvider.trim())) {
            return legacyModel.trim();
        }
        return defaultModelFor(assertProv);
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
