package delivery.vision;

import delivery.excel.ManualTestCase;
import utils.LogsManager;

public final class VisionAssertionGate {

    private VisionAssertionGate() {
    }

    public static boolean shouldRun(ManualTestCase tc) {
        if (!VisionGroundingConfig.assertionsEnabled() || tc == null) {
            return false;
        }
        String text = tc.visualAssertion();
        return text != null && !text.isBlank();
    }

    public static VisionAssertionResult evaluate(
            ManualTestCase tc,
            byte[] png,
            VisionGroundingProvider provider) {
        return evaluate(tc, png, provider, null);
    }

    public static VisionAssertionResult evaluate(
            ManualTestCase tc,
            byte[] png,
            VisionGroundingProvider provider,
            String currentUrl) {
        if (!shouldRun(tc)) {
            return null;
        }
        if (png == null || png.length == 0) {
            return VisionAssertionResult.uncertain("missing screenshot");
        }
        if (provider == null) {
            return VisionAssertionResult.uncertain("missing vision provider");
        }
        try {
            VisionAssertionResult result = provider.assertVisual(png, tc.visualAssertion(), null);
            if (result == null) {
                return VisionAssertionResult.uncertain("empty vision assertion result");
            }
            return honestyCheck(result, tc.visualAssertion(), currentUrl);
        } catch (RuntimeException e) {
            logError(e);
            return VisionAssertionResult.uncertain(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public static final double MIN_PASS_CONFIDENCE = 0.7;
    public static final int MIN_PASS_OBSERVATION_LEN = 20;

    static VisionAssertionResult honestyCheck(
            VisionAssertionResult result, String assertionText, String currentUrl) {
        if (result == null || result.status() != VisionAssertionStatus.PASS) {
            return result;
        }
        if (result.confidence() < MIN_PASS_CONFIDENCE) {
            return VisionAssertionResult.uncertain("visual PASS confidence below "
                    + MIN_PASS_CONFIDENCE + ": " + result.confidence());
        }
        if (isPromptPlaceholder(result.observation()) || isPromptPlaceholder(result.evidence())) {
            return VisionAssertionResult.uncertain("visual PASS used prompt placeholder text");
        }
        String obs = result.observation() == null ? "" : result.observation().trim();
        String ev = result.evidence() == null ? "" : result.evidence().trim();
        if (obs.length() < MIN_PASS_OBSERVATION_LEN) {
            return VisionAssertionResult.uncertain(
                    "visual PASS observation too short (" + obs.length() + " chars)");
        }
        if (ev.length() < 8) {
            return VisionAssertionResult.uncertain("visual PASS evidence too short");
        }
        if (echoesAssertion(result.observation(), assertionText)
                || echoesAssertion(result.evidence(), assertionText)) {
            return VisionAssertionResult.uncertain("visual PASS echoed the assertion text");
        }
        if (looksLikeRegistrationClaim(assertionText) && looksLikeLoginUrl(currentUrl)) {
            return VisionAssertionResult.of(
                    VisionAssertionStatus.FAIL, result.confidence(),
                    result.observation(), "page URL is login while assertion is registration");
        }
        return result;
    }

    /** Detects schema-example / prompt placeholder echoes (e.g. last SauceDemo run). */
    static boolean isPromptPlaceholder(String text) {
        if (text == null) {
            return true;
        }
        String t = text.trim().toLowerCase();
        if (t.isEmpty()) {
            return true;
        }
        if (t.startsWith("<") && t.endsWith(">")) {
            return true;
        }
        return t.equals("what is visible")
                || t.equals("why")
                || t.equals("currentobservation")
                || t.equals("what is visible now on the page")
                || t.contains("describe concrete ui")
                || t.contains("<describe");
    }

    static boolean echoesAssertion(String observation, String assertionText) {
        String obs = normalize(observation);
        String claim = normalize(assertionText);
        if (obs.isBlank() || claim.isBlank() || obs.length() < 12) {
            return false;
        }
        if (obs.equals(claim) || (claim.length() >= 20 && obs.contains(claim))) {
            return true;
        }
        return paraphrasesClaim(obs, claim);
    }

    /** High token overlap with little extra evidence — the model restated Excel instead of looking. */
    static boolean paraphrasesClaim(String observation, String claim) {
        java.util.Set<String> claimTokens = significantTokens(claim);
        java.util.Set<String> obsTokens = significantTokens(observation);
        if (claimTokens.size() < 4 || obsTokens.isEmpty()) {
            return false;
        }
        long hit = claimTokens.stream().filter(obsTokens::contains).count();
        double overlap = hit / (double) claimTokens.size();
        // Only treat as echo when observation barely adds anything beyond the claim wording.
        return overlap >= 0.9
                && obsTokens.size() <= claimTokens.size() + 1
                && observation.length() <= claim.length() + 24;
    }

    private static java.util.Set<String> significantTokens(String normalized) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (normalized == null || normalized.isBlank()) {
            return out;
        }
        for (String t : normalized.split(" ")) {
            if (t.length() >= 4) {
                out.add(t);
            }
        }
        return out;
    }

    public static boolean looksLikeRegistrationClaim(String assertionText) {
        String t = assertionText == null ? "" : assertionText.toLowerCase();
        return t.contains("registration") || t.contains("sign up") || t.contains("signup")
                || t.contains("create account") || t.contains("first name")
                || t.contains("register");
    }

    public static boolean looksLikeLoginUrl(String currentUrl) {
        if (currentUrl == null || currentUrl.isBlank()) {
            return false;
        }
        String path;
        try {
            path = java.net.URI.create(currentUrl.trim()).getPath();
        } catch (IllegalArgumentException e) {
            path = currentUrl;
        }
        if (path == null) {
            path = currentUrl;
        }
        String p = path.toLowerCase();
        return p.contains("/login") || p.contains("/signin") || p.contains("/sign-in");
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static void logError(RuntimeException e) {
        StringBuilder sb = new StringBuilder("VISION_ASSERT: ");
        sb.append(e);
        for (StackTraceElement el : e.getStackTrace()) {
            sb.append('\n').append(el);
        }
        LogsManager.error(sb.toString());
    }
}
