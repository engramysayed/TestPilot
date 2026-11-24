package delivery.mobile.runner.agent;

import delivery.mobile.runner.action.ActionResult;
import delivery.mobile.runner.action.MobileActionExecutor;
import delivery.mobile.runner.action.MobileDriverPort;
import delivery.mobile.runner.action.NormalizedSwipe;
import delivery.mobile.runner.action.ScrollDirection;
import delivery.mobile.runner.assertx.AssertResult;
import delivery.mobile.runner.assertx.MobileAssertionEngine;
import delivery.mobile.runner.bind.ActionKind;
import delivery.mobile.runner.bind.BindResult;
import delivery.mobile.runner.bind.LocatorRequest;
import delivery.mobile.runner.bind.LocatorStrategy;
import delivery.mobile.runner.bind.MobileLocatorBinder;
import delivery.mobile.runner.execute.CaseOutcome;
import delivery.mobile.runner.execute.MobileExecuteEngine;
import delivery.mobile.runner.settle.MobileSettleService;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs one case on a live session: for every line, take a snapshot, interpret it ({@link MobileStep}),
 * bind the target (resource id → accessibility id → exact text → label sibling → normalized contains), act, and
 * settle. Expected-result lines with a quoted text are asserted at the end.
 */
public final class MobileCaseRunner {

    /** Result of one case, serialisable for the portal. */
    public record Outcome(String tcId, CaseOutcome outcome, String failureCode, int failedStep, String detail,
                          List<String> log, List<WalkedLocator> locators) {
        public Outcome(String tcId, CaseOutcome outcome, String failureCode, int failedStep, String detail,
                       List<String> log) {
            this(tcId, outcome, failureCode, failedStep, detail, log, List.of());
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("tcId", tcId);
            m.put("outcome", outcome.name());
            m.put("failureCode", failureCode);
            m.put("failedStep", failedStep);
            m.put("detail", detail);
            m.put("log", log);
            m.put("locators", locators == null ? List.of() : locators.stream().map(WalkedLocator::toMap).toList());
            return m;
        }
    }

    public record CaseInput(String tcId, List<String> steps, List<String> expected, List<String> testData) {
    }

    /**
     * Optional per-step screenshot sink. Invoked after each step's execution (whether it passed or
     * failed). stepIndex is 1-based so it matches the naming the portal expects (step-NNN.png).
     * Exceptions are swallowed by the runner so a flaky capture never breaks the run.
     */
    @FunctionalInterface
    public interface StepCapture {
        void accept(String tcId, int stepIndex, byte[] pngBytes);
    }

    /**
     * Optional ambiguity breaker. When the locator ladder ends with more than one actionable
     * text-contains hit, {@link #bind} delegates the pick to this binder; a non-empty return
     * value is the chosen node index. Null → no delegation (ambiguity returns not-found as
     * before). Returning {@link Optional#empty()} ⇒ keep the not-found behavior (used when the
     * remote caller fails or falls back).
     *
     * <p>Production wiring is
     * {@code delivery.mobile.runner.precision.MobilePrecisionClient}, which POSTs to the
     * portal's {@code /api/v1/runners/mobile/runs/{runId}/precision/bind}.
     */
    @FunctionalInterface
    public interface PrecisionBinder {
        Optional<Integer> pick(String intent, List<MobileSnapshotNode> candidates);
    }

    private final MobileDriverPort driver;
    private final MobileActionExecutor actions;
    private final MobileLocatorBinder binder = new MobileLocatorBinder();
    private final MobileAssertionEngine asserts = new MobileAssertionEngine();
    private final String appPackage;
    private final String appActivity;
    private final StepCapture stepCapture;
    private final PrecisionBinder precision;
    private final List<WalkedLocator> captured = new ArrayList<>();

    public MobileCaseRunner(MobileDriverPort driver, String appPackage, String appActivity) {
        this(driver, appPackage, appActivity, null, null);
    }

    public MobileCaseRunner(MobileDriverPort driver, String appPackage, String appActivity, StepCapture stepCapture) {
        this(driver, appPackage, appActivity, stepCapture, null);
    }

    public MobileCaseRunner(MobileDriverPort driver, String appPackage, String appActivity,
                            StepCapture stepCapture, PrecisionBinder precision) {
        this.driver = driver;
        this.actions = new MobileActionExecutor(driver, new MobileSettleService());
        this.appPackage = appPackage == null ? "" : appPackage;
        this.appActivity = appActivity == null ? "" : appActivity;
        this.stepCapture = stepCapture;
        this.precision = precision;
    }

    public Outcome run(CaseInput c) {
        return run(c, false);
    }

    /**
     * @param keepGoing when true, a missed step is recorded and later steps are located only.
     *                  They are not tapped, typed, or swiped. Execute passes false and stops.
     */
    public Outcome run(CaseInput c, boolean keepGoing) {
        return run(c, keepGoing, null);
    }

    public Outcome run(CaseInput c, boolean keepGoing, ActionBudget budget) {
        List<String> log = new ArrayList<>();
        captured.clear();
        String firstErr = null;
        int firstStep = -1;
        String firstCode = "STEP_FAILED";
        try {
            for (int i = 0; i < c.steps().size(); i++) {
                String data = i < c.testData().size() ? c.testData().get(i) : "";
                MobileStep step = MobileStep.parse(c.steps().get(i), data);
                if (step.kind() == MobileStep.Kind.SKIP) {
                    if (!step.raw().isBlank()) log.add("skip: " + step.raw());
                    continue;
                }
                if (budget != null && !budget.take()) {
                    log.add("stopped: step budget used");
                    return finish(c.tcId(), CaseOutcome.FAILED, "STEP_BUDGET", i + 1,
                            "Stopped at the step budget.", log);
                }
                boolean watchOnly = keepGoing && firstErr != null;
                String err = watchOnly ? locate(step) : execute(step);
                if (watchOnly) {
                    log.add((err == null ? "located: " : "not on this screen: ") + step.raw()
                            + (err == null ? "" : " — " + err));
                } else {
                    log.add((err == null ? "ok: " : "FAIL: ") + step.raw() + (err == null ? "" : " — " + err));
                }
                tryCaptureStep(c.tcId(), i + 1);
                if (err != null && !watchOnly) {
                    if (firstErr == null) {
                        firstErr = err;
                        firstStep = i + 1;
                        firstCode = code(err);
                    }
                    if (!keepGoing) {
                        return finish(c.tcId(), CaseOutcome.FAILED, firstCode, firstStep, firstErr, log);
                    }
                }
            }
            for (String exp : c.expected()) {
                List<String> q = MobileStep.quoted(exp);
                if (q.isEmpty()) {
                    continue; // only quoted UI text is asserted automatically
                }
                MobileStep check = new MobileStep(MobileStep.Kind.ASSERT_VISIBLE, q.get(0), "", "", exp);
                if (budget != null && !budget.take()) {
                    log.add("stopped: step budget used");
                    return finish(c.tcId(), CaseOutcome.FAILED, "STEP_BUDGET", -1,
                            "Stopped at the step budget.", log);
                }
                boolean watchOnly = keepGoing && firstErr != null;
                String err = watchOnly ? locate(check) : execute(check);
                log.add((err == null ? "ok: " : "FAIL: ") + "expected " + exp + (err == null ? "" : " — " + err));
                if (err != null && !watchOnly) {
                    if (firstErr == null) {
                        firstErr = err;
                        firstCode = "ASSERTION_FAILED";
                    }
                    if (!keepGoing) {
                        return finish(c.tcId(), CaseOutcome.FAILED, "ASSERTION_FAILED", -1, err, log);
                    }
                }
            }
            if (firstErr != null) {
                return finish(c.tcId(), CaseOutcome.FAILED, firstCode, firstStep, firstErr, log);
            }
            return finish(c.tcId(), CaseOutcome.PASSED, "", -1, "", log);
        } catch (MobileExecuteEngine.DeviceLostException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isTransport(e)) {
                throw new MobileExecuteEngine.DeviceLostException(e.getMessage());
            }
            return finish(c.tcId(), CaseOutcome.FAILED, "RUNNER_ERROR", -1, String.valueOf(e.getMessage()), log);
        }
    }

    private Outcome finish(String tcId, CaseOutcome outcome, String code, int failedStep, String detail, List<String> log) {
        return new Outcome(tcId, outcome, code, failedStep, detail, log, List.copyOf(captured));
    }

    /** @return null on success, else a reason. */
    private void tryCaptureStep(String tcId, int stepIndex) {
        if (stepCapture == null) return;
        try {
            byte[] png = driver.screenshot();
            if (png != null && png.length > 0) {
                stepCapture.accept(tcId, stepIndex, png);
            }
        } catch (Throwable ignored) {
            // Capture is best-effort; never break a run on a screenshot miss.
        }
    }

    String execute(MobileStep step) {
        return perform(step, true);
    }

    /** Record the locator on the current screen. Do not tap, type, swipe, or press back. */
    private String locate(MobileStep step) {
        return perform(step, false);
    }

    private String perform(MobileStep step, boolean act) {
        switch (step.kind()) {
            case BACK:
                return act ? status(actions.pressBack(true)) : null;
            case LAUNCH:
                if (!act) return null;
                if (appPackage.isBlank()) return "no app package configured for the project";
                return status(actions.launch(appPackage, appActivity, true));
            case SWIPE:
                MobileSnapshot swipeSnap = driver.pageSource();
                remember(WalkedLocator.fromScrollable(swipeSnap.nodes()), swipeSnap);
                if (!act) return null;
                return status(actions.swipe(NormalizedSwipe.forDirection(ScrollDirection.parse(step.direction()), 0.6),
                        null, true));
            default:
                break;
        }
        MobileSnapshot snap = driver.pageSource();
        if (step.kind() == MobileStep.Kind.ASSERT_VISIBLE || step.kind() == MobileStep.Kind.ASSERT_NOT_VISIBLE) {
            List<MobileSnapshotNode> matches = textMatches(snap, step.target());
            remember(matches.isEmpty()
                    ? WalkedLocator.shell(step.target())
                    : WalkedLocator.fromNode(matches.get(0), step.target()), snap);
            if (!act) {
                boolean wantVisible = step.kind() == MobileStep.Kind.ASSERT_VISIBLE;
                boolean seen = !matches.isEmpty();
                return seen == wantVisible ? null : "not on this screen: \"" + step.target() + "\"";
            }
            AssertResult r = asserts.assertVisible(snap, matches, step.kind() == MobileStep.Kind.ASSERT_VISIBLE);
            return r.passed() ? null : (r.detail() + ": \"" + step.target() + "\"");
        }
        ActionKind kind = switch (step.kind()) {
            case TYPE -> ActionKind.TYPE;
            case CLEAR -> ActionKind.CLEAR;
            default -> ActionKind.TAP;
        };
        Optional<Integer> node = bind(snap, step.target(), kind);
        remember(node.isEmpty()
                ? WalkedLocator.shell(step.target())
                : WalkedLocator.fromNode(snap.node(node.get()), step.target()), snap);
        if (node.isEmpty()) {
            return "element not found: \"" + step.target() + "\"";
        }
        if (!act) {
            return null;
        }
        return switch (step.kind()) {
            case TYPE -> status(actions.type(node.get(), step.value(), false, true));
            case CLEAR -> status(actions.clear(node.get(), true));
            default -> status(actions.tap(node.get(), false, false, true));
        };
    }

    private void remember(WalkedLocator loc, MobileSnapshot snap) {
        captured.add(loc.withScreen(ScreenSignature.of(snap, appPackage)));
    }

    Optional<Integer> bind(MobileSnapshot snap, String target, ActionKind kind) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        List<LocatorStrategy> order = new ArrayList<>();
        if (target.contains(":id/")) {
            order.add(LocatorStrategy.RESOURCE_ID);
        }
        order.add(LocatorStrategy.ACCESSIBILITY_ID);
        order.add(LocatorStrategy.TEXT);
        if (kind == ActionKind.TYPE || kind == ActionKind.CLEAR) {
            order.add(LocatorStrategy.LABEL_SIBLING);
        }
        for (LocatorStrategy s : order) {
            try {
                BindResult r = binder.bind(snap, LocatorRequest.hierarchy(s, target, kind));
                if (r.isBound() && r.selected().isPresent()) {
                    return Optional.of(r.selected().get().nodeIndex());
                }
            } catch (RuntimeException ignored) {
                // next strategy
            }
        }
        // Normalized contains (Arabic letter forms, case) — only when exactly one actionable node matches.
        List<MobileSnapshotNode> hits = textMatches(snap, target).stream()
                .filter(MobileSnapshotNode::displayed)
                .filter(n -> kind != ActionKind.TAP || n.clickable() || n.parentIndex() != null)
                .toList();
        if (hits.size() == 1) {
            MobileSnapshotNode n = hits.get(0);
            // tap the nearest clickable ancestor so labels inside buttons work
            if (kind == ActionKind.TAP && !n.clickable()) {
                Integer p = n.parentIndex();
                while (p != null) {
                    MobileSnapshotNode parent = snap.node(p);
                    if (parent.clickable()) {
                        return Optional.of(parent.index());
                    }
                    p = parent.parentIndex();
                }
            }
            return Optional.of(n.index());
        }
        if (hits.size() > 1 && precision != null) {
            try {
                Optional<Integer> picked = precision.pick(target, hits);
                if (picked.isPresent() && hits.stream().anyMatch(n -> n.index() == picked.get())) {
                    return picked;
                }
            } catch (RuntimeException ignored) {
                // Precision is best-effort: fall through to not-found.
            }
        }
        return Optional.empty();
    }

    static List<MobileSnapshotNode> textMatches(MobileSnapshot snap, String target) {
        String want = MobileStep.normalize(target);
        List<MobileSnapshotNode> out = new ArrayList<>();
        if (want.isEmpty()) {
            return out;
        }
        for (MobileSnapshotNode n : snap.nodes()) {
            String t = MobileStep.normalize(n.text());
            String d = MobileStep.normalize(n.contentDesc());
            String id = n.resourceId() == null ? "" : n.resourceId();
            if (t.equals(want) || d.equals(want) || id.endsWith(":id/" + target)
                    || (!t.isEmpty() && t.contains(want)) || (!d.isEmpty() && d.contains(want))) {
                out.add(n);
            }
        }
        return out;
    }

    private static String status(ActionResult r) {
        if (r == null) return "no result";
        if (r.status() == ActionResult.Status.OK) return null;
        if (r.status() == ActionResult.Status.TRANSPORT_FAILED) {
            throw new MobileExecuteEngine.DeviceLostException(r.detail());
        }
        return r.status().name() + (r.detail() == null || r.detail().isBlank() ? "" : ": " + r.detail());
    }

    private static String code(String err) {
        int colon = err.indexOf(':');
        String head = colon > 0 ? err.substring(0, colon) : err;
        return head.matches("[A-Z_]+") ? head : "STEP_FAILED";
    }

    private static boolean isTransport(RuntimeException e) {
        String m = String.valueOf(e.getMessage()).toLowerCase();
        return m.contains("appium unreachable") || m.contains("invalid session id") || m.contains("page source failed");
    }
}
