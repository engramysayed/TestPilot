package delivery.job;

import delivery.authoring.DummyValueInventor;
import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Brings the browser onto a page that shows a username/password login form.
 * Site-agnostic discovery (no host/product URLs):
 * <ol>
 *   <li>Already on a login form (password field visible)</li>
 *   <li>Excel “Open … at /path” when that path yields a login form</li>
 *   <li>Click an on-page control whose visible text/href looks like login / sign-in</li>
 * </ol>
 */
public final class LoginFormNavigator {
    /** Score on-page hrefs only — never used as a blind navigation target. */
    private static final Pattern LOGIN_HREF = Pattern.compile(
            "(?i)(/login|/signin|/sign-in|/auth|/authenticate|/session/new)(/|\\?|#|$)");
    private static final Pattern LOGIN_TEXT = Pattern.compile(
            "(?i)\\b(log\\s*in|sign\\s*in|sign\\s*on|authenticat(?:e|ion))\\b");

    private LoginFormNavigator() {
    }

    public static boolean pageHasLoginForm(WebDriverFactory driverFactory) {
        return pageHasLoginForm(driverFactory.get());
    }

    public static boolean pageHasLoginForm(WebDriver driver) {
        if (driver == null) {
            return false;
        }
        try {
            if (driver.findElements(By.cssSelector("input[type='password']")).stream()
                    .anyMatch(LoginFormNavigator::isDisplayedLoginPassword)) {
                return true;
            }
            // Custom / SPA fields sometimes omit type=password until hydrated
            List<WebElement> candidates = driver.findElements(By.cssSelector(
                    "input[name*='pass'], input[name*='Pass'], input[id*='pass'], input[id*='Pass'], "
                            + "input[autocomplete='current-password'], input[autocomplete='new-password'], "
                            + "input[data-test*='password'], input[data-testid*='password']"));
            return candidates.stream().anyMatch(LoginFormNavigator::isDisplayedLoginPassword);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Masked OTP/MFA inputs use {@code type=password} but are not a username/password login form.
     * Treating them as login made Sign-in look like a no-op and re-triggered login before TC_06.
     */
    static boolean isDisplayedLoginPassword(WebElement el) {
        if (el == null) {
            return false;
        }
        try {
            if (!el.isDisplayed()) {
                return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
        String id = attr(el, "id");
        String name = attr(el, "name");
        String placeholder = attr(el, "placeholder");
        String autocomplete = attr(el, "autocomplete");
        String extra = attr(el, "data-axis-test-id") + " " + attr(el, "data-testid")
                + " " + attr(el, "aria-label") + " " + attr(el, "data-test");
        if (DummyValueInventor.looksLikeOtpHint(
                id + " " + name + " " + placeholder + " " + extra)) {
            return false;
        }
        String type = attr(el, "type").toLowerCase(Locale.ROOT);
        if ("password".equals(type)) {
            return true;
        }
        return looksLikeLoginPasswordField(id, name, placeholder, autocomplete, extra);
    }

    static boolean looksLikeLoginPasswordField(
            String id, String name, String placeholder, String autocomplete, String extra) {
        String hint = ((id == null ? "" : id) + " " + (name == null ? "" : name)
                + " " + (placeholder == null ? "" : placeholder)
                + " " + (autocomplete == null ? "" : autocomplete)
                + " " + (extra == null ? "" : extra)).toLowerCase(Locale.ROOT);
        if (DummyValueInventor.looksLikeOtpHint(hint)) {
            return false;
        }
        return hint.contains("password") || hint.contains("passwd")
                || hint.contains("current-password") || hint.contains("new-password")
                || "pass".equals(hint.trim()) || hint.contains("pass ");
    }

    private static String attr(WebElement el, String name) {
        try {
            String v = el.getAttribute(name);
            return v == null ? "" : v;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * @return true if a login form is visible afterward
     */
    public static boolean ensureLoginFormVisible(
            WebDriverFactory driverFactory,
            String baseUrl,
            ManualTestCase tc
    ) {
        if (waitForLoginForm(driverFactory.get(), Duration.ofSeconds(3))) {
            return true;
        }
        WebDriver driver = driverFactory.get();

        String excelPath = StepIntentBinder.firstOpenPath(
                tc == null ? null : tc.preconditions(),
                tc == null ? null : tc.steps());
        if (excelPath != null && !excelPath.isBlank()) {
            navigateToBasePath(driver, baseUrl, excelPath);
            if (waitForLoginForm(driver, Duration.ofSeconds(12))) {
                return true;
            }
        }

        if (clickLoginEntryControl(driver)) {
            return waitForLoginForm(driver, Duration.ofSeconds(8));
        }
        return false;
    }

    /** Navigate to Excel path for non-login cases (public multi-page demos). */
    public static void navigateExcelOpenPathIfPresent(
            WebDriverFactory driverFactory,
            String baseUrl,
            ManualTestCase tc
    ) {
        String path = StepIntentBinder.firstOpenPath(
                tc == null ? null : tc.preconditions(),
                tc == null ? null : tc.steps());
        if (path == null || path.isBlank()) {
            return;
        }
        WebDriver driver = driverFactory.get();
        navigateToBasePath(driver, baseUrl, path);
        waitForDocumentReady(driver, Duration.ofSeconds(12));
    }

    /**
     * Wait for SPA/document paint so execute does not soft-fail on a blank white page.
     */
    static boolean waitForLoginForm(WebDriver driver, Duration timeout) {
        if (driver == null) {
            return false;
        }
        waitForDocumentReady(driver, timeout);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (pageHasLoginForm(driver)) {
                return true;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return pageHasLoginForm(driver);
            }
        }
        return pageHasLoginForm(driver);
    }

    static void waitForDocumentReady(WebDriver driver, Duration timeout) {
        if (driver == null) {
            return;
        }
        try {
            new WebDriverWait(driver, timeout).until(d -> {
                try {
                    Object rs = ((JavascriptExecutor) d).executeScript("return document.readyState");
                    return "complete".equals(String.valueOf(rs));
                } catch (RuntimeException e) {
                    return true;
                }
            });
        } catch (RuntimeException ignored) {
            // Best-effort; caller still checks for the form.
        }
    }

    static boolean clickLoginEntryControl(WebDriver driver) {
        try {
            List<WebElement> candidates = driver.findElements(By.cssSelector("a, button, [role='button']"));
            WebElement best = null;
            int bestScore = 0;
            for (WebElement el : candidates) {
                if (el == null || !el.isDisplayed() || !el.isEnabled()) {
                    continue;
                }
                int score = loginEntryScore(el);
                if (score > bestScore) {
                    bestScore = score;
                    best = el;
                }
            }
            if (best == null || bestScore <= 0) {
                return false;
            }
            best.click();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static int loginEntryScore(WebElement el) {
        String text = safe(el.getText());
        String href = safe(el.getAttribute("href"));
        String aria = safe(el.getAttribute("aria-label"));
        String id = safe(el.getAttribute("id"));
        String name = safe(el.getAttribute("name"));
        String dataTest = safe(el.getAttribute("data-test"))
                + " " + safe(el.getAttribute("data-testid"));
        String blob = (text + " " + href + " " + aria + " " + id + " " + name + " " + dataTest)
                .toLowerCase(Locale.ROOT);

        int score = 0;
        if (LOGIN_HREF.matcher(href).find() || LOGIN_HREF.matcher(blob).find()) {
            score += 8;
        }
        if (LOGIN_TEXT.matcher(text).find() || LOGIN_TEXT.matcher(aria).find()) {
            score += 6;
        }
        if (blob.contains("login") || blob.contains("signin") || blob.contains("sign-in")) {
            score += 3;
        }
        // Prefer real links/buttons over noisy matches
        String tag = safe(el.getTagName()).toLowerCase(Locale.ROOT);
        if ("a".equals(tag) || "button".equals(tag)) {
            score += 1;
        }
        return score;
    }

    static void navigateToBasePath(WebDriver driver, String baseUrl, String path) {
        if (driver == null || path == null || path.isBlank()) {
            return;
        }
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String p = path.startsWith("/") ? path : "/" + path;
        try {
            // Absolute URL in Excel path
            if (path.startsWith("http://") || path.startsWith("https://")) {
                driver.get(path);
                return;
            }
            driver.get(base + p);
        } catch (Exception ignored) {
            // Caller checks for login form / evidence on failure.
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
