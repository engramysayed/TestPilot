package delivery.vision;

import org.json.JSONObject;

final class VisionAssertionParser {

    private VisionAssertionParser() {
    }

    static VisionAssertionResult parse(String json) {
        if (json == null || json.isBlank()) {
            return VisionAssertionResult.uncertain("empty vision assertion response");
        }
        try {
            JSONObject root = extractObject(json.trim());
            if (root == null) {
                return VisionAssertionResult.uncertain("empty vision assertion response");
            }
            VisionAssertionStatus status = parseStatus(root.optString("status", ""));
            double conf = root.has("confidence") ? root.optDouble("confidence", 0.5) : 0.5;
            if (Double.isNaN(conf) || conf < 0) {
                conf = 0.5;
            } else if (conf > 1) {
                conf = 1;
            }
            String observation = cap(root.optString("observation", ""), 500);
            String evidence = cap(root.optString("evidence", ""), 500);
            return VisionAssertionResult.of(status, conf, observation, evidence);
        } catch (RuntimeException e) {
            return VisionAssertionResult.uncertain(
                    e.getMessage() == null ? "invalid vision assertion JSON" : e.getMessage());
        }
    }

    private static VisionAssertionStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return VisionAssertionStatus.UNCERTAIN;
        }
        return switch (raw.trim().toUpperCase()) {
            case "PASS" -> VisionAssertionStatus.PASS;
            case "FAIL" -> VisionAssertionStatus.FAIL;
            case "UNCERTAIN" -> VisionAssertionStatus.UNCERTAIN;
            default -> VisionAssertionStatus.UNCERTAIN;
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
