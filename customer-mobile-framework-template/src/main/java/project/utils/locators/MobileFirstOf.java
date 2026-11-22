package project.utils.locators;

import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ordered first-match-wins fallback for Android locators (mobile analogue of the web {@code FirstOf}).
 * Wraps one primary By and N verified fallbacks: on {@link #findElement} / {@link #findElements} it walks
 * them in order and returns the first slot whose match is non-empty. Each slot records a WIN/MISS line
 * via {@link MobileLocatorOutcomes#append} so the portal's locator-outcomes ledger can rank them later.
 *
 * <p>Usage from generated code:
 * <pre>{@code
 * protected final By signInButton = MobileFirstOf.of(
 *     "LoginScreen", "signInButton",
 *     AppiumBy.accessibilityId("login_submit"),
 *     AppiumBy.id("com.app:id/signin"),
 *     AppiumBy.androidUIAutomator("new UiSelector().text(\"Sign in\")"));
 * }</pre>
 *
 * <p>Single-strategy locators stay as plain {@code AppiumBy.*} — the template only emits a FirstOf when
 * the Prove step validated more than one candidate.
 */
public final class MobileFirstOf extends By {

    private final String screenClassName;
    private final String fieldName;
    private final List<By> slots;

    private MobileFirstOf(String screenClassName, String fieldName, List<By> slots) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("MobileFirstOf needs at least one By");
        }
        this.screenClassName = screenClassName == null ? "" : screenClassName;
        this.fieldName = fieldName == null ? "" : fieldName;
        this.slots = List.copyOf(slots);
    }

    public static By of(String screenClassName, String fieldName, By primary, By... fallbacks) {
        if (primary == null) {
            throw new IllegalArgumentException("primary By required");
        }
        List<By> all = new ArrayList<>();
        all.add(primary);
        if (fallbacks != null) {
            for (By b : fallbacks) {
                if (b != null) all.add(b);
            }
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        return new MobileFirstOf(screenClassName, fieldName, all);
    }

    @Override
    public WebElement findElement(SearchContext context) {
        Throwable last = null;
        for (int i = 0; i < slots.size(); i++) {
            By by = slots.get(i);
            try {
                WebElement el = by.findElement(context);
                if (el != null) {
                    MobileLocatorOutcomes.append(screenClassName, fieldName, i, by, "WIN");
                    return el;
                }
                MobileLocatorOutcomes.append(screenClassName, fieldName, i, by, "MISS");
            } catch (NoSuchElementException e) {
                MobileLocatorOutcomes.append(screenClassName, fieldName, i, by, "MISS");
                last = e;
            } catch (RuntimeException e) {
                MobileLocatorOutcomes.append(screenClassName, fieldName, i, by, "MISS");
                last = e;
            }
        }
        if (last instanceof NoSuchElementException nse) throw nse;
        throw new NoSuchElementException("MobileFirstOf exhausted slots for " + fieldName + ": " + slots);
    }

    @Override
    public List<WebElement> findElements(SearchContext context) {
        for (int i = 0; i < slots.size(); i++) {
            By by = slots.get(i);
            try {
                List<WebElement> found = by.findElements(context);
                if (found != null && !found.isEmpty()) {
                    MobileLocatorOutcomes.append(screenClassName, fieldName, i, by, "WIN");
                    return found;
                }
                MobileLocatorOutcomes.append(screenClassName, fieldName, i, by, "MISS");
            } catch (RuntimeException e) {
                MobileLocatorOutcomes.append(screenClassName, fieldName, i, by, "MISS");
            }
        }
        return new ArrayList<>();
    }

    @Override
    public String toString() {
        return "MobileFirstOf(" + screenClassName + "." + fieldName + " = " + slots + ")";
    }

    /** For tests + diagnostics; not part of the user-facing API. */
    List<By> slotsForTest() {
        return List.copyOf(slots);
    }

    /** Factory for tests needing a known-fallback shape. */
    static By ofForTest(String screen, String field, By... all) {
        if (all == null || all.length == 0) {
            throw new IllegalArgumentException("need at least one By");
        }
        By primary = all[0];
        By[] rest = all.length > 1 ? Arrays.copyOfRange(all, 1, all.length) : new By[0];
        return of(screen, field, primary, rest);
    }

    /** Factory for when the generator built the full list already. */
    public static By ofList(String screen, String field, List<By> all) {
        if (all == null || all.isEmpty()) {
            throw new IllegalArgumentException("need at least one By");
        }
        By primary = all.get(0);
        By[] rest = all.size() > 1 ? all.subList(1, all.size()).toArray(new By[0]) : new By[0];
        return of(screen, field, primary, rest);
    }

    /** Guard-rail: keep the AppiumBy import live when unused in a slim test. */
    @SuppressWarnings("unused")
    private static final Class<?> APPIUM_BY = AppiumBy.class;
}
