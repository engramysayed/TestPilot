package project.utils.Actions;

import org.openqa.selenium.By;
import project.drivers.WebDriverFactory;

public final class Frames {
    private Frames() {
    }

    private static FramesHandler h() {
        return new FramesHandler(WebDriverFactory.getDriver());
    }

    public static void switchToFrameByIndex(int index) {
        h().switchToFrameByIndex(index);
    }

    public static void switchToFrameByNameOrId(String nameOrId) {
        h().switchToFrameByNameOrId(nameOrId);
    }

    public static void switchToFrameByElement(By locator) {
        h().switchToFrameByElement(locator);
    }

    public static void switchToDefaultContent() {
        h().switchToDefaultContent();
    }
}
