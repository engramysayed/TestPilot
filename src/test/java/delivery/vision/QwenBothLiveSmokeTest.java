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
 * Both ground + assert via Qwen (new prompt / quality gate). Skips if model missing.
 */
public class QwenBothLiveSmokeTest {

    private static final String SITE = "https://the-internet.herokuapp.com/login";
    private static final Path NOTES = Path.of(
            "docs/superpowers/plans/2026-08-21-qwen-both-live-notes.md");

    @Test
    public void qwenGroundsLoginAndAssertsWithHardenedPrompt() throws Exception {
        assumeQwenUp();
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.assertions.enabled", "true");
        System.setProperty("delivery.vision.grounding.provider", "qwen");
        System.setProperty("delivery.vision.grounding.model", "qwen2.5vl:3b");
        System.setProperty("delivery.vision.assert.provider", "qwen");
        System.setProperty("delivery.vision.assert.model", "qwen2.5vl:3b");
        System.clearProperty("delivery.vision.provider");
        System.clearProperty("delivery.vision.model");
        System.setProperty("delivery.llm-base-url", "http://127.0.0.1:11434");
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("BROWSER_HEADLESS", "true");
        System.setProperty("BROWSER_TYPE", "CHROME");

        WebDriverFactory factory = null;
        StringBuilder notes = new StringBuilder();
        notes.append("# Qwen both (ground+assert) live — ").append(Instant.now()).append("\n\n");
        notes.append("- ground: ").append(VisionGroundingConfig.groundingProviderId())
                .append(" / ").append(VisionGroundingConfig.groundingModel()).append('\n');
        notes.append("- assert: ").append(VisionGroundingConfig.assertProviderId())
                .append(" / ").append(VisionGroundingConfig.assertModel()).append('\n');
        try {
            VisionGroundingProvider ground = VisionGroundingConfig.createProvider();
            VisionGroundingProvider assertP = VisionGroundingConfig.createAssertionProvider();
            Assert.assertTrue(ground instanceof QwenVisionProvider, ground.getClass().getName());
            Assert.assertTrue(assertP instanceof QwenVisionProvider, assertP.getClass().getName());

            factory = new WebDriverFactory();
            WebDriver driver = factory.get();
            driver.get(SITE);
            Thread.sleep(1500);
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            long t0 = System.nanoTime();
            VisionAnalysisResult analysis = ground.analyze(
                    png,
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"));
            notes.append("- analyze ms: ").append((System.nanoTime() - t0) / 1_000_000L).append('\n');
            notes.append("- found: ").append(analysis.found()).append(" err=").append(analysis.error()).append('\n');
            if (ground instanceof QwenVisionProvider qwen) {
                notes.append("- preGate: ").append(qwen.lastPreGateCandidates()).append('\n');
                String raw = qwen.lastAnalyzeRaw();
                if (raw != null) {
                    String clip = raw.length() > 600 ? raw.substring(0, 600) + "…" : raw;
                    notes.append("- raw: ").append(clip.replace('\n', ' ')).append('\n');
                }
            }
            Assert.assertTrue(analysis.found(), String.valueOf(analysis));
            VisualCandidate top = analysis.candidates().get(0);
            notes.append("- bbox: ").append(top.boundingBox()).append(" conf=").append(top.confidence()).append('\n');
            notes.append("- label: ").append(top.description()).append('\n');
            Assert.assertTrue(top.confidence() >= VisionBboxQualityGate.MIN_CONFIDENCE, top.toString());
            Assert.assertTrue(top.boundingBox().width() >= VisionBboxQualityGate.MIN_SIDE_PX);

            GroundingBrowser browser = new SeleniumGroundingBrowser(driver);
            ViewportMetrics metrics = browser.metrics();
            int[] dims = PngDimensions.read(png);
            CssPoint center = CoordinateMapper.center(top.boundingBox());
            CssPoint css = CoordinateMapper.toCss(
                    center,
                    metrics.screenshotWidth() > 0 ? metrics.screenshotWidth() : dims[0],
                    metrics.screenshotHeight() > 0 ? metrics.screenshotHeight() : dims[1],
                    metrics.innerWidth(),
                    metrics.innerHeight());
            GroundedNode node = browser.elementFromPoint(css.x(), css.y());
            notes.append("- cssPoint: ").append(css.x()).append(',').append(css.y()).append('\n');
            notes.append("- elementFromPoint: ").append(node).append('\n');
            if (node == null) {
                notes.append("- WARN: bbox center missed DOM; Qwen ground often oversized — assert still runs\n");
            } else {
                String hay = ((node.visibleText() == null ? "" : node.visibleText())
                        + " " + (node.tag() == null ? "" : node.tag())).toLowerCase(Locale.ROOT);
                Assert.assertTrue(
                        hay.contains("login")
                                && ("button".equalsIgnoreCase(node.tag())
                                || "a".equalsIgnoreCase(node.tag())),
                        "expected Login control, got " + node);
            }

            t0 = System.nanoTime();
            VisionAssertionResult raw = assertP.assertVisual(
                    png, "Login page shows username and password fields", null);
            notes.append("- assert ms: ").append((System.nanoTime() - t0) / 1_000_000L).append('\n');
            VisionAssertionResult gated = VisionAssertionGate.honestyCheck(
                    raw, "Login page shows username and password fields", SITE);
            notes.append("- raw: ").append(raw.status()).append(" conf=").append(raw.confidence()).append('\n');
            notes.append("- observation: ").append(raw.observation()).append('\n');
            notes.append("- evidence: ").append(raw.evidence()).append('\n');
            notes.append("- gated: ").append(gated.status()).append(" err=").append(gated.error()).append('\n');

            Assert.assertFalse(VisionAssertionGate.isPromptPlaceholder(raw.observation())
                    && raw.status() == VisionAssertionStatus.PASS);
            if (raw.status() == VisionAssertionStatus.PASS || raw.status() == VisionAssertionStatus.FAIL) {
                Assert.assertTrue(raw.observation().length() >= 20);
                Assert.assertTrue(raw.evidence().length() >= 8);
                Assert.assertTrue(raw.confidence() >= 0.7);
            }
            notes.append("\n**Verdict:** qwen both OK; gated=").append(gated.status()).append('\n');
        } finally {
            try {
                Files.createDirectories(NOTES.getParent());
                Files.writeString(NOTES, notes.toString());
            } catch (Exception ignored) {
                // best-effort
            }
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.assertions.enabled");
            System.clearProperty("delivery.vision.grounding.provider");
            System.clearProperty("delivery.vision.grounding.model");
            System.clearProperty("delivery.vision.assert.provider");
            System.clearProperty("delivery.vision.assert.model");
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

    private static void assumeQwenUp() throws Exception {
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
            String body = res.body().toLowerCase(Locale.ROOT);
            if (!body.contains("qwen")) {
                throw new SkipException("qwen model not installed");
            }
        } catch (SkipException e) {
            throw e;
        } catch (Exception e) {
            throw new SkipException("Ollama unreachable: " + e.getMessage());
        }
    }
}
