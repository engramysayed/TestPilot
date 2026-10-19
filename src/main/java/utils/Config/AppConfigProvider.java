package utils.Config;

import org.aeonbits.owner.ConfigFactory;

public final class AppConfigProvider {
    private AppConfigProvider() {
    }

    /** Fresh read of system properties so Admin Save applies to the next driver. */
    public static AppConfig get() {
        return ConfigFactory.create(AppConfig.class);
    }
}
