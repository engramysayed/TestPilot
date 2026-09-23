package delivery.vision;

import delivery.authoring.*;
import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real UI-TARS on a disposable local page. Records accepted vs safe miss separately from safety assertions. */
public class UiTarsLocalFixtureLiveSmokeTest {
    @Test(timeOut = 120_000)
    public void localModelCannotTurnLoginIntoUsernameClick() throws Exception {
        String oldHeadless = System.getProperty("EXECUTION_TYPE");
        String oldGrounding = System.getProperty("delivery.vision.grounding.enabled");
        String oldAllowlist = System.getProperty("delivery.provider.allowlist");
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.provider.allowlist", "ollama,vision");
        WebDriverFactory factory = null;
        try {
            factory = new WebDriverFactory();
            var driver = factory.get();
            String html = "<html><body style='font:24px Arial;padding:50px'><h1>Merchant operations</h1>"
                    + "<label>Username <input id='username' style='display:block;width:360px;height:45px'></label>"
                    + "<label>Password <input type='password' id='password' style='display:block;width:360px;height:45px'></label>"
                    + "<button id='login' style='margin-top:24px;width:360px;height:55px;font-size:24px' "
                    + "onclick=\"document.body.dataset.clicked='login'\">Login</button></body></html>";
            driver.get("data:text/html;base64," + java.util.Base64.getEncoder().encodeToString(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var local = new UiTarsVisionProvider(new LocalLlmClient("http://127.0.0.1:11434", "ui-tars", java.time.Duration.ofSeconds(45)));
            java.util.List<String> errors = new java.util.ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            VisionGroundingProvider provider = (png, intent) -> {
                calls.incrementAndGet();
                var analysis = local.analyze(png, intent);
                if (analysis.error() != null) errors.add(analysis.error());
                return analysis;
            };
            var steps = new VisionGroundingEngine().tryGround("TC_LOCAL_LOGIN",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK_LOGIN, "Click Login"),
                    DomCandidateExtractor.extract(driver.getPageSource()), provider,
                    new SeleniumGroundingBrowser(driver), null,
                    new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()));
            for (var step : steps) {
                Assert.assertEquals(step.locatorStrategy(), "id");
                Assert.assertEquals(step.locatorValue(), "login", "a wrong model point must be rejected");
                driver.findElement(By.id(step.locatorValue())).click();
                Assert.assertEquals(driver.findElement(By.tagName("body")).getDomAttribute("data-clicked"), "login");
            }
            var record = new org.json.JSONObject().put("model", "ui-tars")
                    .put("fixture", "local in-memory merchant login")
                    .put("accepted", !steps.isEmpty()).put("wrongTargetExecuted", false)
                    .put("providerCalls", calls.get()).put("errors", errors)
                    .put("result", !errors.isEmpty() ? "PROVIDER_UNAVAILABLE" : steps.isEmpty() ? "SAFE_MISS" : "CORRECT_TARGET_EXECUTED");
            Files.writeString(Path.of("target/uitars-local-grounding-result.json"), record.toString(2));
        } finally {
            if (factory != null) factory.quit();
            if (oldHeadless == null) System.clearProperty("EXECUTION_TYPE"); else System.setProperty("EXECUTION_TYPE", oldHeadless);
            if (oldGrounding == null) System.clearProperty("delivery.vision.grounding.enabled"); else System.setProperty("delivery.vision.grounding.enabled", oldGrounding);
            if (oldAllowlist == null) System.clearProperty("delivery.provider.allowlist"); else System.setProperty("delivery.provider.allowlist", oldAllowlist);
        }
    }
}
