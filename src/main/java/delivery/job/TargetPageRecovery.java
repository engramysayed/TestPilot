package delivery.job;

import delivery.authoring.DomCandidate;
import delivery.authoring.DomCandidateExtractor;
import delivery.authoring.StepIntentBinder;
import delivery.excel.ManualTestCase;
import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import utils.LogsManager;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * When a named field is missing, recover using the Excel open-path or an on-page
 * register/sign-up control. Does not invent URLs.
 */
public final class TargetPageRecovery {
    private static final Pattern SIGNUP_HREF = Pattern.compile(
            "(?i)(/reg|/register|/signup|/sign-up|/r\\.php|/join)(/|\\?|#|$)");
    private static final Pattern SIGNUP_TEXT = Pattern.compile(
            "(?i)\\b(create\\s+(new\\s+)?account|sign\\s*up|register|join\\s+now)\\b");
    private static final Pattern LOGIN_HREF = Pattern.compile(
            "(?i)(/login|/signin|/sign-in|/auth)(/|\\?|#|$)");
    private static final Pattern LOGIN_TEXT = Pattern.compile(
            "(?i)\\b(log\\s*in|sign\\s*in|sign\\s*on)\\b");

    private TargetPageRecovery() {
    }

    /**
     * @return true if the browser was navigated or a signup entry was clicked
     */
    public static boolean tryRecover(
            WebDriverFactory driverFactory,
            String baseUrl,
            ManualTestCase tc,
            StepIntentBinder.IntentLine intent,
            String html
    ) {
        if (driverFactory == null) {
            return false;
        }
        String excelPath = StepIntentBinder.firstOpenPath(
                tc == null ? null : tc.preconditions(),
                tc == null ? null : tc.steps());
        WebDriver driver = driverFactory.get();
        if (driver == null) {
            return false;
        }
        String currentUrl;
        try {
            currentUrl = driver.getCurrentUrl();
        } catch (WebDriverException e) {
            return false;
        }
        boolean fieldMissing = isRecoverableFieldIntent(intent) && !namedFieldPresent(intent, html);
        boolean submitLeft = StepIntentBinder.wantsFormSubmit(intent == null ? "" : intent.text())
                && shouldReopenExcelPath(currentUrl, excelPath);
        if (!fieldMissing && !submitLeft) {
            return false;
        }
        if (excelPath != null && !excelPath.isBlank() && shouldReopenExcelPath(currentUrl, excelPath)) {
            LogsManager.info("TARGET_RECOVER reopen excel path=" + excelPath + " from=" + currentUrl);
            LoginFormNavigator.navigateToBasePath(driver, baseUrl, excelPath);
            return true;
        }
        if (excelPath != null && !excelPath.isBlank()) {
            return false;
        }
        return clickSignupEntryControl(driver);
    }

    public static boolean shouldReopenExcelPath(String currentUrl, String excelPath) {
        if (currentUrl == null || currentUrl.isBlank() || excelPath == null || excelPath.isBlank()) {
            return false;
        }
        String want = pathOnly(excelPath);
        String have = pathOnly(currentUrl);
        if (want.isEmpty() || "/".equals(want)) {
            return false;
        }
        return !pathMatches(have, want);
    }

    static int signupEntryScore(String tag, String text, String href) {
        String t = text == null ? "" : text;
        String h = href == null ? "" : href;
        if (LOGIN_HREF.matcher(h).find() || LOGIN_TEXT.matcher(t).find()) {
            return 0;
        }
        int score = 0;
        if (SIGNUP_HREF.matcher(h).find()) {
            score += 8;
        }
        if (SIGNUP_TEXT.matcher(t).find()) {
            score += 6;
        }
        String tagSafe = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
        if (score > 0 && ("a".equals(tagSafe) || "button".equals(tagSafe))) {
            score += 1;
        }
        return score;
    }

    static boolean namedFieldPresent(StepIntentBinder.IntentLine intent, String html) {
        if (intent == null || html == null || html.isBlank()) {
            return false;
        }
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        for (DomCandidate c : candidates) {
            if (!StepIntentBinder.candidateSharesFieldToken(intent.text(), c)) {
                continue;
            }
            String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
            if ("input".equals(tag) || "textarea".equals(tag) || "select".equals(tag)
                    || "label".equals(tag)) {
                return true;
            }
        }
        return false;
    }

    static boolean isRecoverableFieldIntent(StepIntentBinder.IntentLine intent) {
        if (intent == null || intent.kind() == null) {
            return false;
        }
        return switch (intent.kind()) {
            case TYPE_FIELD, TYPE_USER, TYPE_PASS -> true;
            case ASSERT_VISIBLE -> looksLikeFieldAssert(intent.text());
            default -> false;
        };
    }

    private static boolean looksLikeFieldAssert(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("field") || t.contains("first name") || t.contains("last name")
                || t.contains("email") || t.contains("password") || t.contains("username")
                || t.contains("input") || t.contains("textbox");
    }

    private static boolean clickSignupEntryControl(WebDriver driver) {
        try {
            List<WebElement> candidates = driver.findElements(By.cssSelector("a, button, [role='button']"));
            WebElement best = null;
            int bestScore = 0;
            for (WebElement el : candidates) {
                if (el == null || !el.isDisplayed() || !el.isEnabled()) {
                    continue;
                }
                int score = signupEntryScore(el.getTagName(), el.getText(), el.getAttribute("href"));
                if (score > bestScore) {
                    bestScore = score;
                    best = el;
                }
            }
            if (best == null || bestScore <= 0) {
                return false;
            }
            LogsManager.info("TARGET_RECOVER click signup entry text=" + best.getText());
            best.click();
            return true;
        } catch (WebDriverException e) {
            return false;
        }
    }

    private static boolean pathMatches(String have, String want) {
        String h = trimSlashes(have).toLowerCase(Locale.ROOT);
        String w = trimSlashes(want).toLowerCase(Locale.ROOT);
        return h.equals(w) || h.startsWith(w + "/");
    }

    private static String pathOnly(String urlOrPath) {
        String s = urlOrPath.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            try {
                String p = URI.create(s).getPath();
                return p == null || p.isBlank() ? "/" : p;
            } catch (IllegalArgumentException e) {
                return s;
            }
        }
        return s.startsWith("/") ? s : "/" + s;
    }

    private static String trimSlashes(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path;
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
