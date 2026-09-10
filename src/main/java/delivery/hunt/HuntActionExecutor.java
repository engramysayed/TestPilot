package delivery.hunt;

import org.json.JSONArray;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Executes allowlisted Bug Hunter planner actions against a live WebDriver. */
public final class HuntActionExecutor {
    /** Default wait when AI sends wait with no/blank ms. */
    public static final int DEFAULT_WAIT_MS = 5000;
    /** Hard ceiling for a single wait action. */
    public static final int WAIT_CAP_MS = 15_000;
    /** Max characters for an execute_js script body. */
    public static final int EXECUTE_JS_MAX_CHARS = 4000;
    /** Max characters of JS return value stored in the action log. */
    public static final int EXECUTE_JS_RESULT_MAX_CHARS = 500;

    private final WebDriver driver;
    private HuntActionGuard guard;

    public HuntActionExecutor(WebDriver driver) {
        this.driver = driver;
    }

    public void setGuard(HuntActionGuard guard) {
        this.guard = guard;
    }

    public List<Map<String, Object>> executeAll(List<Map<String, Object>> actions) {
        return executeAll(actions, Integer.MAX_VALUE);
    }

    public List<Map<String, Object>> executeAll(List<Map<String, Object>> actions, int actionCap) {
        List<Map<String, Object>> log = new ArrayList<>();
        if (actions == null || actions.isEmpty()) {
            return log;
        }
        int cap = Math.max(1, actionCap);
        int limit = Math.min(actions.size(), cap);
        for (int i = 0; i < limit; i++) {
            log.add(executeOne(actions.get(i)));
        }
        if (actions.size() > limit) {
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("type", "cap");
            skipped.put("status", "rejected");
            skipped.put("reason", "actionCapPerCycle=" + cap + "; skipped "
                    + (actions.size() - limit) + " extra action(s)");
            log.add(skipped);
        }
        return log;
    }

    public Map<String, Object> executeOne(Map<String, Object> action) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (action == null) {
            row.put("status", "rejected");
            row.put("reason", "null action");
            return row;
        }
        String type = str(action.get("type")).isBlank() ? str(action.get("action")) : str(action.get("type"));
        Map<String, Object> normalized = HuntActionNormalizer.normalize(action);
        type = str(normalized.get("type"));
        row.putAll(normalized);
        row.put("type", type);
        if (guard != null) {
            Optional<String> why = guard.rejectReason(normalized);
            if (why.isPresent()) {
                row.put("status", "rejected");
                row.put("reason", "ungrounded_locator: " + why.get());
                return row;
            }
        }
        try {
            switch (type) {
                case "navigate" -> {
                    String url = str(normalized.get("url"));
                    if (url.isBlank()) {
                        row.put("status", "rejected");
                        row.put("reason", "navigate requires url");
                        return row;
                    }
                    driver.get(url);
                    row.put("status", "ok");
                }
                case "back" -> {
                    driver.navigate().back();
                    row.put("status", "ok");
                }
                case "forward" -> {
                    driver.navigate().forward();
                    row.put("status", "ok");
                }
                case "refresh" -> {
                    driver.navigate().refresh();
                    row.put("status", "ok");
                }
                case "execute_js" -> {
                    String script = jsScript(normalized);
                    if (script.isBlank()) {
                        row.put("status", "rejected");
                        row.put("reason", "execute_js requires script|code|js");
                        return row;
                    }
                    if (script.length() > EXECUTE_JS_MAX_CHARS) {
                        row.put("status", "rejected");
                        row.put("reason", "execute_js script exceeds " + EXECUTE_JS_MAX_CHARS + " chars");
                        return row;
                    }
                    if (!(driver instanceof JavascriptExecutor js)) {
                        row.put("status", "fail");
                        row.put("reason", "driver does not support JavascriptExecutor");
                        return row;
                    }
                    Object result = js.executeScript(script);
                    row.put("script", script);
                    row.put("result", truncate(result == null ? "null" : String.valueOf(result),
                            EXECUTE_JS_RESULT_MAX_CHARS));
                    row.put("status", "ok");
                }
                case "click" -> {
                    find(normalized).click();
                    row.put("status", "ok");
                }
                case "type" -> {
                    WebElement el = find(normalized);
                    el.clear();
                    el.sendKeys(str(normalized.get("value")));
                    row.put("status", "ok");
                }
                case "clear" -> {
                    find(normalized).clear();
                    row.put("status", "ok");
                }
                case "wait" -> {
                    int ms = parseWaitMs(normalized.get("ms"));
                    Thread.sleep(ms);
                    row.put("ms", ms);
                    row.put("status", "ok");
                }
                case "assert_visible" -> {
                    WebElement el = find(normalized);
                    boolean ok = el.isDisplayed();
                    row.put("status", ok ? "ok" : "fail");
                    if (!ok) {
                        row.put("reason", "element not visible");
                    }
                }
                case "assert_text" -> {
                    String expected = str(normalized.get("text"));
                    if (expected.isBlank()) {
                        expected = str(normalized.get("value"));
                    }
                    String body = driver.findElement(By.tagName("body")).getText();
                    boolean ok = body != null && body.contains(expected);
                    row.put("expected", expected);
                    row.put("status", ok ? "ok" : "fail");
                    if (!ok) {
                        row.put("reason", "text not found on page");
                    }
                }
                case "", "finish" -> {
                    row.put("status", "rejected");
                    row.put("reason", "empty or non-action type");
                }
                default -> {
                    row.put("status", "rejected");
                    row.put("reason", "action not in allowlist: " + type);
                }
            }
        } catch (Exception e) {
            row.put("status", "fail");
            row.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return row;
    }

    /** Failed assert_* rows become bug drafts when planner did not emit bugs. */
    public static List<Map<String, Object>> bugsFromFailedAsserts(List<Map<String, Object>> actionLog) {
        List<Map<String, Object>> bugs = new ArrayList<>();
        if (actionLog == null) {
            return bugs;
        }
        for (Map<String, Object> row : actionLog) {
            String type = str(row.get("type"));
            if (!"fail".equals(str(row.get("status")))) {
                continue;
            }
            if (!type.startsWith("assert_")) {
                continue;
            }
            Map<String, Object> bug = new LinkedHashMap<>();
            bug.put("title", "Assert failed: " + type);
            bug.put("severity", "major");
            bug.put("repro", "Planner action " + type + " failed during hunt");
            bug.put("expected", str(row.get("expected")).isBlank() ? "Assertion to pass" : str(row.get("expected")));
            bug.put("actual", str(row.get("reason")));
            bugs.add(bug);
        }
        return bugs;
    }

    public static void writeActionsLog(Path cycleDir, List<Map<String, Object>> log) throws Exception {
        Files.writeString(cycleDir.resolve("actions-log.json"),
                new JSONArray(log == null ? List.of() : log).toString(2), StandardCharsets.UTF_8);
    }

    public byte[] screenshotPng() {
        if (!(driver instanceof TakesScreenshot ts)) {
            return new byte[0];
        }
        return ts.getScreenshotAs(OutputType.BYTES);
    }

    private WebElement find(Map<String, Object> action) {
        String locator = str(action.get("locator"));
        if (locator.isBlank()) {
            locator = str(action.get("locatorValue"));
        }
        String strategy = str(action.get("locatorStrategy")).toLowerCase(Locale.ROOT);
        if (strategy.isBlank()) {
            strategy = guessStrategy(locator);
        }
        return driver.findElement(by(strategy, locator));
    }

    private static By by(String strategy, String value) {
        return switch (strategy) {
            case "id" -> By.id(value);
            case "name" -> By.name(value);
            case "xpath" -> By.xpath(value);
            case "linktext", "link_text" -> By.linkText(value);
            case "css", "cssselector", "css_selector", "" -> By.cssSelector(value);
            default -> By.cssSelector(value);
        };
    }

    private static String guessStrategy(String locator) {
        if (locator.startsWith("//") || locator.startsWith("(//")) {
            return "xpath";
        }
        if (locator.startsWith("#") || locator.contains("[") || locator.contains(".")) {
            return "css";
        }
        return "css";
    }

    private static int parseWaitMs(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank() || "null".equalsIgnoreCase(String.valueOf(raw))) {
            return DEFAULT_WAIT_MS;
        }
        int ms = DEFAULT_WAIT_MS;
        try {
            ms = Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return DEFAULT_WAIT_MS;
        }
        if (ms < 0) {
            ms = 0;
        }
        return Math.min(ms, WAIT_CAP_MS);
    }

    static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "navigate_back", "history_back", "go_back" -> "back";
            case "navigate_forward", "history_forward", "go_forward" -> "forward";
            case "reload", "reload_page", "page_refresh" -> "refresh";
            case "js", "javascript", "eval_js", "run_js" -> "execute_js";
            case "fill", "input", "enter", "send_keys", "sendkeys", "set_value", "setvalue" -> "type";
            case "press", "tap" -> "click";
            default -> type.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static String jsScript(Map<String, Object> action) {
        String script = str(action.get("script"));
        if (script.isBlank()) {
            script = str(action.get("code"));
        }
        if (script.isBlank()) {
            script = str(action.get("js"));
        }
        return script;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
