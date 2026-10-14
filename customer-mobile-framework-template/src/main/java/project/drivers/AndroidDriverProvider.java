package project.drivers;

import io.appium.java_client.android.AndroidDriver;

/** Provides the current AndroidDriver for listeners/evidence. */
public interface AndroidDriverProvider {
    /** @return live driver or null if no session */
    AndroidDriver getAndroidDriver();
}
