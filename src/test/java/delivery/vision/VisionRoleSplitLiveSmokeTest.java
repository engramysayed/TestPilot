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
 * Live proof of role split: UI-TARS grounds, Qwen asserts. Skips if models missing (option C).
 */
public class VisionRoleSplitLiveSmokeTest {

    private static final String SITE = "https://the-internet.herokuapp.com/login";
    private static final Path NOTES = Path.of(
            "docs/superpowers/plans/2026-08-21-vision-role-split-live-notes.md");

    @Test
    public void uitarsGroundsLoginAndQwenAssertsHonestly() throws Exception {
        assumeModelsUp();
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.assertions.enabled", "true");
        System.setProperty("delivery.vision.grounding.provider", "uitars");
        System.setProperty("delivery.vision.grounding.model", "ui-tars");
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
        notes.append("# Vision role-split live — ").append(Instant.now()).append("\n\n");
        notes.append("- grounding: ").append(VisionGroundingConfig.groundingProviderId())
                .append(" / ").append(VisionGroundingConfig.groundingModel()).append('\n');
        notes.append("- assert: ").append(VisionGroundingConfig.assertProviderId())
                .append(" / ").append(VisionGroundingConfig.assertModel()).append('\n');
        try {
            VisionGroundingProvider ground = VisionGroundingConfig.createProvider();
            VisionGroundingProvider assertP = VisionGroundingConfig.createAssertionProvider();
            Assert.assertTrue(ground instanceof UiTarsVisionProvider, ground.getClass().getName());
            Assert.assertTrue(assertP instanceof QwenVisionProvider, assertP.getClass().getName());

            factory = new WebDriverFactory();
            WebDriver driver = factory.get();
            driver.get(SITE);
            Thread.sleep(1500);
            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);

            VisionAnalysisResult analysis = ground.analyze(
                    png,
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"));
            notes.append("- found: ").append(analysis.found()).append('\n');
            notes.append("- candidates: ").append(analysis.candidates().size()).append('\n');
            Assert.assertTrue(analysis.found(), String.valueOf(analysis));
            VisualCandidate top = analysis.candidates().get(0);
            notes.append("- bbox: ").append(top.boundingBox()).append('\n');
            notes.append("- conf: ").append(top.confidence()).append('\n');

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
            notes.append("- elementFromPoint: ").append(node).append('\n');
            Assert.assertNotNull(node);
            String hay = ((node.visibleText() == null ? "" : node.visibleText())
                    + " " + (node.tag() == null ? "" : node.tag())).toLowerCase(Locale.ROOT);
            Assert.assertTrue(hay.contains("login") && "button".equalsIgnoreCase(node.tag()),
                    "expected Login button, got " + node);

            VisionAssertionResult raw = assertP.assertVisual(
                    png, "Login page shows username and password fields", null);
            VisionAssertionResult gated = VisionAssertionGate.honestyCheck(
                    raw, "Login page shows username and password fields", SITE);
            notes.append("- raw assert: ").append(raw.status()).append(" conf=").append(raw.confidence()).append('\n');
            notes.append("- observation: ").append(raw.observation()).append('\n');
            notes.append("- evidence: ").append(raw.evidence()).append('\n');
            notes.append("- gated: ").append(gated.status()).append(" err=").append(gated.error()).append('\n');

            Assert.assertFalse(
                    raw.status() == VisionAssertionStatus.PASS
                            && VisionAssertionGate.isPromptPlaceholder(raw.observation()),
                    "Qwen must not placeholder-PASS");
            if (raw.status() == VisionAssertionStatus.PASS || raw.status() == VisionAssertionStatus.FAIL) {
                Assert.assertTrue(raw.observation().trim().length() >= 20, raw.observation());
                Assert.assertTrue(raw.evidence().trim().length() >= 8, raw.evidence());
            }
            notes.append("\n**Verdict:** split OK; gated=").append(gated.status()).append('\n');
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

    private static void assumeModelsUp() throws Exception {
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
            if (!(body.contains("ui-tars") || body.contains("\"uitars\""))) {
                throw new SkipException("ui-tars not installed");
            }
            String assertModel = VisionGroundingConfig.assertModel().toLowerCase(Locale.ROOT);
            String needle = assertModel.contains(":")
                    ? assertModel.substring(0, assertModel.indexOf(':'))
                    : assertModel;
            if (!body.contains(needle) && !body.contains("qwen")) {
                throw new SkipException("assert model not installed: " + assertModel);
            }
        } catch (SkipException e) {
            throw e;
        } catch (Exception e) {
            throw new SkipException("Ollama unreachable: " + e.getMessage());
        }
    }
}
