package delivery.vision;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses UI-TARS / Ollama grounding responses: canonical candidates JSON or native point/box shapes.
 * Never coerces zero-size boxes to 1×1.
 */
public final class UiTarsResponseParser {
    private static final int POINT_PAD = 12;

    private UiTarsResponseParser() {
    }

    public static VisionAnalysisResult parse(String raw) {
        return parse(raw, 0, 0);
    }

    public static VisionAnalysisResult parse(String raw, int imageW, int imageH) {
        if (raw == null || raw.isBlank()) {
            return VisionAnalysisResult.unavailable("empty vision response");
        }
        String text = stripFences(raw.trim());
        try {
            JSONObject root = extractObject(text);
            if (root != null) {
                List<VisualCandidate> fromCanonical = parseCandidatesArray(root);
                if (!fromCanonical.isEmpty()) {
                    return VisionAnalysisResult.of(fromCanonical);
                }
                VisualCandidate nativeHit = parseNative(root, imageW, imageH);
                if (nativeHit != null) {
                    return VisionAnalysisResult.of(List.of(nativeHit));
                }
            }
            VisualCandidate fromAction = parseActionText(text, imageW, imageH);
            if (fromAction != null) {
                return VisionAnalysisResult.of(List.of(fromAction));
            }
            if (looksLikeFinished(text)) {
                return VisionAnalysisResult.empty();
            }
            if (root == null) {
                return VisionAnalysisResult.unavailable("unparsed vision response");
            }
            return VisionAnalysisResult.empty();
        } catch (RuntimeException e) {
            VisualCandidate fromAction = parseActionText(text, imageW, imageH);
            if (fromAction != null) {
                return VisionAnalysisResult.of(List.of(fromAction));
            }
            return VisionAnalysisResult.unavailable(
                    e.getMessage() == null ? "invalid vision JSON" : e.getMessage());
        }
    }

    /** Native UI-TARS Action: click(start_box=…) / click(point=…) / click(x,y). */
    static VisualCandidate parseActionText(String text, int imageW, int imageH) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text;
        // click(start_box='<|box_start|>(x,y)<|box_end|>') or (x1,y1,x2,y2)
        java.util.regex.Matcher box = java.util.regex.Pattern.compile(
                        "(?is)(?:start_box|end_box|box)\\s*=\\s*['\"]?(?:<\\|box_start\\|>)?\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)"
                                + "(?:\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+))?\\s*\\)")
                .matcher(t);
        if (box.find()) {
            double x1 = Double.parseDouble(box.group(1));
            double y1 = Double.parseDouble(box.group(2));
            if (box.group(3) != null && box.group(4) != null) {
                double x2 = Double.parseDouble(box.group(3));
                double y2 = Double.parseDouble(box.group(4));
                BoundingBox b = xyxyToBox(x1, y1, x2, y2, imageW, imageH, true);
                if (b != null) {
                    return new VisualCandidate(descriptionFromAction(t), b, 0.85);
                }
            }
            return pointCandidateThousand(x1, y1, descriptionFromAction(t), 0.85, imageW, imageH);
        }
        java.util.regex.Matcher point = java.util.regex.Pattern.compile(
                        "(?is)<point>\\s*([0-9.]+)\\s+([0-9.]+)\\s*</point>")
                .matcher(t);
        if (point.find()) {
            return pointCandidateThousand(
                    Double.parseDouble(point.group(1)),
                    Double.parseDouble(point.group(2)),
                    descriptionFromAction(t),
                    0.85,
                    imageW,
                    imageH);
        }
        java.util.regex.Matcher clickXy = java.util.regex.Pattern.compile(
                        "(?is)\\bclick\\s*\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*\\)")
                .matcher(t);
        if (clickXy.find()) {
            return pointCandidateThousand(
                    Double.parseDouble(clickXy.group(1)),
                    Double.parseDouble(clickXy.group(2)),
                    descriptionFromAction(t),
                    0.85,
                    imageW,
                    imageH);
        }
        return null;
    }

    private static VisualCandidate pointCandidateThousand(
            double x, double y, String description, double conf, int imageW, int imageH) {
        double sx = x;
        double sy = y;
        if (imageW > 0 && imageH > 0) {
            sx = x / 1000.0 * imageW;
            sy = y / 1000.0 * imageH;
        }
        return pointCandidate(sx, sy, description, conf, 0, 0);
    }

    private static boolean looksLikeFinished(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("finished()") || lower.contains("action: finished");
    }

    private static String descriptionFromAction(String text) {
        java.util.regex.Matcher thought = java.util.regex.Pattern.compile(
                        "(?is)Thought:\\s*(.+?)(?:\\n|Action:|$)")
                .matcher(text);
        if (thought.find()) {
            String d = thought.group(1).trim();
            if (!d.isBlank() && d.length() <= 80) {
                return d;
            }
        }
        return "target";
    }

    private static List<VisualCandidate> parseCandidatesArray(JSONObject root) {
        JSONArray arr = root.optJSONArray("candidates");
        if (arr == null || arr.length() == 0) {
            return List.of();
        }
        List<VisualCandidate> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) {
                continue;
            }
            VisualCandidate c = fromCandidateObject(item);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static VisualCandidate fromCandidateObject(JSONObject item) {
        JSONObject bbox = item.optJSONObject("bbox");
        if (bbox != null) {
            int w = bbox.optInt("width", -1);
            int h = bbox.optInt("height", -1);
            if (w <= 0 || h <= 0) {
                return null;
            }
            return new VisualCandidate(
                    item.optString("description", ""),
                    new BoundingBox(bbox.optInt("x"), bbox.optInt("y"), w, h),
                    clampConf(item));
        }
        JSONArray xyxy = item.optJSONArray("bbox");
        if (xyxy != null && xyxy.length() >= 4) {
            BoundingBox box = fromXyxy(xyxy, 0, 0);
            if (box == null) {
                return null;
            }
            return new VisualCandidate(item.optString("description", ""), box, clampConf(item));
        }
        return null;
    }

    private static VisualCandidate parseNative(JSONObject root, int imageW, int imageH) {
        if (root.has("candidates")) {
            return null;
        }
        String description = root.optString("description", root.optString("label", "target"));
        double conf = clampConf(root);

        JSONArray start = root.optJSONArray("start_box");
        JSONArray end = root.optJSONArray("end_box");
        if (start != null && end != null && start.length() >= 2 && end.length() >= 2) {
            BoundingBox box = fromStartEnd(start, end, imageW, imageH);
            if (box != null) {
                return new VisualCandidate(description, box, conf);
            }
        }

        JSONArray bboxArr = root.optJSONArray("bbox");
        if (bboxArr != null && bboxArr.length() >= 4) {
            BoundingBox box = fromXyxy(bboxArr, imageW, imageH);
            if (box != null) {
                return new VisualCandidate(description, box, conf);
            }
        }

        JSONArray click = root.optJSONArray("click");
        if (click != null && click.length() >= 2) {
            return pointCandidate(click.optDouble(0), click.optDouble(1), description, conf, imageW, imageH);
        }
        JSONArray point = root.optJSONArray("point");
        if (point != null && point.length() >= 2) {
            return pointCandidate(point.optDouble(0), point.optDouble(1), description, conf, imageW, imageH);
        }
        if (root.has("x") && root.has("y") && !root.has("width") && !root.has("height")) {
            return pointCandidate(root.optDouble("x"), root.optDouble("y"), description, conf, imageW, imageH);
        }
        return null;
    }

    private static VisualCandidate pointCandidate(
            double x, double y, String description, double conf, int imageW, int imageH) {
        int cx = (int) Math.round(scaleIfNormalized(x, imageW));
        int cy = (int) Math.round(scaleIfNormalized(y, imageH));
        int pad = POINT_PAD;
        int left = Math.max(0, cx - pad);
        int top = Math.max(0, cy - pad);
        int width = pad * 2;
        int height = pad * 2;
        if (imageW > 0) {
            width = Math.min(width, Math.max(1, imageW - left));
        }
        if (imageH > 0) {
            height = Math.min(height, Math.max(1, imageH - top));
        }
        return new VisualCandidate(description, new BoundingBox(left, top, width, height), conf);
    }

    private static BoundingBox fromXyxy(JSONArray xyxy, int imageW, int imageH) {
        return xyxyToBox(xyxy.optDouble(0), xyxy.optDouble(1), xyxy.optDouble(2), xyxy.optDouble(3),
                imageW, imageH);
    }

    private static BoundingBox xyxyToBox(
            double x1, double y1, double x2, double y2, int imageW, int imageH) {
        return xyxyToBox(x1, y1, x2, y2, imageW, imageH, false);
    }

    private static BoundingBox xyxyToBox(
            double x1, double y1, double x2, double y2, int imageW, int imageH, boolean forceThousand) {
        boolean normalizedUnit = maxAbs(x1, y1, x2, y2) <= 1.0 && imageW > 0 && imageH > 0;
        boolean normalized1000 = forceThousand && imageW > 0 && imageH > 0
                && maxAbs(x1, y1, x2, y2) > 1.0;
        if (!normalized1000 && !normalizedUnit && imageW > 0 && imageH > 0) {
            // Prefer absolute pixels when they fit; else treat as 0–1000 grid.
            double maxX = Math.max(x1, x2);
            double maxY = Math.max(y1, y2);
            if (maxX > imageW || maxY > imageH) {
                normalized1000 = maxAbs(x1, y1, x2, y2) <= 1000.0;
            }
        }
        int ax1;
        int ay1;
        int ax2;
        int ay2;
        if (normalized1000) {
            ax1 = (int) Math.round(x1 / 1000.0 * imageW);
            ay1 = (int) Math.round(y1 / 1000.0 * imageH);
            ax2 = (int) Math.round(x2 / 1000.0 * imageW);
            ay2 = (int) Math.round(y2 / 1000.0 * imageH);
        } else if (normalizedUnit) {
            ax1 = (int) Math.round(x1 * imageW);
            ay1 = (int) Math.round(y1 * imageH);
            ax2 = (int) Math.round(x2 * imageW);
            ay2 = (int) Math.round(y2 * imageH);
        } else {
            ax1 = (int) Math.round(x1);
            ay1 = (int) Math.round(y1);
            ax2 = (int) Math.round(x2);
            ay2 = (int) Math.round(y2);
        }
        int left = Math.min(ax1, ax2);
        int top = Math.min(ay1, ay2);
        int width = Math.abs(ax2 - ax1);
        int height = Math.abs(ay2 - ay1);
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new BoundingBox(left, top, width, height);
    }

    private static BoundingBox fromStartEnd(JSONArray start, JSONArray end, int imageW, int imageH) {
        double x1 = start.optDouble(0);
        double y1 = start.optDouble(1);
        double x2 = end.optDouble(0);
        double y2 = end.optDouble(1);
        // UI-TARS start_box/end_box is conventionally 0–1000 normalized.
        return xyxyToBox(x1, y1, x2, y2, imageW, imageH, true);
    }

    private static double scaleIfNormalized(double v, int span) {
        if (span > 0 && v >= 0 && v <= 1.0) {
            return v * span;
        }
        // Only remap 0–1000 grid when the value clearly exceeds the pixel span.
        if (span > 0 && v > span && v <= 1000.0) {
            return v / 1000.0 * span;
        }
        return v;
    }

    private static double maxAbs(double a, double b, double c, double d) {
        return Math.max(Math.max(Math.abs(a), Math.abs(b)), Math.max(Math.abs(c), Math.abs(d)));
    }

    private static double clampConf(JSONObject o) {
        double conf = o.has("confidence") ? o.optDouble("confidence", 0.5) : 0.5;
        if (Double.isNaN(conf) || conf < 0) {
            return 0.5;
        }
        return Math.min(1.0, conf);
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (t.toLowerCase(Locale.ROOT).startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end >= 0) {
                t = t.substring(0, end);
            }
        }
        return t.trim();
    }

    private static JSONObject extractObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return new JSONObject(text.substring(start, end + 1));
    }
}
