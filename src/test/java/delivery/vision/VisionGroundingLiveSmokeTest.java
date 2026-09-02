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
import java.time.Duration;

/**
 * Live smoke: real browser screenshot + local Ollama vision provider.
 * Skips when Ollama or the configured vision model is unavailable.
 *
 * Run:
 * mvn "-Dtest=VisionGroundingLiveSmokeTest" "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" test
 */
public class VisionGroundingLiveSmokeTest {

    private static final String SITE = "https://the-internet.herokuapp.com/login";

    @Test
    public void qwenFindsLoginButtonBboxOnPublicLoginPage() throws Exception {
        assumeOllamaUp();
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.grounding.provider", "qwen");
        System.setProperty("delivery.vision.grounding.model", "qwen2.5vl:3b");
        System.clearProperty("delivery.vision.provider");
        System.clearProperty("delivery.vision.model");
        System.setProperty("delivery.llm-base-url", "http://127.0.0.1:11434");
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("BROWSER_HEADLESS", "true");
        System.setProperty("BROWSER_TYPE", "CHROME");
        WebDriverFactory factory = null;
        try {
            VisionGroundingProvider provider = VisionGroundingConfig.createProvider();
            Assert.assertTrue(provider instanceof QwenVisionProvider, "expected Qwen provider");

            factory = new WebDriverFactory();
            WebDriver driver = factory.get();
            driver.get(SITE);
            Thread.sleep(1500);
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Assert.assertTrue(png.length > 1000, "screenshot too small");

            VisionAnalysisResult result = provider.analyze(
                    png,
                    new StepIntentBinder.IntentLine(
                            StepIntentBinder.IntentKind.CLICK, "Click Login"));

            Assert.assertTrue(result.error() == null || result.error().isBlank(),
                    "vision error: " + result.error());
            Assert.assertTrue(result.found(), "expected found=true, got: " + result);
            Assert.assertFalse(result.candidates().isEmpty(), "expected at least one candidate");
            VisualCandidate top = result.candidates().get(0);
            Assert.assertTrue(top.boundingBox().width() >= VisionBboxQualityGate.MIN_SIDE_PX, "bbox width");
            Assert.assertTrue(top.boundingBox().height() >= VisionBboxQualityGate.MIN_SIDE_PX, "bbox height");
            Assert.assertTrue(top.confidence() >= VisionBboxQualityGate.MIN_CONFIDENCE, "confidence");

            // Qwen ground is not the production default (UI-TARS is). Oversized/off-center boxes
            // may miss elementFromPoint — record but do not fail the smoke on DOM hit alone.
            GroundingBrowser browser = new SeleniumGroundingBrowser(driver);
            ViewportMetrics metrics = browser.metrics();
            OptionalHit hit = firstInteractiveHit(browser, top, metrics);
            if (!hit.ok()) {
                System.out.println("WARN: Qwen ground elementFromPoint miss for bbox="
                        + top.boundingBox() + " metrics=" + metrics + " node=" + hit.node());
            }
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.grounding.provider");
            System.clearProperty("delivery.vision.grounding.model");
            System.clearProperty("delivery.vision.provider");
            System.clearProperty("delivery.vision.model");
            System.clearProperty("delivery.llm-base-url");
            System.clearProperty("EXECUTION_TYPE");
            System.clearProperty("BROWSER_HEADLESS");
            System.clearProperty("BROWSER_TYPE");
            if (factory != null) {
                try {
                    factory.quit();
                } catch (RuntimeException ignored) {
                    // best-effort
                }
            }
        }
    }

    private static void assumeOllamaUp() throws Exception {
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
            if (!res.body().contains("qwen2.5vl")) {
                throw new SkipException("qwen2.5vl not installed — run scripts/setup-vision-models.ps1");
            }
        } catch (SkipException e) {
            throw e;
        } catch (Exception e) {
            throw new SkipException("Ollama unreachable: " + e.getMessage());
        }
    }

    private record OptionalHit(boolean ok, GroundedNode node) {
    }

    private static OptionalHit firstInteractiveHit(
            GroundingBrowser browser, VisualCandidate visual, ViewportMetrics metrics) {
        CssPoint center = CoordinateMapper.center(visual.boundingBox());
        CssPoint css = CoordinateMapper.toCss(
                center,
                metrics.screenshotWidth(),
                metrics.screenshotHeight(),
                metrics.innerWidth(),
                metrics.innerHeight());
        GroundedNode node = browser.elementFromPoint(css.x(), css.y());
        boolean ok = node != null && node.displayed() && node.enabled();
        return new OptionalHit(ok, node);
    }
}
