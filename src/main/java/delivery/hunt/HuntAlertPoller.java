package delivery.hunt;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Short poll for toast / alert nodes after auth failures. */
public final class HuntAlertPoller {
    public static final long DEFAULT_TIMEOUT_MS = 2000L;
    public static final long POLL_MS = 200L;
    private static final By ALERTS = By.cssSelector(
            "[role='alert'], .ant-message, .ant-notification, .ant-message-notice, "
                    + "[class*='toast'], [class*='Toast'], .MuiAlert-root");

    private HuntAlertPoller() {
    }

    public static List<String> poll(WebDriver driver, long timeoutMs) {
        if (driver == null) {
            return List.of();
        }
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        Set<String> seen = new LinkedHashSet<>();
        do {
            try {
                for (WebElement el : driver.findElements(ALERTS)) {
                    try {
                        if (!el.isDisplayed()) {
                            continue;
                        }
                        String t = el.getText();
                        if (t != null && !t.isBlank()) {
                            seen.add(t.trim());
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            if (!seen.isEmpty() || System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (System.currentTimeMillis() < deadline);
        return new ArrayList<>(seen);
    }

    public static boolean networkSuggestsAuthFailure(List<java.util.Map<String, Object>> netFails) {
        if (netFails == null || netFails.isEmpty()) {
            return false;
        }
        for (java.util.Map<String, Object> f : netFails) {
            int status = toInt(f.get("status"));
            if (status == 401 || status == 403) {
                return true;
            }
            String url = String.valueOf(f.get("url")).toLowerCase();
            if (status >= 400 && (url.contains("login") || url.contains("auth") || url.contains("signin"))) {
                return true;
            }
        }
        return false;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
