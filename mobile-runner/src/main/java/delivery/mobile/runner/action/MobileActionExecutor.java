package delivery.mobile.runner.action;

import delivery.mobile.runner.bind.ActionKind;
import delivery.mobile.runner.bind.BindingFingerprint;
import delivery.mobile.runner.bind.NodeEligibility;
import delivery.mobile.runner.settle.ActionOutcome;
import delivery.mobile.runner.settle.MobileSettleService;
import delivery.mobile.runner.settle.ScreenshotProbe;
import delivery.mobile.runner.settle.SettleActionKind;
import delivery.mobile.runner.settle.SettleResult;
import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Tap/type/clear (M2-04) plus long-press/swipe/scroll/back/keyboard/launch/restart (M3-04).
 * No infinite scroll/heal loop; restart never implies clear-data.
 */
public final class MobileActionExecutor {
    private final MobileDriverPort driver;
    private final MobileSettleService settle;
    private final LongSupplier clockMs;

    public MobileActionExecutor(MobileDriverPort driver, MobileSettleService settle) {
        this(driver, settle, System::currentTimeMillis);
    }

    public MobileActionExecutor(MobileDriverPort driver, MobileSettleService settle, LongSupplier clockMs) {
        this.driver = driver;
        this.settle = settle;
        this.clockMs = clockMs == null ? System::currentTimeMillis : clockMs;
    }

    public ActionResult tap(int nodeIndex, boolean retryIfNoChange, boolean mutatingUncertain,
                            boolean businessAssertOk) {
        RetryIfNoChangePolicy.requireLegal(retryIfNoChange, SettleActionKind.TAP, false);
        return runGesture(SettleActionKind.TAP, nodeIndex, retryIfNoChange, mutatingUncertain, businessAssertOk);
    }

    public ActionResult longPress(int nodeIndex, boolean retryIfNoChange, boolean mutatingUncertain,
                                  boolean businessAssertOk) {
        RetryIfNoChangePolicy.requireLegal(retryIfNoChange, SettleActionKind.LONG_PRESS, false);
        try {
            MobileSnapshot pre = driver.pageSource();
            MobileSnapshotNode n = pre.node(nodeIndex);
            if (!NodeEligibility.actionEligible(n, ActionKind.LONG_PRESS)) {
                return new ActionResult(ActionResult.Status.HIDDEN_OR_DISABLED, ActionOutcome.NO_CHANGE,
                        null, false, "hidden/disabled", false);
            }
            byte[] shot = safeShot();
            driver.longPress(nodeIndex);
            SettleResult sr = settle.settle(SettleActionKind.LONG_PRESS, pre, driver::pageSource, screenshotProbe(), shot);
            ActionResult first = ActionResult.fromSettle(SettleActionKind.LONG_PRESS, sr, businessAssertOk);
            var decision = RetryIfNoChangePolicy.evaluate(retryIfNoChange, SettleActionKind.LONG_PRESS,
                    mutatingUncertain, sr.outcome(), false);
            if (decision == RetryIfNoChangePolicy.Decision.REJECT) {
                throw new IllegalArgumentException("retryIfNoChange rejected");
            }
            if (decision == RetryIfNoChangePolicy.Decision.ALLOW_ONCE) {
                driver.longPress(nodeIndex);
                SettleResult sr2 = settle.settle(SettleActionKind.LONG_PRESS, driver.pageSource(), driver::pageSource,
                        screenshotProbe(), shot);
                return ActionResult.fromSettle(SettleActionKind.LONG_PRESS, sr2, businessAssertOk).withRetried(true);
            }
            return first;
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    public ActionResult type(int nodeIndex, String value, boolean secret, boolean businessAssertOk) {
        try {
            MobileSnapshot pre = driver.pageSource();
            MobileSnapshotNode n = pre.node(nodeIndex);
            if (!NodeEligibility.actionEligible(n, ActionKind.TYPE)) {
                return new ActionResult(ActionResult.Status.HIDDEN_OR_DISABLED, ActionOutcome.NO_CHANGE,
                        null, false, "hidden/disabled", false);
            }
            if (secret || n.password()) {
                driver.typeUnicode(nodeIndex, value);
                SettleResult sr = settle.settle(SettleActionKind.TYPE_SECRET, pre, driver::pageSource, null, null);
                return ActionResult.fromSettle(SettleActionKind.TYPE_SECRET, sr, businessAssertOk);
            }
            TypeInputPolicy.Path path = TypeInputPolicy.pathFor(value);
            if (path == TypeInputPolicy.Path.UNICODE_ELEMENT) {
                if (TypeInputPolicy.adbInputTextAllowed(value)) {
                    throw new IllegalStateException("adb input text must never be used for non-ASCII");
                }
                driver.typeUnicode(nodeIndex, value);
            } else {
                driver.typeAscii(nodeIndex, value);
            }
            SettleResult sr = settle.settle(SettleActionKind.TYPE, pre, driver::pageSource,
                    screenshotProbe(), driver.screenshot());
            String observed = driver.readText(nodeIndex);
            if (!TypeInputPolicy.readBackMatches(value, observed, false)) {
                return new ActionResult(ActionResult.Status.TYPE_MISMATCH, sr.outcome(), sr, false,
                        "TYPE_MISMATCH expected NFC read-back", false);
            }
            return ActionResult.fromSettle(SettleActionKind.TYPE, sr, businessAssertOk);
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    public ActionResult clear(int nodeIndex, boolean businessAssertOk) {
        try {
            MobileSnapshot pre = driver.pageSource();
            MobileSnapshotNode n = pre.node(nodeIndex);
            if (!NodeEligibility.actionEligible(n, ActionKind.CLEAR)) {
                return new ActionResult(ActionResult.Status.HIDDEN_OR_DISABLED, ActionOutcome.NO_CHANGE,
                        null, false, "hidden/disabled", false);
            }
            driver.clear(nodeIndex);
            SettleResult sr = settle.settle(SettleActionKind.CLEAR, pre, driver::pageSource,
                    screenshotProbe(), driver.screenshot());
            return ActionResult.fromSettle(SettleActionKind.CLEAR, sr, businessAssertOk);
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    public ActionResult pressBack(boolean businessAssertOk) {
        try {
            if (driver.isKeyboardShown()) {
                // Prefer hide-keyboard path; raw BACK while IME shown is still allowed as navigation
                // only when caller explicitly wants BACK — but obstructing keyboard for other actions
                // is handled separately.
            }
            MobileSnapshot pre = driver.pageSource();
            byte[] shot = safeShot();
            driver.pressBack();
            SettleResult sr = settle.settle(SettleActionKind.BACK, pre, driver::pageSource, screenshotProbe(), shot);
            return ActionResult.fromSettle(SettleActionKind.BACK, sr, businessAssertOk);
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    public ActionResult hideKeyboard() {
        try {
            MobileSnapshot pre = driver.pageSource();
            KeyboardDismisser.Result kr = KeyboardDismisser.dismiss(driver, clockMs);
            if (!kr.dismissed()) {
                return new ActionResult(ActionResult.Status.KEYBOARD_NOT_DISMISSED, ActionOutcome.NO_CHANGE,
                        null, false, kr.detail(), false);
            }
            SettleResult sr = settle.settle(SettleActionKind.HIDE_KEYBOARD, pre, driver::pageSource,
                    screenshotProbe(), safeShot());
            return ActionResult.fromSettle(SettleActionKind.HIDE_KEYBOARD, sr, true)
                    .withDetail(kr.detail());
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    /**
     * Bounded swipe using normalized geometry.
     * @param expectedOrientation if non-null and window orientation differs → GEOMETRY_CHANGED
     */
    public ActionResult swipe(NormalizedSwipe swipe, String expectedOrientation, boolean businessAssertOk) {
        try {
            if (expectedOrientation != null && !expectedOrientation.isBlank()
                    && !expectedOrientation.equalsIgnoreCase(driver.orientation())) {
                return new ActionResult(ActionResult.Status.GEOMETRY_CHANGED, ActionOutcome.NO_CHANGE,
                        null, false, "orientation changed before swipe: " + driver.orientation(), false);
            }
            // resolve against current window — proves bounded geometry path
            swipe.toDevicePx(driver.windowWidth(), driver.windowHeight());
            MobileSnapshot pre = driver.pageSource();
            driver.swipeNormalized(swipe);
            SettleResult sr = settle.settle(SettleActionKind.SWIPE, pre, driver::pageSource,
                    screenshotProbe(), safeShot());
            return ActionResult.fromSettle(SettleActionKind.SWIPE, sr, businessAssertOk);
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    /**
     * SCROLL_UNTIL with max-swipes / no-progress caps and visibilityPercentage gate.
     * {@code targetBounds} supplier returns current target bounds (null if absent).
     * {@code viewport} is scroll container or window bounds.
     */
    public ActionResult scrollUntil(
            ScrollUntilRequest req,
            Supplier<MobileBounds> targetBounds,
            MobileBounds viewport,
            boolean businessAssertOk) {
        long start = clockMs.getAsLong();
        int swipes = 0;
        int noProgress = 0;
        String lastFp = null;
        try {
            while (true) {
                if (driver.isActionCancelled()) {
                    return new ActionResult(ActionResult.Status.CANCELLED, ActionOutcome.NO_CHANGE,
                            null, false, "scroll cancelled", false);
                }
                if (clockMs.getAsLong() - start > req.maxDurationMs()) {
                    return scrollLimit(req, "maxDuration");
                }
                MobileBounds target = targetBounds == null ? null : targetBounds.get();
                int vis = VisibilityPercent.of(target, viewport);
                if (target != null && vis >= req.visibilityPercentage()) {
                    if (req.centerElement()) {
                        // one extra bounded centering swipe (still counts toward max)
                        if (swipes >= req.maxSwipes()) {
                            return scrollLimit(req, "centerElement needs extra swipe but maxSwipes reached");
                        }
                        MobileSnapshot pre = driver.pageSource();
                        driver.swipeNormalized(NormalizedSwipe.forDirection(req.direction(), 0.15));
                        swipes++;
                        settle.settle(SettleActionKind.SCROLL_UNTIL, pre, driver::pageSource,
                                screenshotProbe(), safeShot());
                    }
                    return new ActionResult(ActionResult.Status.OK, ActionOutcome.CHANGED, null, businessAssertOk,
                            "target visible pct=" + vis, false);
                }
                if (swipes >= req.maxSwipes()) {
                    return scrollLimit(req, "maxSwipes; lastVisibility=" + vis);
                }
                if (driver.isKeyboardShown()) {
                    ActionResult hide = hideKeyboard();
                    if (hide.status() == ActionResult.Status.KEYBOARD_NOT_DISMISSED) {
                        return new ActionResult(ActionResult.Status.KEYBOARD_NOT_DISMISSED, ActionOutcome.NO_CHANGE,
                                null, false, "obstructing keyboard: " + hide.detail(), false);
                    }
                }
                MobileSnapshot pre = driver.pageSource();
                String fp = BindingFingerprint.of(pre);
                if (lastFp != null && lastFp.equals(fp)) {
                    noProgress++;
                    if (noProgress >= req.noProgressLimit()) {
                        return scrollLimit(req, "noProgress");
                    }
                } else {
                    noProgress = 0;
                }
                lastFp = fp;
                driver.swipeNormalized(NormalizedSwipe.forDirection(req.direction(), 0.4));
                swipes++;
                settle.settle(SettleActionKind.SCROLL_UNTIL, pre, driver::pageSource,
                        screenshotProbe(), safeShot());
            }
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    public ActionResult launch(String packageName, String activity, boolean businessAssertOk) {
        try {
            MobileSnapshot pre = driver.pageSource();
            driver.launchApp(packageName, activity);
            SettleResult sr = settle.settle(SettleActionKind.LAUNCH, pre, driver::pageSource,
                    screenshotProbe(), safeShot());
            return ActionResult.fromSettle(SettleActionKind.LAUNCH, sr, businessAssertOk);
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    /**
     * Restart without data wipe. Passing {@code clearData=true} is refused here — use explicit
     * M3-05 data-clear API instead.
     */
    public ActionResult restart(String packageName, boolean businessAssertOk) {
        try {
            MobileSnapshot pre = driver.pageSource();
            driver.restartApp(packageName, false);
            SettleResult sr = settle.settle(SettleActionKind.RESTART, pre, driver::pageSource,
                    screenshotProbe(), safeShot());
            return ActionResult.fromSettle(SettleActionKind.RESTART, sr, businessAssertOk)
                    .withDetail("restart clearData=false");
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    private ActionResult scrollLimit(ScrollUntilRequest req, String reason) {
        return new ActionResult(ActionResult.Status.SCROLL_LIMIT, ActionOutcome.NO_CHANGE,
                null, false, req.limitTuningMessage(reason), false);
    }

    private ActionResult runGesture(
            SettleActionKind kind,
            int nodeIndex,
            boolean retryIfNoChange,
            boolean mutatingUncertain,
            boolean businessAssertOk
    ) {
        try {
            MobileSnapshot pre = driver.pageSource();
            MobileSnapshotNode n = pre.node(nodeIndex);
            if (!NodeEligibility.actionEligible(n, ActionKind.TAP)) {
                return new ActionResult(ActionResult.Status.HIDDEN_OR_DISABLED, ActionOutcome.NO_CHANGE,
                        null, false, "hidden/disabled", false);
            }
            byte[] shot = safeShot();
            driver.tap(nodeIndex);
            SettleResult sr = settle.settle(kind, pre, driver::pageSource, screenshotProbe(), shot);
            ActionResult first = ActionResult.fromSettle(kind, sr, businessAssertOk);
            var decision = RetryIfNoChangePolicy.evaluate(retryIfNoChange, kind, mutatingUncertain,
                    sr.outcome(), false);
            if (decision == RetryIfNoChangePolicy.Decision.REJECT) {
                throw new IllegalArgumentException("retryIfNoChange rejected");
            }
            if (decision == RetryIfNoChangePolicy.Decision.ALLOW_ONCE) {
                driver.tap(nodeIndex);
                SettleResult sr2 = settle.settle(kind, driver.pageSource(), driver::pageSource,
                        screenshotProbe(), shot);
                return ActionResult.fromSettle(kind, sr2, businessAssertOk).withRetried(true);
            }
            return first;
        } catch (MobileDriverPort.DriverException e) {
            return mapDriver(e);
        }
    }

    private byte[] safeShot() {
        try {
            return driver.screenshot();
        } catch (MobileDriverPort.DriverException e) {
            return new byte[0];
        }
    }

    private ScreenshotProbe screenshotProbe() {
        return () -> {
            try {
                return driver.screenshot();
            } catch (MobileDriverPort.DriverException e) {
                return new byte[0];
            }
        };
    }

    private static ActionResult mapDriver(MobileDriverPort.DriverException e) {
        return switch (e.kind()) {
            case STALE -> new ActionResult(ActionResult.Status.STALE, ActionOutcome.NO_CHANGE, null, false, e.getMessage(), false);
            case TRANSPORT -> new ActionResult(ActionResult.Status.TRANSPORT_FAILED, ActionOutcome.NO_CHANGE, null, false, e.getMessage(), false);
            case HIDDEN, DISABLED -> new ActionResult(ActionResult.Status.HIDDEN_OR_DISABLED, ActionOutcome.NO_CHANGE, null, false, e.getMessage(), false);
            case CANCELLED -> new ActionResult(ActionResult.Status.CANCELLED, ActionOutcome.NO_CHANGE, null, false, e.getMessage(), false);
            default -> new ActionResult(ActionResult.Status.ACTION_FAILED, ActionOutcome.NO_CHANGE, null, false, e.getMessage(), false);
        };
    }
}
