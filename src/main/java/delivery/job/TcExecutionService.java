package delivery.job;

import delivery.codegen.PageClusterer;
import delivery.codegen.ProvenStep;
import delivery.vision.DomPostClickValidator;
import delivery.vision.VisionMissJournal;
import drivers.WebDriverFactory;
import executionLayer.SelectorParser;
import executionLayer.actionExecute;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TcExecutionService {
    private final WebDriverFactory driverFactory;
    private final actionExecute executor;
    private final ConversionJobRequest request;
    /** Monotonic across all execute() batches for one TC (avoids overwriting step-001.png). */
    private final AtomicInteger shotSeq = new AtomicInteger(0);

    private String evidenceFolder;

    public TcExecutionService(WebDriverFactory driverFactory) {
        this(driverFactory, null);
    }

    public TcExecutionService(WebDriverFactory driverFactory, ConversionJobRequest request) {
        this.driverFactory = driverFactory;
        this.executor = new actionExecute(driverFactory);
        this.request = request;
    }

    /** Call at the start of each TC so screenshot names restart at 001. */
    public void beginTc() {
        beginTc(null);
    }

    public void beginTc(String evidenceFolder) {
        shotSeq.set(0);
        this.evidenceFolder = evidenceFolder;
        delivery.assertions.CaptureStore.clear();
    }

    private String evidenceKey(String tcId) {
        return (evidenceFolder == null || evidenceFolder.isBlank()) ? tcId : evidenceFolder;
    }

    String currentEvidenceFolder(String tcId) {
        return evidenceKey(tcId);
    }

    public TcOutcome execute(String tcId, List<ProvenStep> steps, Path evidenceRoot) {
        steps = LoginSecretResolver.resolveForLive(steps, request);
        List<ProvenStep> proven = new ArrayList<>();
        Path evidenceDir = evidenceRoot == null ? null : evidenceRoot.resolve(evidenceKey(tcId));
        try (VisionMissJournal.Scope journal = VisionMissJournal.activate(evidenceDir, request == null ? null : request.password())) {
            if (evidenceDir != null) {
                Files.createDirectories(evidenceDir);
            }
            for (ProvenStep step : steps) {
                if (!step.validated()) {
                    captureFailure(evidenceDir);
                    return new TcOutcome(tcId, TcStatus.TODO, proven,
                            "Locator validation failed: " + step.rationale(), evidenceDir);
                }
                String pageName = resolvePageName(step.pageName());
                boolean hasAssert = step.assertionType() != null && !step.assertionType().isBlank();
                boolean hasAction = step.action() != null && !step.action().isBlank()
                        && !"assert".equalsIgnoreCase(step.action());
                By locator = null;
                if (step.locatorValue() != null && !step.locatorValue().isBlank()) {
                    String strategy = normalizeStrategy(step.locatorStrategy());
                    String selector = strategy + ":" + step.locatorValue();
                    locator = SelectorParser.toBy(selector);
                }

                if (hasAction) {
                    String result;
                    if ("navigate".equalsIgnoreCase(step.action())) {
                        String current = "";
                        try {
                            current = driverFactory.get().getCurrentUrl();
                        } catch (RuntimeException ignored) {
                            current = "";
                        }
                        String url = ExcelPathNavigator.resolve(current, step.value());
                        if (url == null || url.isBlank()) {
                            result = "false -> empty Excel open-path navigate target";
                        } else {
                            result = executor.checkAction("browserAction", "navigate", url, "", null, url);
                        }
                    } else {
                        DomPostClickValidator.Snapshot beforeClick = null;
                        boolean isClick = step.action() != null
                                && step.action().toLowerCase().contains("click");
                        if (isClick && DomPostClickValidator.enabled()) {
                            beforeClick = DomPostClickValidator.capture(driverFactory.get());
                        }
                        result = executor.checkAction(
                            step.actionType(),
                            step.action(),
                            step.value(),
                            "",
                            locator,
                            step.value()
                        );
                        if (isClick && beforeClick != null
                                && (result == null || !result.startsWith("false"))) {
                            DomPostClickValidator.Result post = DomPostClickValidator.validate(
                                    beforeClick,
                                    driverFactory.get(),
                                    step.action(),
                                    step.locatorValue() == null ? step.rationale() : step.locatorValue());
                            VisionMissJournal.recordDomPostClick(
                                    step.action(), step.locatorValue(), post);
                            if (post.failsStrict() && DomPostClickValidator.strict()) {
                                captureFailure(evidenceDir);
                                return new TcOutcome(
                                        tcId,
                                        TcStatus.TODO,
                                        proven,
                                        "DOM post-click check failed: " + post.reason(),
                                        evidenceDir);
                            }
                            if (post.status() == DomPostClickValidator.Status.FAIL
                                    || post.status() == DomPostClickValidator.Status.WEAK) {
                                LogsManager.info("DOM_POST_CLICK: non-strict " + post.status()
                                        + " " + post.reason());
                            }
                        }
                    }
                    if (result != null && result.startsWith("false")) {
                        String retry = retryActionViaContext(step, locator);
                        if (retry != null) {
                            captureFailure(evidenceDir);
                            return new TcOutcome(tcId, TcStatus.TODO, proven, result + " | " + retry, evidenceDir);
                        }
                    } else if (looksLikeDateField(step)) {
                        // Ant Design date pickers stay open after typing and block the next control.
                        dismissTransientOverlays();
                    }
                }
                if (hasAssert) {
                    String assertResult = runAssertion(step);
                    if (assertResult != null) {
                        captureFailure(evidenceDir);
                        return new TcOutcome(tcId, TcStatus.TODO, proven, assertResult, evidenceDir);
                    }
                }
                if (!hasAction && !hasAssert) {
                    captureFailure(evidenceDir);
                    return new TcOutcome(tcId, TcStatus.TODO, proven, "step has no action or assertion", evidenceDir);
                }
                // Capture AFTER success so thumbs show post-click / post-assert page state
                String shot = captureStep(evidenceDir);
                proven.add(new ProvenStep(
                        step.tcId(), pageName, step.actionType(), step.action(),
                        step.locatorStrategy(), step.locatorValue(), step.value(),
                        step.assertionType(), step.assertionExpected(), true, step.rationale(), shot
                ));
            }
            return new TcOutcome(tcId, TcStatus.PASSED, proven, "", evidenceDir);
        } catch (Exception e) {
            captureFailure(evidenceDir);
            return new TcOutcome(tcId, TcStatus.TODO, proven, e.getMessage(), evidenceDir);
        } finally {
            try {
                driverFactory.get().switchTo().defaultContent();
            } catch (Exception ignored) {
            }
        }
    }

    public byte[] capturePngBytes() {
        return captureRedactedPng();
    }

    private byte[] captureRedactedPng() {
        try {
            org.openqa.selenium.WebDriver driver = driverFactory.get();
            if (driver instanceof TakesScreenshot ts) {
                return delivery.privacy.ScreenshotRedactor.redactCapture(
                        driver, ts.getScreenshotAs(OutputType.BYTES));
            }
        } catch (Exception ignored) {
        }
        return new byte[0];
    }

    private static boolean anyDisplayedTextContains(WebDriverFactory driverFactory, By by, String expected) {
        try {
            driverFactory.get().switchTo().defaultContent();
        } catch (Exception ignored) {
        }
        try {
            List<WebElement> list = driverFactory.get().findElements(by);
            for (WebElement el : list) {
                try {
                    if (el == null || !el.isDisplayed()) {
                        continue;
                    }
                    String text = el.getText();
                    if (text != null && text.contains(expected)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        ContextSearch.Hit hit = ContextSearch.find(driverFactory, by);
        if (hit == null) {
            return false;
        }
        try {
            String text = hit.element().getText();
            return text != null && text.contains(expected);
        } catch (Exception e) {
            return false;
        }
    }

    private String retryActionViaContext(ProvenStep step, By locator) {
        if (locator == null) {
            return "no locator for context retry";
        }
        ContextSearch.Hit hit = ContextSearch.find(driverFactory, locator);
        if (hit == null) {
            return "context retry: not found";
        }
        try {
            WebElement el = hit.element();
            String action = step.action() == null ? "" : step.action().toLowerCase();
            switch (action) {
                case "click" -> {
                    DomPostClickValidator.Snapshot before = DomPostClickValidator.enabled()
                            ? DomPostClickValidator.capture(driverFactory.get())
                            : null;
                    el.click();
                    if (before != null) {
                        DomPostClickValidator.Result post = DomPostClickValidator.validate(
                                before, driverFactory.get(), step.action(), step.locatorValue());
                        VisionMissJournal.recordDomPostClick(step.action(), step.locatorValue(), post);
                        if (post.failsStrict() && DomPostClickValidator.strict()) {
                            return "context retry: DOM post-click failed: " + post.reason();
                        }
                    }
                }
                case "clear" -> {
                    el.clear();
                    forceClearElement(el);
                }
                case "type" -> {
                    el.clear();
                    forceClearElement(el);
                    el.sendKeys(step.value() == null ? "" : step.value());
                }
                case "select" -> {
                    String result = new handlingLayer.ElementsHandler(driverFactory.get())
                            .selectOn(el, step.value() == null ? "" : step.value());
                    if (result.startsWith("false")) {
                        return "context retry: " + result;
                    }
                }
                default -> {
                    return "context retry: unsupported action " + action;
                }
            }
            return null;
        } catch (Exception e) {
            return "context retry failed: " + e.getMessage();
        }
    }

    /** Clear plus Ctrl/Cmd+A Delete when the field keeps its value (React-style inputs). */
    static void forceClearElement(WebElement el) {
        if (el == null) {
            return;
        }
        try {
            String remaining = el.getAttribute("value");
            if (remaining == null || remaining.isEmpty()) {
                return;
            }
            CharSequence modifier = System.getProperty("os.name", "").toLowerCase().contains("mac")
                    ? Keys.COMMAND
                    : Keys.CONTROL;
            el.sendKeys(Keys.chord(modifier, "a"), Keys.DELETE);
        } catch (Exception e) {
            LogsManager.warn("Could not force-clear field: " + e.getMessage());
        }
    }

    static boolean looksLikeDateField(ProvenStep step) {
        if (step == null) {
            return false;
        }
        String hay = ((step.locatorValue() == null ? "" : step.locatorValue()) + " "
                + (step.value() == null ? "" : step.value()) + " "
                + (step.rationale() == null ? "" : step.rationale())).toLowerCase();
        if (hay.contains("date") || hay.contains("birth") || hay.contains("expir")) {
            return true;
        }
        String value = step.value() == null ? "" : step.value().trim();
        return value.matches("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}");
    }

    void dismissTransientOverlays() {
        try {
            driverFactory.get().findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            LogsManager.info("DISMISS_OVERLAY: Escape after date field");
        } catch (Exception e) {
            LogsManager.warn("DISMISS_OVERLAY failed: " + e.getMessage());
        }
    }

    private String runAssertion(ProvenStep step) {
        try {
            return switch (step.assertionType()) {
                case "urlContains" -> {
                    String url = driverFactory.get().getCurrentUrl();
                    yield url != null && url.contains(step.assertionExpected())
                            ? null
                            : "false -> url does not contain " + step.assertionExpected();
                }
                case "textContains" -> {
                    String expected = step.assertionExpected() == null ? "" : step.assertionExpected();
                    try {
                        new WebDriverWait(driverFactory.get(), Duration.ofSeconds(15))
                                .until(d -> {
                                    try {
                                        d.switchTo().defaultContent();
                                    } catch (Exception ignored) {
                                    }
                                    try {
                                        String bodyText = d.findElement(By.tagName("body")).getText();
                                        if (bodyText != null && bodyText.contains(expected)) {
                                            return true;
                                        }
                                    } catch (Exception ignored) {
                                    }
                                    if (step.locatorValue() == null || step.locatorValue().isBlank()) {
                                        return false;
                                    }
                                    By by = SelectorParser.toBy(
                                            normalizeStrategy(step.locatorStrategy()) + ":" + step.locatorValue());
                                    return anyDisplayedTextContains(driverFactory, by, expected);
                                });
                        yield null;
                    } catch (TimeoutException e) {
                        yield "false -> text assertion timed out waiting for: " + expected;
                    }
                }
                case "visible" -> {
                    By by = SelectorParser.toBy(normalizeStrategy(step.locatorStrategy()) + ":" + step.locatorValue());
                    try {
                        new WebDriverWait(driverFactory.get(), Duration.ofSeconds(15))
                                .until(d -> {
                                    ContextSearch.Hit hit = ContextSearch.find(driverFactory, by);
                                    return hit != null && hit.element().isDisplayed();
                                });
                        yield null;
                    } catch (TimeoutException e) {
                        yield "false -> element not visible within timeout";
                    }
                }
                case "checked" -> selectedState(step, true);
                case "unchecked" -> selectedState(step, false);
                case "selected" -> selectedState(step, true);
                case "notVisible" -> {
                    if (step.locatorValue() == null || step.locatorValue().isBlank()) {
                        yield "false -> locator required for notVisible";
                    }
                    By by = SelectorParser.toBy(normalizeStrategy(step.locatorStrategy()) + ":" + step.locatorValue());
                    try {
                        new WebDriverWait(driverFactory.get(), Duration.ofSeconds(10))
                                .until(d -> {
                                    ContextSearch.Hit hit = ContextSearch.find(driverFactory, by);
                                    return hit == null || !hit.element().isDisplayed();
                                });
                        yield null;
                    } catch (TimeoutException e) {
                        yield "false -> element still visible: " + step.locatorValue();
                    }
                }
                case "captureText" -> captureText(step);
                case "capturedEquals" -> compareCaptured(step);
                case "signedOut" -> signedOut(step);
                default -> "false -> unsupported assertionType: " + step.assertionType();
            };
        } catch (Exception e) {
            return "false -> assertion error: " + e.getMessage();
        }
    }

    private String selectedState(ProvenStep step, boolean wantSelected) {
        if (step.locatorValue() == null || step.locatorValue().isBlank()) {
            return "false -> locator required for selected-state assert";
        }
        By by = SelectorParser.toBy(normalizeStrategy(step.locatorStrategy()) + ":" + step.locatorValue());
        String expected = step.assertionExpected() == null ? "" : step.assertionExpected().trim();
        try {
            new WebDriverWait(driverFactory.get(), Duration.ofSeconds(10))
                    .until(d -> {
                        ContextSearch.Hit hit = ContextSearch.find(driverFactory, by);
                        return hit != null && hit.element().isDisplayed();
                    });
            ContextSearch.Hit hit = ContextSearch.find(driverFactory, by);
            if (hit == null) {
                return "false -> element not found for selected-state assert";
            }
            WebElement el = hit.element();
            String tag = el.getTagName() == null ? "" : el.getTagName().toLowerCase();
            if (!expected.isBlank() && "select".equals(tag)) {
                org.openqa.selenium.support.ui.Select sel = new org.openqa.selenium.support.ui.Select(el);
                String selectedText = sel.getFirstSelectedOption().getText();
                boolean ok = selectedText != null && selectedText.contains(expected);
                return ok ? null : "false -> selected option was '" + selectedText + "', expected to contain '"
                        + expected + "'";
            }
            return SelectedValueCheck.evaluate(
                    wantSelected,
                    expected,
                    tag,
                    el.getAttribute("role"),
                    el.getAttribute("aria-haspopup"),
                    el.getAttribute("type"),
                    el.isSelected(),
                    el.getText(),
                    el.getAttribute("aria-valuetext"),
                    el.getAttribute("aria-label"));
        } catch (TimeoutException e) {
            return "false -> element not found for selected-state assert";
        }
    }

    private String captureText(ProvenStep step) {
        if (step.locatorValue() == null || step.locatorValue().isBlank()) {
            return "false -> locator required for captureText";
        }
        if (step.value() == null || step.value().isBlank()) {
            return "false -> capture variable name required";
        }
        try {
            By by = SelectorParser.toBy(normalizeStrategy(step.locatorStrategy()) + ":" + step.locatorValue());
            List<String> texts = elementTexts(by);
            var extracted = delivery.assertions.CaptureCompare.extract(texts, step.assertionExpected());
            if (!extracted.ok()) {
                return "false -> " + extracted.error();
            }
            delivery.assertions.CaptureStore.put(step.value(), extracted.value());
            LogsManager.info("captured phrase slot=" + step.value() + " value=" + extracted.value());
            return null;
        } catch (Exception e) {
            return "false -> unavailable browser state: " + e.getClass().getSimpleName();
        }
    }

    private String compareCaptured(ProvenStep step) {
        if (step.locatorValue() == null || step.locatorValue().isBlank()) {
            return "false -> locator required for capturedEquals";
        }
        try {
            By by = SelectorParser.toBy(normalizeStrategy(step.locatorStrategy()) + ":" + step.locatorValue());
            List<String> texts = elementTexts(by);
            var compared = delivery.assertions.CaptureCompare.compareExact(
                    texts, delivery.assertions.CaptureStore.get(step.value()));
            if (!compared.ok()) {
                return "false -> " + compared.error();
            }
            LogsManager.info("compared captured slot=" + step.value()
                    + " value=" + delivery.assertions.CaptureStore.get(step.value()));
            return null;
        } catch (Exception e) {
            return "false -> unavailable browser state: " + e.getClass().getSimpleName();
        }
    }

    private String signedOut(ProvenStep step) {
        if (step.locatorValue() == null || step.locatorValue().isBlank()) {
            return "false -> locator required for signedOut";
        }
        try {
            By by = SelectorParser.toBy(normalizeStrategy(step.locatorStrategy()) + ":" + step.locatorValue());
            List<WebElement> els = driverFactory.get().findElements(by);
            boolean anyDisplayed = false;
            List<String> displayed = new ArrayList<>();
            for (WebElement el : els) {
                try {
                    if (el.isDisplayed()) {
                        anyDisplayed = true;
                        displayed.add(el.getText());
                    }
                } catch (Exception stale) {
                    return "false -> unavailable browser state: " + stale.getClass().getSimpleName();
                }
            }
            var result = delivery.assertions.CaptureCompare.signedOut(displayed, anyDisplayed, step.assertionExpected());
            if (!result.ok()) {
                return "false -> " + result.error();
            }
            LogsManager.info("signed-out locator=" + step.locatorValue() + " expected=" + step.assertionExpected());
            return null;
        } catch (Exception e) {
            return "false -> unavailable browser state: " + e.getClass().getSimpleName();
        }
    }

    private List<String> elementTexts(By by) {
        List<String> texts = new ArrayList<>();
        for (WebElement el : driverFactory.get().findElements(by)) {
            texts.add(el.getText());
        }
        return texts;
    }

    private String resolvePageName(String existing) {
        // Never freeze "Login" as its own POM cluster — always prefer live URL stem
        if (existing != null && !existing.isBlank()
                && !"Page".equals(existing)
                && !"Login".equalsIgnoreCase(existing)
                && !"TargetLogin".equals(existing)) {
            return existing;
        }
        try {
            String url = driverFactory.get().getCurrentUrl();
            return PageClusterer.pageNameFromUrl(url);
        } catch (Exception e) {
            return existing == null || existing.isBlank() || "Login".equalsIgnoreCase(existing)
                    || "TargetLogin".equals(existing) ? "Page" : existing;
        }
    }

    private static String normalizeStrategy(String strategy) {
        if (strategy == null) {
            return "cssSelector";
        }
        return switch (strategy.trim().toLowerCase()) {
            case "css", "cssselector" -> "cssSelector";
            case "data-test", "data-testid", "data-qa", "testid" -> strategy.trim().toLowerCase();
            case "classname" -> "className";
            case "linktext" -> "linkText";
            case "partiallinktext" -> "partialLinkText";
            case "tagname" -> "tagName";
            case "id" -> "id";
            case "name" -> "name";
            case "xpath" -> "xpath";
            default -> strategy.trim();
        };
    }

    private String captureStep(Path evidenceDir) {
        if (evidenceDir == null) {
            return "";
        }
        try {
            Files.createDirectories(evidenceDir);
            byte[] png = captureRedactedPng();
            if (png.length > 0) {
                int n = shotSeq.incrementAndGet();
                String name = String.format("step-%03d.png", n);
                Files.write(evidenceDir.resolve(name), png);
                return name;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Capture {@code failure.png} under {@code evidenceRoot/tcId} (e.g. login prelude bind fail
     * before any proven step). Returns the evidence directory path string, or empty on skip.
     */
    public String captureFailureEvidence(String tcId, Path evidenceRoot) {
        if (tcId == null || tcId.isBlank() || evidenceRoot == null) {
            return "";
        }
        Path evidenceDir = evidenceRoot.resolve(evidenceKey(tcId));
        captureFailure(evidenceDir);
        return Files.isRegularFile(evidenceDir.resolve("failure.png"))
                ? evidenceDir.toString()
                : "";
    }

    private void captureFailure(Path evidenceDir) {
        if (evidenceDir == null) {
            return;
        }
        try {
            Files.createDirectories(evidenceDir);
            byte[] png = captureRedactedPng();
            if (png.length > 0) {
                Files.write(evidenceDir.resolve("failure.png"), png);
            }
        } catch (Exception ignored) {
        }
    }
}
