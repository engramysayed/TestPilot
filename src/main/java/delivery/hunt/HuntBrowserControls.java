package delivery.hunt;

import org.openqa.selenium.WebDriver;

import java.util.Map;

/** Browser session surface used by {@link HuntActionExecutor} (restart + live driver). */
public interface HuntBrowserControls {
    WebDriver driver();

    /**
     * Quit + recreate browser, reopen base URL, optionally soft-login.
     * Returns a result map with at least {@code ok} (boolean); on failure includes {@code reason}.
     */
    Map<String, Object> restart(boolean login);
}
