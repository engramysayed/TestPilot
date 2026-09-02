package delivery.vision;

import delivery.authoring.StepIntentBinder;
import drivers.WebDriverFactory;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Live ui-tars smoke after bbox-quality work. Skips when Ollama / ui-tars missing.
 */
public class UiTarsLiveSmokeTest {

    private static final String SITE = "https://the-internet.herokuapp.com/login";
    private static final Path SCORECARD = Path.of(
            "docs/superpowers/plans/2026-08-21-uitars-live-smoke-notes.md");

    @Test
    public void uitarsFindsLoginControlAndHonestAssertDoesNotPlaceholderPass() throws Exception {
        assumeUiTarsUp();
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.assertions.enabled", "true");
        System.setProperty("delivery.vision.provider", "uitars");
        System.setProperty("delivery.vision.model", "ui-tars");
        System.setProperty("delivery.llm-base-url", "http://127.0.0.1:11434");
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("BROWSER_HEADLESS", "true");
        System.setProperty("BROWSER_TYPE", "CHROME");

        WebDriverFactory factory = null;
        StringBuilder notes = new StringBuilder();
        notes.append("# UI-TARS live smoke — ").append(Instant.now()).append("\n\n");
        try {
            VisionGroundingProvider provider = VisionGroundingConfig.createProvider();
            Assert.assertTrue(provider instanceof UiTarsVisionProvider,
                    "expected UiTarsVisionProvider, got " + provider.getClass().getName());

            factory = new WebDriverFactory();
            WebDriver driver = factory.get();
            driver.get(SITE);
            Thread.sleep(1500);
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Assert.assertTrue(png.length > 1000, "screenshot too small");
            int[] dims = PngDimensions.read(png);
            notes.append("- png: ").append(dims[0]).append('x').append(dims[1]).append('\n');

            long t0 = System.nanoTime();
            VisionAnalysisResult analysis = provider.analyze(
                    png,
                    new StepIntentBinder.IntentLine(
                            StepIntentBinder.IntentKind.CLICK, "Click Login"));
            long analyzeMs = (System.nanoTime() - t0) / 1_000_000L;
            notes.append("- analyze ms: ").append(analyzeMs).append('\n');
            notes.append("- analyze error: ").append(analysis.error()).append('\n');
            notes.append("- found: ").append(analysis.found()).append('\n');
            notes.append("- candidates: ").append(analysis.candidates().size()).append('\n');
            if (provider instanceof UiTarsVisionProvider uitars) {
                String raw = uitars.lastAnalyzeRaw();
                if (raw != null) {
                    String clip = raw.length() > 800 ? raw.substring(0, 800) + "…" : raw;
                    notes.append("- raw analyze (clip): ").append(clip.replace('\n', ' ')).append('\n');
                }
            }

            Assert.assertTrue(analysis.error() == null || analysis.error().isBlank()
                            || analysis.found(),
                    "vision error: " + analysis.error());
            Assert.assertTrue(analysis.found(), "expected found=true after quality gate: " + analysis);
            Assert.assertFalse(analysis.candidates().isEmpty());
            VisualCandidate top = analysis.candidates().get(0);
            notes.append("- top bbox: ").append(top.boundingBox()).append('\n');
            notes.append("- top conf: ").append(top.confidence()).append('\n');
            notes.append("- top label: ").append(top.description()).append('\n');

            Assert.assertTrue(top.confidence() >= VisionBboxQualityGate.MIN_CONFIDENCE, top.toString());
            Assert.assertTrue(top.boundingBox().width() >= VisionBboxQualityGate.MIN_SIDE_PX
                            && top.boundingBox().height() >= VisionBboxQualityGate.MIN_SIDE_PX,
                    "bbox too small: " + top.boundingBox());

            GroundingBrowser browser = new SeleniumGroundingBrowser(driver);
            ViewportMetrics metrics = browser.metrics();
            CssPoint center = CoordinateMapper.center(top.boundingBox());
            CssPoint css = CoordinateMapper.toCss(
                    center,
                    metrics.screenshotWidth() > 0 ? metrics.screenshotWidth() : dims[0],
                    metrics.screenshotHeight() > 0 ? metrics.screenshotHeight() : dims[1],
                    metrics.innerWidth(),
                    metrics.innerHeight());
            GroundedNode node = browser.elementFromPoint(css.x(), css.y());
            notes.append("- elementFromPoint: ").append(node).append('\n');
            Assert.assertNotNull(node, "elementFromPoint miss for " + top.boundingBox());
            Assert.assertTrue(node.displayed() && node.enabled(), String.valueOf(node));
            String hay = ((node.visibleText() == null ? "" : node.visibleText())
                    + " " + (node.ariaLabel() == null ? "" : node.ariaLabel())
                    + " " + (node.id() == null ? "" : node.id())
                    + " " + (node.tag() == null ? "" : node.tag())).toLowerCase(Locale.ROOT);
            Assert.assertTrue(
                    hay.contains("login")
                            && ("button".equalsIgnoreCase(node.tag())
                            || "a".equalsIgnoreCase(node.tag())
                            || (node.visibleText() != null
                            && node.visibleText().toLowerCase(Locale.ROOT).contains("login"))),
                    "expected Login button/control (not username field), got: " + node);

            t0 = System.nanoTime();
            VisionAssertionResult rawAssert = provider.assertVisual(
                    png, "Login page shows username and password fields", null);
            long assertMs = (System.nanoTime() - t0) / 1_000_000L;
            VisionAssertionResult gated = VisionAssertionGate.honestyCheck(
                    rawAssert, "Login page shows username and password fields", SITE);

            notes.append("- assert ms: ").append(assertMs).append('\n');
            notes.append("- raw status: ").append(rawAssert.status()).append('\n');
            notes.append("- raw observation: ").append(rawAssert.observation()).append('\n');
            notes.append("- raw evidence: ").append(rawAssert.evidence()).append('\n');
            notes.append("- raw confidence: ").append(rawAssert.confidence()).append('\n');
            notes.append("- gated status: ").append(gated.status()).append('\n');
            notes.append("- gated error: ").append(gated.error()).append('\n');

            Assert.assertFalse(VisionAssertionGate.isPromptPlaceholder(rawAssert.observation())
                            && rawAssert.status() == VisionAssertionStatus.PASS,
                    "placeholder observation on PASS: " + rawAssert.observation());
            Assert.assertFalse(VisionAssertionGate.isPromptPlaceholder(rawAssert.evidence())
                            && rawAssert.status() == VisionAssertionStatus.PASS,
                    "placeholder evidence on PASS: " + rawAssert.evidence());
            if (rawAssert.status() == VisionAssertionStatus.PASS
                    || rawAssert.status() == VisionAssertionStatus.FAIL) {
                Assert.assertTrue(rawAssert.observation().trim().length() >= 20,
                        "observation too short: " + rawAssert.observation());
                Assert.assertTrue(rawAssert.evidence().trim().length() >= 8,
                        "evidence too short: " + rawAssert.evidence());
                Assert.assertTrue(rawAssert.confidence() >= 0.7,
                        "PASS/FAIL confidence must be >= 0.7, was " + rawAssert.confidence());
            }
            // Analyze grounding is the hard option-C gate; assert may stay UNCERTAIN if model JSON is weak.
            notes.append("\n**Verdict:** live analyze OK (Login control); assert raw=")
                    .append(rawAssert.status()).append(" gated=").append(gated.status()).append('\n');
        } finally {
            try {
                Files.createDirectories(SCORECARD.getParent());
                Files.writeString(SCORECARD, notes.toString());
            } catch (Exception ignored) {
                // best-effort notes
            }
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.assertions.enabled");
            System.clearProperty("delivery.vision.provider");
            System.clearProperty("delivery.vision.model");
            System.clearProperty("delivery.llm-base-url");
            System.clearProperty("EXECUTION_TYPE");
            System.clearProperty("BROWSER_HEADLESS");
            System.clearProperty("BROWSER_TYPE");
            if (factory != null) {
                try {
                    factory.quit();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private static void assumeUiTarsUp() throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:11434/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new SkipException("Ollama not healthy: HTTP " + res.statusCode());
            }
            String body = res.body().toLowerCase();
            if (!(body.contains("ui-tars") || body.contains("\"uitars\""))) {
                throw new SkipException("ui-tars not installed — import via scripts/setup-ui-tars-ollama.ps1");
            }
        } catch (SkipException e) {
            throw e;
        } catch (Exception e) {
            throw new SkipException("Ollama unreachable: " + e.getMessage());
        }
    }
}
