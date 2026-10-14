package drivers;

import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;

/** B11 test helper: builds Chrome options without starting a browser. */
public final class ChromeFactoryProxyProbe {
    private ChromeFactoryProxyProbe() {
    }

    public static List<String> args() {
        return ChromeFactory.proxyArgs(new ChromeFactory().buildOptions());
    }

    public static List<String> argsOf(ChromeOptions o) {
        return ChromeFactory.proxyArgs(o);
    }
}
