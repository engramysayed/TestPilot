package project.utils.Actions;

import project.drivers.WebDriverFactory;

public final class Alerts {
    private Alerts() {
    }

    private static AlertsHandler h() {
        return new AlertsHandler(WebDriverFactory.getDriver());
    }

    public static void acceptAlert() {
        h().acceptAlert();
    }

    public static void dismissAlert() {
        h().dismissAlert();
    }

    public static void sendKeysToAlert(String keys) {
        h().sendKeysToAlert(keys);
    }

    public static String getAlertText() {
        return h().getAlertText();
    }
}
