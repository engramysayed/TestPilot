package project.utils.locators;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import project.utils.Logs.LogsManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Installs keel/closed-shadow-hook.js (verbatim resource) via CDP before the first navigation. */
public final class KeelHook {
    public static final String RESOURCE = "/keel/closed-shadow-hook.js";

    private KeelHook() {
    }

    public static String source() {
        try (InputStream in = KeelHook.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    /**
     * Chrome/Edge only (must be called on the raw driver, before ThreadGuard wrapping and before any
     * {@code get}). Returns false when the driver has no CDP; shadow-closed targets will then fail.
     */
    public static boolean install(WebDriver driver) {
        if (!(driver instanceof HasCdp cdp)) {
            LogsManager.warn("keel: driver has no CDP; closed shadow roots unavailable");
            return false;
        }
        try {
            cdp.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Map.of("source", source()));
            return true;
        } catch (RuntimeException e) {
            LogsManager.warn("keel: closed-shadow hook install failed: " + e.getMessage());
            return false;
        }
    }
}
