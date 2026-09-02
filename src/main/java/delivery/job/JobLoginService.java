package delivery.job;

import drivers.WebDriverFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Job-scoped application login before TC authoring.
 * Site-agnostic: username/password fields + submit; no host-specific branches.
 */
public class JobLoginService {

    public void loginIfNeeded(WebDriverFactory driverFactory, ConversionJobRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            return;
        }
        if (request.password() == null) {
            throw new IllegalStateException("LOGIN_FAILED: password is required when username is provided");
        }
        WebDriver driver = driverFactory.get();
        try {
            loginWithCredentials(driver, request.username(), request.password());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("LOGIN_FAILED: " + e.getMessage(), e);
        }
    }

    private void loginWithCredentials(WebDriver driver, String username, String password) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        By user = By.cssSelector(
                "input[data-test='username'], input[data-testid='username'], "
                        + "input[name='username'], input[id='username'], "
                        + "input[autocomplete='username'], input[type='email']");
        By pass = By.cssSelector(
                "input[data-test='password'], input[data-testid='password'], "
                        + "input[name='password'], input[id='password'], "
                        + "input[type='password']");
        By login = loginClickCss();
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

        // Site-agnostic success: URL change and/or login fields gone (avoid host-specific paths)
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

    /** Visible for unit tests — CSS login/submit click shortlist (site-agnostic). */
    static By loginClickCss() {
        return By.cssSelector(
                "button[data-test='login-button'], input[data-test='login-button'], "
                        + "button[data-testid='login-button'], "
                        + "button[type='submit'], input[type='submit'], "
                        + "button#submit, input#submit, #submit, #login, "
                        + "button#login, input#login, [name='submit'], [name='login']");
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
