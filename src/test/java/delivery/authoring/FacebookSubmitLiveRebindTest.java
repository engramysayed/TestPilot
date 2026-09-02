package delivery.authoring;

import delivery.codegen.ProvenStep;
import drivers.WebDriverFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Live Task 2B helper: open Facebook /reg/, bind "Click the Submit button", record whether a
 * button wins over the bare Sign-up anchor, and patch IR click steps when safe.
 *
 * Run:
 * {@code mvn "-Dmaven.compiler.release=21" -Dtest=FacebookSubmitLiveRebindTest test}
 */
public class FacebookSubmitLiveRebindTest {

    private static final Path IR_DIR = Path.of("delivery-store/facebook-com/prj_e9a9fc7313b2/ir");
    private static final Path NOTES = Path.of(
            "docs/superpowers/plans/2026-08-21-facebook-submit-rebind-notes.md");
    private static final String REG_URL = "https://www.facebook.com/reg/";
    private static final String SIGN_UP_ANCHOR =
            "//a[contains(normalize-space(.),'Sign up')][not(.//*[contains(normalize-space(.),'Sign up')])]";

    @Test
    public void liveRegPageSubmitBindPrefersButtonWhenPresent() throws Exception {
        if (!Files.isDirectory(IR_DIR)) {
            throw new SkipException("Facebook IR missing at " + IR_DIR.toAbsolutePath());
        }

        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("BROWSER_HEADLESS", "true");
        System.setProperty("BROWSER_TYPE", "CHROME");

        StringBuilder notes = new StringBuilder();
        notes.append("# Facebook Submit live rebind — ").append(Instant.now()).append("\n\n");

        WebDriverFactory factory = null;
        try {
            factory = new WebDriverFactory();
            WebDriver driver = factory.get();
            driver.get(REG_URL);
            Thread.sleep(3500);
            String url = driver.getCurrentUrl();
            notes.append("- landed URL: ").append(url).append('\n');
            if (url != null && url.toLowerCase(Locale.ROOT).contains("login")
                    && !url.toLowerCase(Locale.ROOT).contains("reg")) {
                notes.append("\n**Verdict:** redirected away from /reg/ — cannot rebind Submit.\n");
                writeNotes(notes);
                throw new SkipException("Facebook redirected away from /reg/: " + url);
            }

            String slim = HtmlSlimmer.slim(driver.getPageSource(), 80000);
            List<DomCandidate> candidates = DomCandidateExtractor.extract(slim);
            notes.append("- candidates: ").append(candidates.size()).append('\n');
            notes.append("- buttonish: ").append(candidates.stream().filter(c ->
                    "button".equalsIgnoreCase(c.tag())).count()).append('\n');
            notes.append("- bare Sign-up anchors: ").append(candidates.stream()
                    .filter(StepIntentBinder::looksLikeBareSignUpAnchor).count()).append('\n');
            notes.append("- form-submit-looking: ").append(candidates.stream()
                    .filter(StepIntentBinder::looksLikeFormSubmitControl).count()).append('\n');

            var intent = new StepIntentBinder.IntentLine(
                    StepIntentBinder.IntentKind.CLICK, "Click the Submit button");
            StepIntentBinder.BindResult bind =
                    StepIntentBinder.bindSingle(intent, "TC_FB_LIVE", candidates, List.of());
            notes.append("- bind ok: ").append(bind.ok()).append('\n');
            notes.append("- reject: ").append(bind.rejectReason()).append('\n');

            if (!bind.ok() || bind.steps().isEmpty()) {
                notes.append("\n**Verdict:** Submit bind failed on live DOM — IR unchanged.\n");
                writeNotes(notes);
                Assert.fail("Submit bind failed: " + bind.rejectReason());
            }

            ProvenStep step = bind.steps().get(0);
            String loc = step.locatorValue() == null ? "" : step.locatorValue();
            String strategy = step.locatorStrategy() == null ? "xpath" : step.locatorStrategy();
            notes.append("- bound strategy: ").append(strategy).append('\n');
            notes.append("- bound locator: ").append(loc).append('\n');
            notes.append("- rationale: ").append(step.rationale()).append('\n');

            boolean stillAnchor = SIGN_UP_ANCHOR.equals(loc)
                    || (loc.contains("//a[") && loc.toLowerCase(Locale.ROOT).contains("sign up"));
            if (stillAnchor) {
                notes.append("\n**Verdict:** live bind still chose Sign-up **anchor**. ")
                        .append("IR left unchanged — Meta likely has no better Submit control.\n");
                writeNotes(notes);
                return;
            }

            int patched = patchIrSubmitAnchorClicks(strategy, loc);
            notes.append("- IR click steps patched: ").append(patched).append('\n');
            notes.append("\n**Verdict:** Submit rebound away from Sign-up anchor; IR updated.\n");
            writeNotes(notes);
            Assert.assertTrue(patched > 0, "expected at least one IR Sign-up anchor click to patch");
        } finally {
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

    /** Replace only click steps whose locator is the known Sign-up anchor. */
    private static int patchIrSubmitAnchorClicks(String strategy, String locator) throws Exception {
        int patchedSteps = 0;
        try (Stream<Path> files = Files.list(IR_DIR)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                JSONObject root = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
                JSONArray steps = root.optJSONArray("provenSteps");
                if (steps == null) {
                    steps = root.optJSONArray("steps");
                }
                if (steps == null) {
                    continue;
                }
                boolean fileChanged = false;
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject s = steps.optJSONObject(i);
                    if (s == null) {
                        continue;
                    }
                    if (!"click".equalsIgnoreCase(s.optString("action"))) {
                        continue;
                    }
                    if (!SIGN_UP_ANCHOR.equals(s.optString("locatorValue"))) {
                        continue;
                    }
                    s.put("locatorStrategy", strategy);
                    s.put("locatorValue", locator);
                    s.put("rationale", "intent:CLICK:live-rebind-submit");
                    patchedSteps++;
                    fileChanged = true;
                }
                if (fileChanged) {
                    Files.writeString(file, root.toString(2) + "\n", StandardCharsets.UTF_8);
                }
            }
        }
        return patchedSteps;
    }

    private static void writeNotes(StringBuilder notes) throws Exception {
        Files.createDirectories(NOTES.getParent());
        Files.writeString(NOTES, notes.toString(), StandardCharsets.UTF_8);
    }
}
