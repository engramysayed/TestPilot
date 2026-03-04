package utils;

import utils.Config.AppConfigProvider;

public final class RuntimeSettings {
    private static volatile int globalWait = AppConfigProvider.get().defaultWait();
    private static volatile int screenshotWait = AppConfigProvider.get().defaultScreenshotWait();


    public static int getGlobalWait() {
        return globalWait;
    }

    public static int getScreenshotWait() {
        return screenshotWait;
    }

    public static void setGlobalWait(int waitSeconds) {
        if (waitSeconds > 0) {
            globalWait = waitSeconds;
        }
    }

    public static void setScreenshotWait(int waitSeconds) {
        if (waitSeconds >= 0) {
            screenshotWait = waitSeconds;
        }
    }
}
