package project.utils.locators;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import project.utils.Logs.LogsManager;

import java.util.List;

/**
 * Pointer actions for Keel v2 targets: clicks at a provider=/point= offset and drag between two
 * targets. Offsets are from the element centre (Selenium Actions origin), as resolved by
 * {@link KeelLocate#resolveClick}. Callers reset frames with {@link KeelLocate#reset} afterwards.
 */
public final class KeelActions {
    private KeelActions() {
    }

    /** Clicks the anchor at its provider=/point= offset (centre for plain targets). */
    public static void click(WebDriver driver, By locator) {
        KeelLocate.ClickPoint p = point(driver, locator);
        new Actions(driver).moveToElement(p.element(), (int) Math.round(p.dxFromCentre()), (int) Math.round(p.dyFromCentre()))
                .click().perform();
        LogsManager.info("keel: clicked " + locator + " at offset " + p.dxFromCentre() + "," + p.dyFromCentre());
    }

    /**
     * Drag from {@code source} to {@code drop} (spec §4 drag proposal: two targets, actionHint "drag").
     * Both must live in the same frame (one Actions sequence cannot cross frames).
     */
    public static void drag(WebDriver driver, By source, By drop) {
        KeelLocate.ClickPoint dst = point(driver, drop);
        List<KeelLocate.Hop> dstFrames = frames(drop);
        KeelLocate.ClickPoint src = point(driver, source);   // resolved last: the driver stays in the source frame
        if (dstFrames != null && !dstFrames.equals(frames(source))) {
            throw new UnsupportedOperationException("keel: drag source and drop target are in different frames: "
                    + source + " -> " + drop);
        }
        WebElement dstEl = dst.element();
        new Actions(driver)
                .moveToElement(src.element(), (int) Math.round(src.dxFromCentre()), (int) Math.round(src.dyFromCentre()))
                .clickAndHold()
                .moveToElement(dstEl, (int) Math.round(dst.dxFromCentre()), (int) Math.round(dst.dyFromCentre()))
                .release()
                .perform();
        LogsManager.info("keel: dragged " + source + " -> " + drop);
    }

    private static KeelLocate.ClickPoint point(WebDriver driver, By locator) {
        if (locator instanceof KeelBy k) {
            return k.clickPoint(driver);
        }
        driver.switchTo().defaultContent();
        return new KeelLocate.ClickPoint(driver.findElement(locator), 0, 0);
    }

    private static List<KeelLocate.Hop> frames(By locator) {
        if (!(locator instanceof KeelBy k)) {
            return List.of();
        }
        return k.winningContext().stream().filter(h -> h.kind() == KeelLocate.Kind.FRAME).toList();
    }
}
