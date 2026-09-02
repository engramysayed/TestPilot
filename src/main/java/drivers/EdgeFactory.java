package drivers;

import utils.LogsManager;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Map;

public class EdgeFactory extends AbstractDriver {

    EdgeOptions buildOptions() {
        EdgeOptions options = new EdgeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");
        // Same Chromium save-password / autofill bubbles as ChromeFactory.
        options.setExperimentalOption("prefs", Map.of(
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false,
                "autofill.profile_enabled", false,
                "autofill.credit_card_enabled", false));
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        options.setCapability(CapabilityType.UNHANDLED_PROMPT_BEHAVIOUR, UnexpectedAlertBehaviour.IGNORE);
        options.setCapability(CapabilityType.ENABLE_DOWNLOADS, true);
        options.setAcceptInsecureCerts(true);
        if (BrowserExecution.isHeadless()) {
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
        } else {
            options.addArguments("--start-maximized");
        }
        if ("Remote".equalsIgnoreCase(BrowserExecution.executionType())) {
            options.addArguments("--disable-extensions");
        }
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        return options;
    }

    @Override
    public WebDriver createDriver() {
        if (BrowserExecution.isLocalDriver()) {
            LogsManager.info("Edge driver headless=" + BrowserExecution.isHeadless());
            return new EdgeDriver(buildOptions());
        }
        throw new RuntimeException("TODO: 需要人工确认异常处理逻辑 — Remote Edge WebDriver is not configured");
    }
}
