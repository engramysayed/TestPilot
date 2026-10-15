package delivery.job;

import delivery.authoring.GeneratedIdDetector;
import delivery.codegen.PageClusterer;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns locators captured while recording into the IR the framework builder already emits.
 * No browser is opened.
 */
public final class RecordedDrafts {
    private static final Set<String> STRATEGIES = Set.of(
            "id", "name", "css", "xpath", "data-testid", "data-test", "data-qa");

    private RecordedDrafts() {
    }

    public static TcDraft draft(ManualTestCase tc, List<RecordedCapture> captures) {
        String tcId = tc == null || tc.tcId() == null ? "" : tc.tcId();
        List<ProvenStep> body = new ArrayList<>();
        boolean signIn = tc != null && tc.preconditions() != null
                && tc.preconditions().toLowerCase(Locale.ROOT).contains("sign in");
        boolean sawSecret = false;
        if (captures != null) {
            for (RecordedCapture capture : captures) {
                if (capture != null && capture.secret()) {
                    sawSecret = true;
                }
                ProvenStep step = toStep(tcId, capture);
                if (step == null || step.secret()) {
                    continue;
                }
                body.add(step);
            }
        }
        boolean needsLogin = signIn || sawSecret;
        String lastUrl = "";
        if (captures != null) {
            for (int i = captures.size() - 1; i >= 0; i--) {
                String page = captures.get(i).pageUrl();
                if (page != null && !page.isBlank()) {
                    lastUrl = page;
                    break;
                }
            }
        }
        return new TcDraft(
                tcId,
                tc == null ? "" : tc.title(),
                tc == null ? "" : tc.steps(),
                tc == null ? "" : tc.expectedResult(),
                TcDraftStatus.PASSED,
                body,
                List.of(),
                needsLogin,
                -1,
                "",
                "",
                "",
                0,
                lastUrl,
                "none",
                "recorded locators",
                "");
    }

    public static boolean canBuild(List<RecordedCapture> captures) {
        if (captures == null) {
            return false;
        }
            for (RecordedCapture capture : captures) {
                ProvenStep step = toStep("TC_1", capture);
                if (step != null && !step.secret()) {
                    return true;
                }
            }
        return false;
    }

    static ProvenStep toStep(String tcId, RecordedCapture capture) {
        if (capture == null || capture.kind() == null) {
            return null;
        }
        String kind = capture.kind().trim().toLowerCase(Locale.ROOT);
        String page = pageName(capture.pageUrl());
        return switch (kind) {
            case "click", "check", "uncheck" -> action(tcId, page, "click", capture, "");
            case "type" -> action(tcId, page, "type", capture, capture.secret() ? "" : capture.value());
            case "select" -> action(tcId, page, "select", capture, capture.value());
            case "assert" -> assertText(tcId, page, capture.label());
            case "assert-url" -> assertUrl(tcId, page, capture.value().isBlank() ? capture.label() : capture.value());
            default -> null;
        };
    }

    private static ProvenStep action(String tcId, String page, String action, RecordedCapture capture, String value) {
        String strategy = capture.strategy() == null ? "" : capture.strategy().trim().toLowerCase(Locale.ROOT);
        String locator = capture.locator() == null ? "" : capture.locator().trim();
        if (!STRATEGIES.contains(strategy) || locator.isBlank() || locator.length() > 300) {
            return null;
        }
        if (("id".equals(strategy) || "name".equals(strategy) || strategy.startsWith("data-"))
                && !GeneratedIdDetector.isTrustworthyIdentifier(locator)) {
            return null;
        }
        return new ProvenStep(
                tcId, page, "elementAction", action, strategy, locator,
                value == null ? "" : value, "", "", true, "recorded", "")
                .withSecret(capture.secret());
    }

    private static ProvenStep assertText(String tcId, String page, String text) {
        String expected = text == null ? "" : text.trim();
        if (expected.isBlank()) {
            return null;
        }
        if (expected.length() > 180) {
            expected = expected.substring(0, 180).trim();
        }
        return new ProvenStep(
                tcId, page, "elementAction", "assert", "", "",
                "", "textContains", expected, true, "recorded", "");
    }

    private static ProvenStep assertUrl(String tcId, String page, String url) {
        String hint = addressHint(url);
        if (hint.isBlank()) {
            return null;
        }
        return new ProvenStep(
                tcId, page, "elementAction", "assert", "", "",
                "", "urlContains", hint, true, "recorded", "");
    }

    private static String pageName(String url) {
        String name = PageClusterer.pageNameFromUrl(url);
        return name == null || name.isBlank() ? "Page" : name;
    }

    static String addressHint(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath();
            if (path != null && !path.isBlank() && !"/".equals(path)) {
                return path.length() > 180 ? path.substring(0, 180) : path;
            }
            String host = uri.getHost();
            return host == null ? "" : host;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
