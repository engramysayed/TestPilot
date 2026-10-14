package project.utils.locators;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import project.drivers.WebDriverFactory;
import project.utils.reports.AllureSteps;
import project.utils.settle.Settle;

/**
 * Settled smart/drag verbs for generated pages: Allure + Settle + SmartWait + {@link KeelLocate#reset}.
 * Plain (non-smart) clicks/types stay on {@code driver.element()} in page methods.
 */
public final class SettledActions {
    private SettledActions() {
    }

    public static void click(WebDriverFactory driver, By locator, String settleKind, String stepName) {
        click(driver, locator, settleKind, stepName, false);
    }

    public static void click(
            WebDriverFactory driver, By locator, String settleKind, String stepName, boolean pointer) {
        AllureSteps.run(stepName, () -> {
            WebDriver wd = driver.get();
            try {
                Settle.after(wd, settleKind, locator, () -> {
                    SmartWait.untilActionable(wd, locator);
                    if (pointer) {
                        KeelActions.click(wd, locator);
                    } else {
                        driver.element().click(locator);
                    }
                });
            } finally {
                KeelLocate.reset(wd);
            }
        });
    }

    public static void type(WebDriverFactory driver, By locator, String value, String settleKind, String stepName) {
        AllureSteps.run(stepName, () -> {
            WebDriver wd = driver.get();
            try {
                Settle.after(wd, settleKind, locator, () -> {
                    SmartWait.untilActionable(wd, locator);
                    driver.element().type(locator, value);
                });
            } finally {
                KeelLocate.reset(wd);
            }
        });
    }

    public static void select(WebDriverFactory driver, By locator, String value, String settleKind, String stepName) {
        AllureSteps.run(stepName, () -> {
            WebDriver wd = driver.get();
            try {
                Settle.after(wd, settleKind, locator, () -> {
                    SmartWait.untilActionable(wd, locator);
                    driver.element().selectFromDD(locator, value);
                });
            } finally {
                KeelLocate.reset(wd);
            }
        });
    }

    public static void drag(WebDriverFactory driver, By source, By drop, String stepName) {
        AllureSteps.run(stepName, () -> {
            WebDriver wd = driver.get();
            try {
                Settle.after(wd, "drag", source, () -> {
                    SmartWait.untilActionable(wd, source);
                    KeelActions.drag(wd, source, drop);
                });
            } finally {
                KeelLocate.reset(wd);
            }
        });
    }
}
