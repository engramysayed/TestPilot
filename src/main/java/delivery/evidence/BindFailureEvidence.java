package delivery.evidence;

import delivery.privacy.ScreenshotRedactor;
import delivery.snapshot.PageModel;
import delivery.snapshot.PageSerializer;
import delivery.snapshot.SnapshotCandidate;
import delivery.snapshot.TextLocatorSanitizer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A9: evidence captured at a bind failure while the session is still open (after e2e run/failure-evidence.ts):
 * redacted DOM, masked screenshot, and the 5 nearest candidates with scores and reasons, written as a bundle
 * {@code <artifactsDir>/evidence/bind-<step>-<epochMs>/}. Evidence never turns a failure into a different
 * failure: every part is best-effort, errors are recorded in the manifest, nothing is thrown.
 */
public final class BindFailureEvidence {
    public static final String CONTRACT = "keel.bind-evidence";
    public static final int VERSION = 1;
    public static final int TOP_N = 5;
    public static final int MAX_DOM_BYTES = 1_000_000;
    public static final int MAX_CANDIDATE_BYTES = 1024;
    static final String CAPTURE_JS = resource("/keel/evidence-capture.js");

    /** What failed to bind. attemptedTarget may be null (e.g. the AI answered "none"). */
    public record Request(Path artifactsDir, String stepId, String stepText, List<String> keywords,
                          String attemptedTarget, String error, List<String> preferredAttrs,
                          String action, delivery.settle.SettlePolicy.SideEffects sideEffects,
                          boolean secret, String value) {
        public Request(Path artifactsDir, String stepId, String stepText, List<String> keywords,
                       String attemptedTarget, String error, List<String> preferredAttrs,
                       String action, delivery.settle.SettlePolicy.SideEffects sideEffects) {
            this(artifactsDir, stepId, stepText, keywords, attemptedTarget, error, preferredAttrs, action, sideEffects, false, null);
        }

        public Request(Path artifactsDir, String stepId, String stepText, List<String> keywords,
                       String attemptedTarget, String error, List<String> preferredAttrs) {
            this(artifactsDir, stepId, stepText, keywords, attemptedTarget, error, preferredAttrs, null, null, false, null);
        }
    }

    public record Near(int rank, String id, String target, String role, String name, double score, List<String> reasons) {
    }

    private BindFailureEvidence() {
    }

    public static Optional<Path> capture(WebDriver driver, Request original) {
        long t0 = System.currentTimeMillis();
        final Request req = maskSecret(original);
        JSONObject manifest = new JSONObject().put("contract", CONTRACT).put("version", VERSION)
                .put("step", new JSONObject().put("id", req.stepId()).put("text", TextLocatorSanitizer.redact(nz(req.stepText())))
                        .put("attemptedTarget", req.attemptedTarget() == null ? JSONObject.NULL : req.attemptedTarget())
                        .put("error", TextLocatorSanitizer.redact(nz(req.error())))
                        .put("action", req.action() == null ? JSONObject.NULL : req.action())
                        // IR sideEffects (Codegen r1/05): absent == unknown == treated as mutating
                        .put("sideEffects", (req.sideEffects() == null ? delivery.settle.SettlePolicy.SideEffects.UNKNOWN : req.sideEffects()).wire)
                        .put("retrySafe", req.sideEffects() != null && req.sideEffects().retrySafe())
                        .put("secret", req.secret()))
                .put("capturedAt", Instant.now().toString());
        JSONArray problems = new JSONArray();
        Path dir;
        try {
            dir = req.artifactsDir().resolve("evidence").resolve("bind-" + safe(req.stepId()) + "-" + System.currentTimeMillis());
            Files.createDirectories(dir);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        Map<String, Object> cap = null;
        try {
            driver.switchTo().defaultContent();
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) ((JavascriptExecutor) driver).executeScript(CAPTURE_JS);
            cap = c;
            String html = maskValue(original, redactHtml(maskValue(original, String.valueOf(cap.get("html")))));
            boolean truncated = html.length() > MAX_DOM_BYTES;
            if (truncated) html = html.substring(0, MAX_DOM_BYTES) + "\n<!-- keel: truncated -->";
            Files.writeString(dir.resolve("dom.redacted.html"), html, StandardCharsets.UTF_8);
            manifest.put("url", String.valueOf(cap.get("url"))).put("title", TextLocatorSanitizer.redact(String.valueOf(cap.get("title"))))
                    .put("domTruncated", truncated);
        } catch (Exception e) {
            problems.put("dom: " + e.getClass().getSimpleName());
        }
        try {
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            List<ScreenshotRedactor.Box> boxes = new ArrayList<>();
            int inputs = 0, piiTexts = 0;
            if (cap != null) {
                for (Object o : (List<?>) cap.get("boxes")) { boxes.add(box((Map<?, ?>) o)); inputs++; }
                for (Object o : (List<?>) cap.get("texts")) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    if (TextLocatorSanitizer.containsPii(String.valueOf(m.get("t")))) { boxes.add(box(m)); piiTexts++; }
                }
            } else {
                boxes.addAll(ScreenshotRedactor.locatePasswordBoxes(driver));
            }
            Files.write(dir.resolve("screenshot.masked.png"), ScreenshotRedactor.redact(png, boxes));
            manifest.put("maskedRegions", new JSONObject().put("inputs", inputs).put("piiText", piiTexts));
        } catch (Exception e) {
            problems.put("screenshot: " + e.getClass().getSimpleName());
        }
        try {
            PageModel model = PageSerializer.serialize(driver, nz(req.keywords()), nz(req.preferredAttrs()));
            driver.switchTo().defaultContent();
            JSONArray arr = new JSONArray();
            for (Near n : nearest(model, req.stepText(), nz(req.keywords()), req.attemptedTarget(), TOP_N)) {
                JSONObject j = new JSONObject().put("rank", n.rank()).put("id", n.id()).put("target", n.target())
                        .put("role", n.role()).put("name", maskValue(original, n.name())).put("score", Math.round(n.score() * 1000) / 1000.0)
                        .put("reasons", new JSONArray(n.reasons()));
                while (j.toString().getBytes(StandardCharsets.UTF_8).length > MAX_CANDIDATE_BYTES && j.getString("name").length() > 8) {
                    j.put("name", j.getString("name").substring(0, j.getString("name").length() / 2) + "…");
                }
                if (j.toString().getBytes(StandardCharsets.UTF_8).length > MAX_CANDIDATE_BYTES) j.put("target", "[too long]");
                arr.put(j);
            }
            Files.writeString(dir.resolve("candidates.json"), new JSONObject().put("stepText", TextLocatorSanitizer.redact(nz(req.stepText())))
                    .put("pageCandidates", model.candidates().size()).put("nearest", arr).toString(2), StandardCharsets.UTF_8);
        } catch (Exception e) {
            problems.put("candidates: " + e.getClass().getSimpleName());
        }
        try {
            driver.switchTo().defaultContent();
        } catch (RuntimeException ignored) {
            // session may be gone; evidence must not fail the step differently
        }
        manifest.put("problems", problems).put("elapsedMs", System.currentTimeMillis() - t0)
                .put("files", new JSONArray(List.of("dom.redacted.html", "screenshot.masked.png", "candidates.json")));
        try {
            Files.writeString(dir.resolve("evidence.json"), manifest.toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(dir);
    }

    static final String SECRET_MASK = "\u2022\u2022\u2022";

    /** IR secret=true: the step's value never reaches the bundle (step text, error, target, DOM, candidates). */
    static Request maskSecret(Request r) {
        if (!r.secret()) return r;
        return new Request(r.artifactsDir(), r.stepId(), maskValue(r, r.stepText()), r.keywords(), maskValue(r, r.attemptedTarget()),
                maskValue(r, r.error()), r.preferredAttrs(), r.action(), r.sideEffects(), true, null);
    }

    static String maskValue(Request r, String s) {
        if (s == null || !r.secret() || r.value() == null || r.value().isEmpty()) return s;
        return s.replace(r.value(), SECRET_MASK);
    }

    /** Text nodes outside tags get the PII masking; tag attributes keep only masked values (done in JS). */
    static String redactHtml(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            int lt = html.indexOf('<', i);
            if (lt < 0) { out.append(TextLocatorSanitizer.redact(html.substring(i))); break; }
            out.append(TextLocatorSanitizer.redact(html.substring(i, lt)));
            int gt = html.indexOf('>', lt);
            if (gt < 0) { out.append(html.substring(lt)); break; }
            out.append(redactAttrs(html.substring(lt, gt + 1)));
            i = gt + 1;
        }
        return out.toString();
    }

    private static String redactAttrs(String tag) {
        // e-mails/digit runs in attributes (title, aria-label, data-*) are PII too; keep structure
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("=\"([^\"]*)\"").matcher(tag);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("=\"" + TextLocatorSanitizer.redact(m.group(1)) + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Similarity ranking: token overlap, typo distance, keyword/attempt hints, interactivity. */
    public static List<Near> nearest(PageModel model, String stepText, List<String> keywords, String attempted, int n) {
        Set<String> stepTokens = tokens(nz(stepText) + " " + String.join(" ", keywords));
        Set<String> attemptTokens = tokens(attempted == null ? "" : attempted.replaceAll("[\\[\\]=\"'#.>:()]", " "));
        List<Near> all = new ArrayList<>();
        for (int i = 0; i < model.candidates().size(); i++) {
            SnapshotCandidate c = model.candidates().get(i);
            String hay = c.name() + " " + String.join(" ", c.attrs().values());
            Set<String> ct = tokens(hay);
            List<String> reasons = new ArrayList<>();
            double score = 0;
            int overlap = 0;
            for (String t : stepTokens) if (ct.contains(t)) overlap++;
            if (!stepTokens.isEmpty() && overlap > 0) {
                double f = (double) overlap / stepTokens.size();
                score += 0.5 * f;
                reasons.add("step tokens " + overlap + "/" + stepTokens.size());
            }
            String typo = bestTypo(stepTokens, ct);
            if (typo != null) { score += 0.2; reasons.add(typo); }
            int ao = 0;
            for (String t : attemptTokens) if (t.length() > 2 && ct.contains(t)) ao++;
            if (ao > 0) { score += Math.min(0.15, 0.05 * ao); reasons.add("shares " + ao + " token(s) with attempted target"); }
            if (c.keyword()) { score += 0.1; reasons.add("serializer keyword hit"); }
            if (c.interactive()) { score += 0.05; reasons.add("interactive"); }
            if (c.offscreen()) reasons.add("offscreen");
            if (!c.inViewport() && !c.offscreen()) reasons.add("outside viewport");
            if (score <= 0.05 && reasons.size() <= 1) continue;
            String target;
            try { target = c.target().format(); } catch (RuntimeException e) { target = c.css(); }
            all.add(new Near(0, PageModel.idOf(i), TextLocatorSanitizer.redact(target), c.role().isBlank() ? c.tag() : c.role(),
                    TextLocatorSanitizer.redact(c.name()), Math.min(1.0, score), reasons));
        }
        all.sort(Comparator.comparingDouble(Near::score).reversed());
        List<Near> top = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++) {
            Near x = all.get(i);
            top.add(new Near(i + 1, x.id(), x.target(), x.role(), x.name(), x.score(), x.reasons()));
        }
        return top;
    }

    static String bestTypo(Set<String> want, Set<String> have) {
        for (String w : want) {
            if (w.length() < 4 || have.contains(w)) continue;
            for (String h : have) {
                if (Math.abs(h.length() - w.length()) > 2) continue;
                int d = edit(w, h);
                if (d > 0 && d <= (w.length() >= 7 ? 2 : 1)) return "typo '" + w + "'~'" + h + "' (edit " + d + ")";
            }
        }
        return null;
    }

    static int edit(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
                // transposition counts as 1 (Damerau, restricted)
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    cur[j] = Math.min(cur[j], prevPrev(a, b, i, j));
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    // restricted Damerau needs row i-2; recomputing is fine for short tokens
    private static int prevPrev(String a, String b, int i, int j) {
        return edit(a.substring(0, i - 2), b.substring(0, j - 2)) + 1;
    }

    static final Set<String> STOP = Set.of("the", "a", "an", "on", "in", "to", "of", "and", "click", "press", "tap", "button",
            "link", "field", "enter", "type", "select", "into", "for", "with", "then", "verify", "should");

    static Set<String> tokens(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : nz(s).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 2 && !STOP.contains(t)) out.add(t);
        }
        return out;
    }

    private static ScreenshotRedactor.Box box(Map<?, ?> m) {
        return new ScreenshotRedactor.Box(i(m.get("x")), i(m.get("y")), i(m.get("w")), i(m.get("h")));
    }

    private static int i(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private static String safe(String s) { return nz(s).replaceAll("[^A-Za-z0-9_-]", "_"); }

    private static String nz(String s) { return s == null ? "" : s; }

    private static <T> List<T> nz(List<T> l) { return l == null ? List.of() : l; }

    private static String resource(String path) {
        try (InputStream in = BindFailureEvidence.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
