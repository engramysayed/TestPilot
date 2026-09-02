package delivery.job;

import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

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
            List<WebElement> passwords = driver.findElements(By.cssSelector("input[type='password']"));
            return passwords.stream().anyMatch(WebElement::isDisplayed);
        } catch (Exception e) {
            return false;
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
        if (pageHasLoginForm(driverFactory)) {
            return true;
        }
        WebDriver driver = driverFactory.get();

        String excelPath = StepIntentBinder.firstOpenPath(
                tc == null ? null : tc.preconditions(),
                tc == null ? null : tc.steps());
        if (excelPath != null && !excelPath.isBlank()) {
            navigateToBasePath(driver, baseUrl, excelPath);
            if (pageHasLoginForm(driver)) {
                return true;
            }
        }

        if (clickLoginEntryControl(driver)) {
            return pageHasLoginForm(driver);
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
        navigateToBasePath(driverFactory.get(), baseUrl, path);
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
