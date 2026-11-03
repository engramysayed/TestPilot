package delivery.vision;

import delivery.authoring.LocalLlmClient;
import delivery.prompts.PromptCatalog;
import org.json.JSONObject;

import java.io.IOException;

public final class DesignCompareGate {

    static final String SYSTEM_PROMPT = PromptCatalog.get("vision/design-compare").trim();

    private static final String USER_PROMPT =
            "Compare the design reference (first image) to the actual UI (second image).";

    private DesignCompareGate() {
    }

    public static DesignCompareResult compare(byte[] referencePng, byte[] actualPng, LocalLlmClient client) {
        if (referencePng == null || referencePng.length == 0) {
            return DesignCompareResult.uncertain("missing reference image");
        }
        if (actualPng == null || actualPng.length == 0) {
            return DesignCompareResult.uncertain("missing actual screenshot");
        }
        if (client == null) {
            return DesignCompareResult.uncertain("missing vision client");
        }
        try {
            String raw = client.completeJson(SYSTEM_PROMPT, USER_PROMPT, referencePng, actualPng);
            return parse(raw);
        } catch (IOException e) {
            return DesignCompareResult.uncertain(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DesignCompareResult.uncertain(e.getMessage());
        }
    }

    static DesignCompareResult parse(String json) {
        if (json == null || json.isBlank()) {
            return DesignCompareResult.uncertain("empty design compare response");
        }
        try {
            JSONObject root = extractObject(json.trim());
            if (root == null) {
                return DesignCompareResult.uncertain("empty design compare response");
            }
            DesignCompareStatus status = parseStatus(root.optString("status", ""));
            double conf = root.has("confidence") ? root.optDouble("confidence", 0.5) : 0.5;
            if (Double.isNaN(conf) || conf < 0) {
                conf = 0.5;
            } else if (conf > 1) {
                conf = 1;
            }
            String observation = cap(root.optString("observation", ""), 500);
            String evidence = cap(root.optString("evidence", ""), 500);
            return new DesignCompareResult(status, conf, observation, evidence, null);
        } catch (RuntimeException e) {
            return DesignCompareResult.uncertain(
                    e.getMessage() == null ? "invalid design compare JSON" : e.getMessage());
        }
    }

    private static DesignCompareStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return DesignCompareStatus.UNCERTAIN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "MATCH" -> DesignCompareStatus.MATCH;
            case "MISMATCH" -> DesignCompareStatus.MISMATCH;
            case "UNCERTAIN" -> DesignCompareStatus.UNCERTAIN;
            default -> DesignCompareStatus.UNCERTAIN;
        };
    }

    private static JSONObject extractObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return new JSONObject(text.substring(start, end + 1));
    }

    private static String cap(String value, int max) {
        String s = value == null ? "" : value.trim();
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
