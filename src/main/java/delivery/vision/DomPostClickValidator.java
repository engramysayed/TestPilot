package delivery.vision;

import delivery.job.LoginFormNavigator;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import utils.LogsManager;
import utils.PropertyReader;

import java.util.Locale;

/**
 * Cheap DOM proof after a click: URL/title/body/login-form changed?
 * Prefer this over VLM assert as the primary "did the click work?" signal.
 */
public final class DomPostClickValidator {

    public enum Status {
        OK,
        WEAK,
        FAIL,
        SKIP
    }

    public record Snapshot(String url, String title, String bodyFingerprint, boolean loginFormVisible) {
    }

    public record Result(Status status, String reason) {
        public boolean failsStrict() {
            return status == Status.FAIL;
        }
    }

    private DomPostClickValidator() {
    }

    public static boolean enabled() {
        return booleanProp("delivery.vision.dom-post-click.enabled", true);
    }

    public static boolean strict() {
        return booleanProp("delivery.vision.dom-post-click.strict", false);
    }

    public static Snapshot capture(WebDriver driver) {
        if (driver == null) {
            return new Snapshot("", "", "", false);
        }
        String url = "";
        String title = "";
        String body = "";
        boolean loginForm = false;
        try {
            url = nullToEmpty(driver.getCurrentUrl());
        } catch (RuntimeException ignored) {
            // keep empty
        }
        try {
            title = nullToEmpty(driver.getTitle());
        } catch (RuntimeException ignored) {
            // keep empty
        }
        try {
            WebElement bodyEl = driver.findElement(By.tagName("body"));
            String text = bodyEl.getText();
            body = fingerprint(text);
        } catch (RuntimeException ignored) {
            body = "";
        }
        try {
            loginForm = LoginFormNavigator.pageHasLoginForm(driver);
        } catch (RuntimeException ignored) {
            loginForm = false;
        }
        return new Snapshot(url, title, body, loginForm);
    }

    public static Result validate(Snapshot before, WebDriver driver, String action, String targetHint) {
        if (!enabled()) {
            return new Result(Status.SKIP, "dom-post-click disabled");
        }
        if (action == null || !action.toLowerCase(Locale.ROOT).contains("click")) {
            return new Result(Status.SKIP, "not a click");
        }
        if (before == null || driver == null) {
            return new Result(Status.SKIP, "missing driver/snapshot");
        }
        // SPA login/OTP often paints after the Selenium click returns.
        Result last = compare(before, capture(driver), targetHint);
        if (last.status() != Status.FAIL) {
            return last;
        }
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline && last.status() == Status.FAIL) {
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            last = compare(before, capture(driver), targetHint);
        }
        return last;
    }

    /** Pure comparison for unit tests / offline checks. */
    public static Result compare(Snapshot before, Snapshot after, String targetHint) {
        if (before == null || after == null) {
            return new Result(Status.SKIP, "missing snapshot");
        }
        boolean urlChanged = !nullToEmpty(before.url()).equals(nullToEmpty(after.url()));
        boolean titleChanged = !nullToEmpty(before.title()).equals(nullToEmpty(after.title()));
        boolean bodyChanged = !nullToEmpty(before.bodyFingerprint())
                .equals(nullToEmpty(after.bodyFingerprint()));
        boolean loginCleared = before.loginFormVisible() && !after.loginFormVisible();

        if (urlChanged || titleChanged || loginCleared) {
            String why = urlChanged ? "url-changed" : (loginCleared ? "login-form-cleared" : "title-changed");
            LogsManager.info("DOM_POST_CLICK: OK " + why);
            return new Result(Status.OK, why);
        }
        if (bodyChanged) {
            LogsManager.info("DOM_POST_CLICK: WEAK body-changed");
            return new Result(Status.WEAK, "body-changed-only");
        }
        String hint = targetHint == null ? "" : targetHint.trim();
        LogsManager.info("DOM_POST_CLICK: FAIL no-dom-change hint=" + hint);
        return new Result(Status.FAIL, "no-url-title-body-login-change after click");
    }

    private static String fingerprint(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() > 400) {
            t = t.substring(0, 400);
        }
        return Integer.toHexString(t.hashCode()) + ":" + t.length();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean booleanProp(String key, boolean defaultValue) {
        String p = System.getProperty(key);
        if (p == null || p.isBlank()) {
            p = PropertyReader.getProperty(key);
        }
        if (p == null || p.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(p.trim());
    }
}
