package delivery.job;

import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Job-scoped application login before TC authoring / hunt prelude.
 * Prefers project preferred-hook attributes, then generic username/password selectors.
 */
public class JobLoginService {

    public void loginIfNeeded(WebDriverFactory driverFactory, ConversionJobRequest request) {
        loginIfNeeded(driverFactory, request, List.of());
    }

    public void loginIfNeeded(
            WebDriverFactory driverFactory,
            ConversionJobRequest request,
            List<String> preferredHooks
    ) {
        if (request.username() == null || request.username().isBlank()) {
            return;
        }
        if (request.password() == null) {
            throw new IllegalStateException("LOGIN_FAILED: password is required when username is provided");
        }
        WebDriver driver = driverFactory.get();
        try {
            loginWithCredentials(driver, request.username(), request.password(), preferredHooks);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("LOGIN_FAILED: " + e.getMessage(), e);
        }
    }

    private void loginWithCredentials(
            WebDriver driver,
            String username,
            String password,
            List<String> preferredHooks
    ) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        By user = By.cssSelector(usernameCss(preferredHooks));
        By pass = By.cssSelector(passwordCss(preferredHooks));
        By login = By.cssSelector(submitCss(preferredHooks));
        By loginXpath = loginClickXpath();

        String beforeUrl = safeUrl(driver);
        WebElement userEl = wait.until(ExpectedConditions.visibilityOfElementLocated(user));
        userEl.clear();
        userEl.sendKeys(username);
        WebElement passEl = driver.findElement(pass);
        passEl.clear();
        passEl.sendKeys(password);
        WebElement loginEl = firstDisplayed(driver, login).orElse(null);
        if (loginEl == null) {
            loginEl = wait.until(ExpectedConditions.elementToBeClickable(loginXpath));
        }
        loginEl.click();

        wait.until(d -> {
            String now = safeUrl(d);
            boolean urlChanged = !now.equals(beforeUrl);
            boolean loginGone = d.findElements(user).isEmpty()
                    || d.findElements(user).stream().noneMatch(WebElement::isDisplayed);
            return urlChanged || loginGone;
        });

        List<WebElement> stillVisibleUser = driver.findElements(user).stream()
                .filter(WebElement::isDisplayed)
                .toList();
        if (!stillVisibleUser.isEmpty() && safeUrl(driver).equals(beforeUrl)) {
            throw new IllegalStateException(
                    "LOGIN_FAILED: still on login form after submit (check credentials or selectors)");
        }
    }

    /** Username field CSS: project preferred-hook patterns first (if any), then legacy generics. */
    static String usernameCss(List<String> preferredHooks) {
        List<String> parts = new ArrayList<>();
        for (String hook : safeHooks(preferredHooks)) {
            parts.add("input[" + hook + "*='user']");
            parts.add("input[" + hook + "*='User']");
            parts.add("input[" + hook + "*='username']");
            parts.add("input[" + hook + "*='email']");
            parts.add("input[" + hook + "*='Email']");
            parts.add("input[" + hook + "*='login']");
        }
        parts.add("input[data-test='username']");
        parts.add("input[data-testid='username']");
        parts.add("input[name='username']");
        parts.add("input[id='username']");
        parts.add("input[autocomplete='username']");
        parts.add("input[type='email']");
        return String.join(", ", parts);
    }

    static String passwordCss(List<String> preferredHooks) {
        List<String> parts = new ArrayList<>();
        for (String hook : safeHooks(preferredHooks)) {
            parts.add("input[" + hook + "*='password']");
            parts.add("input[" + hook + "*='Password']");
            parts.add("input[" + hook + "*='pass']");
        }
        parts.add("input[data-test='password']");
        parts.add("input[data-testid='password']");
        parts.add("input[name='password']");
        parts.add("input[id='password']");
        parts.add("input[type='password']");
        return String.join(", ", parts);
    }

    static String submitCss(List<String> preferredHooks) {
        List<String> parts = new ArrayList<>();
        for (String hook : safeHooks(preferredHooks)) {
            parts.add("button[" + hook + "*='sign']");
            parts.add("button[" + hook + "*='Sign']");
            parts.add("button[" + hook + "*='login']");
            parts.add("button[" + hook + "*='Login']");
            parts.add("button[" + hook + "*='submit']");
            parts.add("[" + hook + "*='sign_In']");
            parts.add("[" + hook + "*='sign-In']");
            parts.add("[" + hook + "*='login']");
        }
        parts.add("button[data-test='login-button']");
        parts.add("input[data-test='login-button']");
        parts.add("button[data-testid='login-button']");
        parts.add("button[type='submit']");
        parts.add("input[type='submit']");
        parts.add("button#submit");
        parts.add("input#submit");
        parts.add("#submit");
        parts.add("#login");
        parts.add("button#login");
        parts.add("input#login");
        parts.add("[name='submit']");
        parts.add("[name='login']");
        return String.join(", ", parts);
    }

    /** Visible for unit tests — CSS login/submit click shortlist (site-agnostic). */
    static By loginClickCss() {
        return By.cssSelector(submitCss(List.of()));
    }

    static By loginClickXpath() {
        return By.xpath(
                "//button[contains(translate(normalize-space(.),"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'submit') "
                        + "or contains(translate(normalize-space(.),"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'log in') "
                        + "or contains(translate(normalize-space(.),"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'login') "
                        + "or contains(translate(normalize-space(.),"
                        + "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'sign in')]"
                        + "|//input[@type='submit']");
    }

    private static List<String> safeHooks(List<String> preferredHooks) {
        if (preferredHooks == null || preferredHooks.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String h : preferredHooks) {
            if (h == null || h.isBlank()) {
                continue;
            }
            String name = h.trim().toLowerCase(Locale.ROOT);
            if (!name.matches("^[a-z][a-z0-9_-]{0,62}$")) {
                continue;
            }
            out.add(name);
        }
        return out;
    }

    private static java.util.Optional<WebElement> firstDisplayed(WebDriver driver, By by) {
        try {
            return driver.findElements(by).stream()
                    .filter(WebElement::isDisplayed)
                    .filter(WebElement::isEnabled)
                    .findFirst();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static String safeUrl(WebDriver driver) {
        String url = driver.getCurrentUrl();
        return url == null ? "" : url;
    }
}
