package delivery.portal.record;

import delivery.runner.EngineProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.CapabilityType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Headed Chrome on this computer. The window is the user's, not a hidden browser. */
final class ChromeRecordingBrowser implements RecordingBrowser {
    static final String CHROME_MESSAGE = "Chrome did not open. Install Google Chrome on this computer, then try again.";
    static final String SITE_MESSAGE = "The site did not open. Check the address and try again.";

    private static final Logger log = LogManager.getLogger(ChromeRecordingBrowser.class);

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "keel-record");
        thread.setDaemon(true);
        return thread;
    });
    private ChromeDriver driver;
    private Path pacFile;
    private volatile boolean alive = true;
    private volatile String lastUrl = "";

    static ChromeRecordingBrowser launch(String approvedOrigin, String url) {
        ChromeRecordingBrowser browser = new ChromeRecordingBrowser();
        try {
            browser.start(approvedOrigin, url);
            return browser;
        } catch (RuntimeException e) {
            browser.close();
            throw e;
        }
    }

    private void start(String approvedOrigin, String url) {
        call(() -> {
            pacFile = delivery.net.WorkerPac.installForJob(approvedOrigin);
            ChromeOptions options = buildOptions();
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(40));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(8));
            installHook(driver);
            try {
                driver.manage().window().maximize();
            } catch (RuntimeException ignored) {
                // --start-maximized already requested
            }
            return null;
        }, 60, CHROME_MESSAGE);
        call(() -> {
            driver.get(url);
            installHook(driver);
            String current = driver.getCurrentUrl();
            if (current != null && !current.isBlank()) {
                lastUrl = current;
            }
            return null;
        }, 50, SITE_MESSAGE);
    }

    @Override
    public String location() {
        if (!alive) {
            return "";
        }
        try {
            return call(() -> {
                if (driver == null) {
                    return "";
                }
                String current = driver.getCurrentUrl();
                if (current != null && !current.isBlank()) {
                    lastUrl = current;
                }
                return lastUrl == null ? "" : lastUrl;
            }, 8, SITE_MESSAGE);
        } catch (RuntimeException e) {
            if (deadSession(e)) {
                alive = false;
            }
            return lastUrl == null ? "" : lastUrl;
        }
    }

    @Override
    public String readEvents() {
        if (!alive) {
            return "";
        }
        try {
            return call(() -> {
                if (!(driver instanceof org.openqa.selenium.JavascriptExecutor js)) {
                    return "";
                }
                Object raw = js.executeScript(RecordingScript.READ);
                return raw == null ? "" : String.valueOf(raw);
            }, 8, SITE_MESSAGE);
        } catch (RuntimeException e) {
            if (deadSession(e)) {
                alive = false;
            }
            return "";
        }
    }

    @Override
    public void ensureInstalled() {
        if (!alive) {
            return;
        }
        try {
            call(() -> {
                if (driver != null) {
                    installHook(driver);
                }
                return null;
            }, 8, SITE_MESSAGE);
        } catch (RuntimeException e) {
            if (deadSession(e)) {
                alive = false;
            }
        }
    }

    @Override
    public boolean alive() {
        return alive && driver != null;
    }

    @Override
    public void close() {
        try {
            exec.submit(() -> {
                try {
                    if (driver != null) {
                        driver.quit();
                    }
                } catch (RuntimeException e) {
                    log.warn("Recording browser quit failed: {}", e.toString());
                } finally {
                    driver = null;
                    alive = false;
                    if (pacFile != null) {
                        try {
                            Files.deleteIfExists(pacFile);
                        } catch (Exception ignored) {
                            // temp pac is safe to leave
                        }
                    }
                }
            }).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            alive = false;
            log.warn("Recording browser close failed: {}", e.toString());
        } finally {
            exec.shutdownNow();
        }
    }

    private static void installHook(WebDriver webDriver) {
        if (webDriver instanceof HasCdp cdp) {
            try {
                cdp.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                        Map.of("source", RecordingScript.install()));
            } catch (RuntimeException ignored) {
                // the direct inject below still covers the current page
            }
        }
        if (webDriver instanceof org.openqa.selenium.JavascriptExecutor js) {
            js.executeScript(RecordingScript.install());
        }
    }

    private ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-features=Translate");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--disable-search-engine-choice-screen");
        options.addArguments("--start-maximized");
        options.setExperimentalOption("prefs", Map.of(
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false,
                "autofill.profile_enabled", false,
                "autofill.credit_card_enabled", false));
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        options.setCapability(CapabilityType.UNHANDLED_PROMPT_BEHAVIOUR, UnexpectedAlertBehaviour.IGNORE);
        options.setAcceptInsecureCerts(true);
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        delivery.net.WorkerPac.apply(options);
        EngineProxy.fromSystem().chromeArgs(currentArgs(options)).forEach(options::addArguments);
        return options;
    }

    @SuppressWarnings("unchecked")
    private static List<String> currentArgs(ChromeOptions options) {
        Object args = ((Map<String, Object>) options.asMap().getOrDefault(ChromeOptions.CAPABILITY, Map.of()))
                .getOrDefault("args", List.of());
        return args instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private <T> T call(Callable<T> work, int seconds, String failure) {
        try {
            return exec.submit(work).get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException(failure);
        } catch (RejectedExecutionException e) {
            alive = false;
            throw new IllegalStateException(failure);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Recording browser call failed: {}", cause.toString());
            if (deadSession(cause)) {
                alive = false;
            }
            if (cause instanceof IllegalStateException illegal) {
                throw illegal;
            }
            if (cause instanceof TimeoutException || cause instanceof org.openqa.selenium.TimeoutException) {
                throw new IllegalStateException(failure);
            }
            if (chromeMissing(cause)) {
                throw new IllegalStateException(CHROME_MESSAGE);
            }
            throw new IllegalStateException(failure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Recording was interrupted.");
        }
    }

    private static boolean deadSession(Throwable cause) {
        String message = cause.toString().toLowerCase(Locale.ROOT);
        return message.contains("invalid session")
                || message.contains("no such window")
                || message.contains("disconnected")
                || message.contains("chrome not reachable")
                || message.contains("rejectedexecution");
    }

    private static boolean chromeMissing(Throwable cause) {
        if (cause instanceof SessionNotCreatedException) {
            return true;
        }
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("cannot find chrome")
                || message.contains("chrome binary")
                || message.contains("session not created");
    }
}
