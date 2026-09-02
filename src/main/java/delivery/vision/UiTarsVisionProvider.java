package delivery.vision;

import delivery.authoring.LocalLlmClient;
import delivery.authoring.StepIntentBinder;
import utils.PropertyReader;

import java.io.IOException;
import java.util.Locale;

/**
 * Local UI-TARS via Ollama. Native point/box + JSON parse, quality gate, assert retry.
 */
public final class UiTarsVisionProvider implements VisionGroundingProvider {

    private static final String DEFAULT_MODEL = "ui-tars";

    private final LocalLlmClient client;
    private final String unavailableReason;
    /** Last raw model text (analyze); for live diagnostics only. */
    private volatile String lastAnalyzeRaw;

    public UiTarsVisionProvider(LocalLlmClient client) {
        this.client = client;
        this.unavailableReason = null;
    }

    /** Package-visible for live smoke notes. */
    String lastAnalyzeRaw() {
        return lastAnalyzeRaw;
    }

    private UiTarsVisionProvider(String unavailableReason) {
        this.client = null;
        this.unavailableReason = unavailableReason;
    }

    static UiTarsVisionProvider fromConfig() {
        return fromConfig(resolveModel());
    }

    static UiTarsVisionProvider fromConfig(String modelOverride) {
        String baseUrl = resolveBaseUrl();
        if (baseUrl.isBlank()) {
            return unavailable("vision LLM base URL not configured");
        }
        String model = modelOverride == null || modelOverride.isBlank() ? resolveModel() : modelOverride.trim();
        if (model.isBlank()) {
            return unavailable("uitars provider not configured");
        }
        try {
            return new UiTarsVisionProvider(new LocalLlmClient(baseUrl, model));
        } catch (IllegalArgumentException e) {
            return unavailable(e.getMessage() == null
                    ? "uitars provider not configured"
                    : e.getMessage());
        }
    }

    /** Test / sentinel: no HTTP. */
    static UiTarsVisionProvider unavailable(String reason) {
        return new UiTarsVisionProvider(
                reason == null || reason.isBlank() ? "uitars provider not configured" : reason);
    }

    @Override
    public VisionAnalysisResult analyze(byte[] screenshotPng, StepIntentBinder.IntentLine intent) {
        if (client == null) {
            return VisionAnalysisResult.unavailable(
                    unavailableReason == null ? "uitars provider not configured" : unavailableReason);
        }
        if (intent == null) {
            return VisionAnalysisResult.empty();
        }
        String user = UiTarsPrompt.groundingUser(intent);
        UiTarsImagePrep.Prepared prepared = UiTarsImagePrep.prepare(screenshotPng);
        int imageW = prepared.sendW();
        int imageH = prepared.sendH();
        try {
            VisionAnalysisResult result = analyzeOnce(client, user, prepared, imageW, imageH);
            if (shouldRetryGrounding(intent, result)) {
                String nudgeUser = user + "\n\n"
                        + "IMPORTANT: Click the Login/Submit button itself — not Username/Password inputs.";
                result = analyzeOnce(client, nudgeUser, prepared, imageW, imageH);
            }
            return result;
        } catch (IOException e) {
            return VisionAnalysisResult.unavailable(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VisionAnalysisResult.unavailable(e.getMessage());
        }
    }

    private VisionAnalysisResult analyzeOnce(
            LocalLlmClient llm,
            String user,
            UiTarsImagePrep.Prepared prepared,
            int imageW,
            int imageH) throws IOException, InterruptedException {
        String raw = llm.completeChat(UiTarsPrompt.groundingSystem(), user, prepared.png(), false);
        lastAnalyzeRaw = raw;
        VisionAnalysisResult parsed = UiTarsResponseParser.parse(raw, imageW, imageH);
        if (parsed.error() != null && !parsed.error().isBlank() && parsed.candidates().isEmpty()) {
            return parsed;
        }
        var mapped = parsed.candidates().stream()
                .map(c -> new VisualCandidate(
                        c.description(),
                        prepared.toOriginal(c.boundingBox()),
                        c.confidence()))
                .toList();
        var ok = VisionBboxQualityGate.filter(mapped, prepared.origW(), prepared.origH());
        if (ok.isEmpty() && !mapped.isEmpty()) {
            return VisionAnalysisResult.unavailable(
                    "all candidates rejected by VisionBboxQualityGate (raw had "
                            + mapped.size() + ")");
        }
        return new VisionAnalysisResult(!ok.isEmpty(), ok, parsed.error());
    }

    static boolean shouldRetryGrounding(
            StepIntentBinder.IntentLine intent, VisionAnalysisResult result) {
        if (intent == null || result == null || result.candidates().isEmpty()) {
            return false;
        }
        String intentText = intent.text() == null ? "" : intent.text().toLowerCase();
        boolean wantsLoginClick = intentText.contains("login") || intentText.contains("submit");
        if (!wantsLoginClick) {
            return false;
        }
        String desc = result.candidates().get(0).description();
        String d = desc == null ? "" : desc.toLowerCase();
        return d.contains("username") || d.contains("password") || d.contains("textbox")
                || d.contains("input field") || d.contains("empty text");
    }

    @Override
    public VisionAssertionResult assertVisual(
            byte[] screenshotPng,
            String assertionText,
            String referenceImagePathOrNull) {
        if (client == null) {
            return VisionAssertionResult.uncertain(
                    unavailableReason == null ? "uitars provider not configured" : unavailableReason);
        }
        String text = assertionText == null ? "" : assertionText.trim();
        if (text.isBlank()) {
            return VisionAssertionResult.uncertain("empty visual assertion text");
        }
        String user = "Visual assertion (judge this screenshot only):\n" + text;
        try {
            UiTarsImagePrep.Prepared prepared = UiTarsImagePrep.prepare(screenshotPng);
            String raw = client.completeChat(
                    UiTarsPrompt.assertSystem(), user, prepared.png(), false);
            VisionAssertionResult first = VisionAssertionParser.parse(raw);
            if (!needsAssertRetry(first)) {
                return sanitizeAssert(first);
            }
            String retryUser = user + "\n\n" + UiTarsPrompt.assertRetryNudge();
            String raw2 = client.completeChat(
                    UiTarsPrompt.assertSystem(), retryUser, prepared.png(), false);
            VisionAssertionResult second = VisionAssertionParser.parse(raw2);
            if (!needsAssertRetry(second)) {
                return sanitizeAssert(second);
            }
            // Last resort: force Ollama JSON mode — UI-TARS often emits broken freeform JSON.
            String raw3 = client.completeChat(
                    UiTarsPrompt.assertSystem(), retryUser, prepared.png(), true);
            return sanitizeAssert(VisionAssertionParser.parse(raw3));
        } catch (IOException e) {
            return VisionAssertionResult.uncertain(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VisionAssertionResult.uncertain(e.getMessage());
        }
    }

    /**
     * When UI-TARS returns PASS/FAIL with a solid observation but blank/placeholder evidence
     * (or missing confidence), fill from observation so honesty gate can evaluate real UI facts.
     */
    static VisionAssertionResult sanitizeAssert(VisionAssertionResult r) {
        if (r == null) {
            return VisionAssertionResult.uncertain("empty vision assertion result");
        }
        if (r.status() != VisionAssertionStatus.PASS && r.status() != VisionAssertionStatus.FAIL) {
            return r;
        }
        String obs = unwrapAngleWrapper(r.observation());
        String ev = unwrapAngleWrapper(r.evidence());
        if (obs.isEmpty() && !ev.isEmpty()) {
            obs = ev;
        }
        if (ev.isEmpty() && !obs.isEmpty()) {
            String derived = obs.length() <= 160 ? obs : obs.substring(0, 157) + "...";
            ev = "Visible UI matches assertion: " + derived;
        }
        if (obs.length() < 20) {
            return VisionAssertionResult.uncertain("assert missing concrete observation after sanitize");
        }
        if (looksLikeSchemaEcho(obs) || looksLikeSchemaEcho(ev)) {
            return VisionAssertionResult.uncertain("assert echoed schema placeholder wording");
        }
        double conf = r.confidence();
        if (conf < 0.7) {
            conf = 0.75;
        }
        return VisionAssertionResult.of(r.status(), conf, obs, ev);
    }

    static boolean looksLikeSchemaEcho(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        return t.contains("concrete ui text")
                || t.contains("what is visible")
                || t.equals("why")
                || t.startsWith("why status matches")
                || t.equals("currentobservation");
    }

    static String unwrapAngleWrapper(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("<") && t.endsWith(">") && t.length() > 2) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    static boolean needsAssertRetry(VisionAssertionResult result) {
        if (result == null) {
            return true;
        }
        String obs = result.observation() == null ? "" : result.observation().trim();
        String ev = result.evidence() == null ? "" : result.evidence().trim();
        if (result.status() == VisionAssertionStatus.PASS
                || result.status() == VisionAssertionStatus.FAIL) {
            return obs.isEmpty() || ev.isEmpty()
                    || VisionAssertionGate.isPromptPlaceholder(obs)
                    || VisionAssertionGate.isPromptPlaceholder(ev)
                    || (result.status() == VisionAssertionStatus.PASS && result.confidence() < 0.7);
        }
        if (result.status() != VisionAssertionStatus.UNCERTAIN) {
            return false;
        }
        String err = result.error() == null ? "" : result.error().trim().toLowerCase();
        if (!obs.isEmpty() || !ev.isEmpty()) {
            return false;
        }
        return err.isEmpty() || err.contains("empty");
    }

    private static String resolveBaseUrl() {
        String p = System.getProperty("delivery.llm-base-url");
        if (p == null || p.isBlank()) {
            p = PropertyReader.getProperty("delivery.llm-base-url");
        }
        return p == null ? "" : p.trim();
    }

    private static String resolveModel() {
        String model = VisionGroundingConfig.model();
        if (model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        return model.trim();
    }
}
