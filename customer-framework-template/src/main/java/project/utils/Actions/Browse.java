package project.utils.Actions;

import project.drivers.WebDriverFactory;

public final class Browse {
    private Browse() {
    }

    private static BrowserHandler h() {
        return new BrowserHandler(WebDriverFactory.getDriver());
    }

    public static void maximizeWindow() {
        h().maximizeWindow();
    }

    public static void navigateToUrl(String url) {
        h().navigateToUrl(url);
    }

    public static void refreshPage() {
        h().refreshPage();
    }

    public static void navigateBack() {
        h().navigateBack();
    }

    public static String getCurrentUrl() {
        return h().getCurrentUrl();
    }

    public static void closeWindow() {
        h().closeWindow();
    }

    public static void newWindow() {
        h().newWindow();
    }

    public static void customTab(String tab) {
        h().customTab(tab);
    }
}
