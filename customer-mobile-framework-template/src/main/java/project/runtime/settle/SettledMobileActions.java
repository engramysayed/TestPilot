package project.runtime.settle;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import project.runtime.waits.MobileWait;
import project.utils.reports.AllureSteps;
import project.utils.reports.Secrets;

/**
 * All screen methods call here (one-liners). Owns Allure wrap + settle + outcome classify.
 * <p><b>No blocking sleep</b> — settle uses {@link SettleExecutor} + {@link SettleProbe}.
 * Unit tests inject {@link #setSettleProbeFactory} / {@link #setSettleShortCircuit(boolean)}.
 */
public final class SettledMobileActions {

    private static final SettleExecutor EXECUTOR = new SettleExecutor();
    private static volatile boolean shortCircuitSettle = false;
    private static volatile SettleProbeFactory probeFactory = SettledMobileActions::defaultProbe;

    @FunctionalInterface
    public interface SettleProbeFactory {
        SettleProbe create(AndroidDriver driver, String preFingerprint);
    }

    private SettledMobileActions() {}

    /** Package/test hook: skip live settle polls (returns NO_CHANGE unless hierarchy differs). */
    static void setSettleShortCircuit(boolean enabled) {
        shortCircuitSettle = enabled;
    }

    static void setSettleProbeFactory(SettleProbeFactory factory) {
        probeFactory = factory == null ? SettledMobileActions::defaultProbe : factory;
    }

    static void resetHooks() {
        shortCircuitSettle = false;
        probeFactory = SettledMobileActions::defaultProbe;
    }

    public static ActionOutcome tap(AndroidDriver driver, By locator, String settleKind) {
        require(driver, locator);
        return AllureSteps.step("tap " + locator, () -> {
            String pre = safeSource(driver);
            WebElement el = MobileWait.untilActionable(driver, locator);
            el.click();
            return settle(driver, settleKind, pre);
        });
    }

    public static ActionOutcome type(AndroidDriver driver, By locator, String value, String settleKind) {
        require(driver, locator);
        if (value == null) {
            throw new IllegalArgumentException("value required");
        }
        return AllureSteps.step("type " + locator, () -> {
            String pre = safeSource(driver);
            WebElement el = MobileWait.untilActionable(driver, locator);
            el.clear();
            el.sendKeys(value);
            // Unicode path: sendKeys only — never adb input text for non-ASCII
            return settle(driver, settleKind == null ? "TYPE" : settleKind, pre);
        });
    }

    public static ActionOutcome typeSecret(AndroidDriver driver, By locator, String secretRefKey) {
        require(driver, locator);
        if (secretRefKey == null || secretRefKey.isBlank()) {
            throw new IllegalArgumentException("secretRefKey required");
        }
        return AllureSteps.step("typeSecret " + locator + " ref=" + Secrets.refLabel(secretRefKey), () -> {
            String pre = safeSource(driver);
            String resolved = project.config.SecretResolver.resolve(secretRefKey);
            WebElement el = MobileWait.untilActionable(driver, locator);
            el.clear();
            el.sendKeys(resolved);
            // TYPE_SECRET: no read-back / change-wait
            return settle(driver, "TYPE_SECRET", pre);
        });
    }

    public static ActionOutcome clear(AndroidDriver driver, By locator) {
        require(driver, locator);
        return AllureSteps.step("clear " + locator, () -> {
            String pre = safeSource(driver);
            WebElement el = MobileWait.untilActionable(driver, locator);
            el.clear();
            return settle(driver, "CLEAR", pre);
        });
    }

    public static ActionOutcome longPress(AndroidDriver driver, By locator) {
        require(driver, locator);
        return AllureSteps.step("longPress " + locator, () -> {
            String pre = safeSource(driver);
            // Real long-press uses W3C actions; scaffold invokes click as stand-in when gestures util unavailable
            project.runtime.gestures.MobileGestures.longPress(driver, locator);
            return settle(driver, "LONG_PRESS", pre);
        });
    }

    private static ActionOutcome settle(AndroidDriver driver, String settleKind, String pre) {
        SettleActionKind kind = SettleActionKind.parse(settleKind);
        SettleProbe probe;
        if (shortCircuitSettle) {
            probe = SettleProbe.immediate(pre);
        } else {
            probe = probeFactory.create(driver, pre);
        }
        return EXECUTOR.settle(kind, pre, probe);
    }

    private static SettleProbe defaultProbe(AndroidDriver driver, String preFingerprint) {
        return new SettleProbe() {
            @Override
            public String hierarchyFingerprint() {
                return safeSource(driver);
            }

            @Override
            public Double screenshotChangedRatio() {
                return null;
            }

            @Override
            public long awaitPoll(long maxMs) {
                // Bounded driver-side wait via MobileWait poll helper — never blocking sleep
                return MobileWait.pollQuietly(driver, maxMs);
            }
        };
    }

    private static String safeSource(AndroidDriver driver) {
        try {
            String src = driver.getPageSource();
            return src == null ? "" : Integer.toHexString(src.hashCode());
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void require(AndroidDriver driver, By locator) {
        if (driver == null || locator == null) {
            throw new IllegalArgumentException("driver/locator required");
        }
    }
}
