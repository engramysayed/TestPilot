package delivery.vision;

import delivery.authoring.LocalLlmClient;
import delivery.authoring.StepIntentBinder;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.PropertyReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class QwenVisionProvider implements VisionGroundingProvider {

    static final String SYSTEM_PROMPT = VisionPromptTemplate.systemPrompt();

    static final String ASSERT_SYSTEM_PROMPT =
            "Return JSON only. No markdown. Schema:\n"
            + "{\"status\":\"PASS|FAIL|UNCERTAIN\",\"confidence\":0.9,"
            + "\"observation\":\"concrete UI text and controls visible in the screenshot\","
            + "\"evidence\":\"why the status matches the assertion\"}\n"
            + "status must be PASS, FAIL, or UNCERTAIN.\n"
            + "Judge this screenshot only. Do not compare to Figma or a reference image.\n"
            + "Do not invent locators or CSS selectors.\n"
            + "Do not copy placeholder angle-bracket text into observation or evidence.\n"
            + "Never set observation or evidence to schema examples like \"what is visible\" or \"why\".\n"
            + "observation and evidence must be concrete visible UI facts; never leave them blank when status is PASS or FAIL.\n"
            + "confidence is in 0 to 1; use values like 0.7–0.99 (do not default to 0).";

    private static final String ASSERT_RETRY_NUDGE =
            "Return the full JSON again with non-empty observation and evidence. "
                    + "confidence must be 0.7–0.99 for PASS or FAIL. No angle-bracket placeholders.";

    /** Fits ~4GB laptop GPUs (e.g. RTX A2000). Override with delivery.vision.model. */
    private static final String DEFAULT_MODEL = "qwen2.5vl:3b";

    private final LocalLlmClient client;
    private final String unavailableReason;
    private volatile String lastAnalyzeRaw;
    private volatile List<VisualCandidate> lastPreGateCandidates = List.of();

    public QwenVisionProvider(LocalLlmClient client) {
        this.client = client;
        this.unavailableReason = null;
    }

    String lastAnalyzeRaw() {
        return lastAnalyzeRaw;
    }

    List<VisualCandidate> lastPreGateCandidates() {
        return lastPreGateCandidates;
    }

    private QwenVisionProvider(String unavailableReason) {
        this.client = null;
        this.unavailableReason = unavailableReason;
    }

    static QwenVisionProvider fromConfig() {
        return fromConfig(resolveModel());
    }

    static QwenVisionProvider fromConfig(String modelOverride) {
        String baseUrl = resolveBaseUrl();
        if (baseUrl.isBlank()) {
            return unavailable("vision LLM base URL not configured");
        }
        String model = modelOverride == null || modelOverride.isBlank() ? resolveModel() : modelOverride.trim();
        if (model.isBlank()) {
            return unavailable("vision model not configured");
        }
        try {
            return new QwenVisionProvider(new LocalLlmClient(baseUrl, model));
        } catch (IllegalArgumentException e) {
            return unavailable(e.getMessage() == null ? "vision provider unavailable" : e.getMessage());
        }
    }

    @Override
    public VisionAnalysisResult analyze(byte[] screenshotPng, StepIntentBinder.IntentLine intent) {
        if (client == null) {
            return VisionAnalysisResult.unavailable(unavailableReason);
        }
        if (intent == null) {
            return VisionAnalysisResult.empty();
        }
        String user = VisionPromptTemplate.userPrompt(intent);
        try {
            // Full-res: downscale made Qwen bbox centers miss the Login control.
            int[] dims = PngDimensions.read(screenshotPng);
            int imageW = dims[0];
            int imageH = dims[1];
            String raw = client.completeJson(SYSTEM_PROMPT, user, screenshotPng);
            lastAnalyzeRaw = raw;
            VisionAnalysisResult parsed = parseResponse(raw);
            if (parsed.error() != null && !parsed.error().isBlank() && parsed.candidates().isEmpty()) {
                lastPreGateCandidates = List.of();
                return parsed;
            }
            lastPreGateCandidates = parsed.candidates();
            var normalized = parsed.candidates().stream()
                    .map(c -> {
                        if (c.confidence() >= 0.5 && c.confidence() < VisionBboxQualityGate.MIN_CONFIDENCE
                                && c.boundingBox() != null
                                && c.boundingBox().width() >= VisionBboxQualityGate.MIN_SIDE_PX
                                && c.boundingBox().height() >= VisionBboxQualityGate.MIN_SIDE_PX) {
                            return new VisualCandidate(c.description(), c.boundingBox(), 0.75);
                        }
                        return c;
                    })
                    .toList();
            var ok = VisionBboxQualityGate.filter(normalized, imageW, imageH);
            if (ok.isEmpty() && !normalized.isEmpty()) {
                var salvaged = normalized.stream()
                        .map(c -> VisionBboxQualityGate.shrinkOversizedToCenterPad(c, imageW, imageH))
                        .filter(java.util.Objects::nonNull)
                        .toList();
                ok = VisionBboxQualityGate.filter(salvaged, imageW, imageH);
            }
            if (ok.isEmpty() && !normalized.isEmpty()) {
                return VisionAnalysisResult.unavailable(
                        "all candidates rejected by VisionBboxQualityGate (raw had "
                                + normalized.size() + "): " + normalized.get(0));
            }
            return new VisionAnalysisResult(!ok.isEmpty(), ok, parsed.error());
        } catch (IOException e) {
            return VisionAnalysisResult.unavailable(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VisionAnalysisResult.unavailable(e.getMessage());
        }
    }

    @Override
    public VisionAssertionResult assertVisual(
            byte[] screenshotPng,
            String assertionText,
            String referenceImagePathOrNull) {
        if (client == null) {
            return VisionAssertionResult.uncertain(
                    unavailableReason == null ? "vision provider unavailable" : unavailableReason);
        }
        String text = assertionText == null ? "" : assertionText.trim();
        if (text.isBlank()) {
            return VisionAssertionResult.uncertain("empty visual assertion text");
        }
        String user = "Visual assertion (judge this screenshot only):\n" + text;
        try {
            String raw = client.completeJson(ASSERT_SYSTEM_PROMPT, user, screenshotPng);
            VisionAssertionResult first = VisionAssertionParser.parse(raw);
            if (!needsAssertRetry(first)) {
                return first;
            }
            String retryUser = user + "\n\n" + ASSERT_RETRY_NUDGE;
            String raw2 = client.completeJson(ASSERT_SYSTEM_PROMPT, retryUser, screenshotPng);
            return VisionAssertionParser.parse(raw2);
        } catch (IOException e) {
            return VisionAssertionResult.uncertain(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VisionAssertionResult.uncertain(e.getMessage());
        }
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
                    || result.confidence() < 0.7;
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

    public static VisionAnalysisResult parseResponse(String json) {
        if (json == null || json.isBlank()) {
            return VisionAnalysisResult.unavailable("empty vision response");
        }
        try {
            JSONObject root = new JSONObject(json.trim());
            boolean found = root.optBoolean("found", false);
            JSONArray arr = root.optJSONArray("candidates");
            if (arr == null || arr.length() == 0) {
                return new VisionAnalysisResult(found, List.of(), null);
            }
            List<VisualCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject bbox = item.optJSONObject("bbox");
                if (bbox == null) {
                    continue;
                }
                int w = bbox.optInt("width");
                int h = bbox.optInt("height");
                // Some VLMs return a point (w/h=0); keep a 1×1 so center == (x,y) for elementFromPoint.
                if (w <= 0) {
                    w = 1;
                }
                if (h <= 0) {
                    h = 1;
                }
                double conf = item.has("confidence") ? item.optDouble("confidence", 0.5) : 0.5;
                if (Double.isNaN(conf) || conf < 0) {
                    conf = 0.5;
                } else if (conf > 1) {
                    conf = 1;
                }
                candidates.add(new VisualCandidate(
                        item.optString("description", ""),
                        new BoundingBox(
                                bbox.optInt("x"),
                                bbox.optInt("y"),
                                w,
                                h),
                        conf));
            }
            if (candidates.isEmpty()) {
                return new VisionAnalysisResult(found, List.of(), null);
            }
            return new VisionAnalysisResult(found || !candidates.isEmpty(), List.copyOf(candidates), null);
        } catch (Exception e) {
            return VisionAnalysisResult.unavailable(e.getMessage());
        }
    }

    private static QwenVisionProvider unavailable(String reason) {
        return new QwenVisionProvider(reason);
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
