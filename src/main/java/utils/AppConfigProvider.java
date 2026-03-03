package utils;

import org.aeonbits.owner.ConfigFactory;

public final class AppConfigProvider {
    private static final AppConfig CONFIG = ConfigFactory.create(AppConfig.class);

    private AppConfigProvider() {
    }

    public static AppConfig get() {
        return CONFIG;
    }
}
