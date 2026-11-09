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
                          List<String> log) {
        public Map<String, Object> toMap() {
            return Map.of("tcId", tcId, "outcome", outcome.name(), "failureCode", failureCode,
                    "failedStep", failedStep, "detail", detail, "log", log);
        }
    }

    public record CaseInput(String tcId, List<String> steps, List<String> expected, List<String> testData) {
    }

    private final MobileDriverPort driver;
    private final MobileActionExecutor actions;
    private final MobileLocatorBinder binder = new MobileLocatorBinder();
    private final MobileAssertionEngine asserts = new MobileAssertionEngine();
    private final String appPackage;
    private final String appActivity;

    public MobileCaseRunner(MobileDriverPort driver, String appPackage, String appActivity) {
        this.driver = driver;
        this.actions = new MobileActionExecutor(driver, new MobileSettleService());
        this.appPackage = appPackage == null ? "" : appPackage;
        this.appActivity = appActivity == null ? "" : appActivity;
    }

    public Outcome run(CaseInput c) {
        List<String> log = new ArrayList<>();
        try {
            for (int i = 0; i < c.steps().size(); i++) {
                String data = i < c.testData().size() ? c.testData().get(i) : "";
                MobileStep step = MobileStep.parse(c.steps().get(i), data);
                if (step.kind() == MobileStep.Kind.SKIP) {
                    if (!step.raw().isBlank()) log.add("skip: " + step.raw());
                    continue;
                }
                String err = execute(step);
                log.add((err == null ? "ok: " : "FAIL: ") + step.raw() + (err == null ? "" : " — " + err));
                if (err != null) {
                    return new Outcome(c.tcId(), CaseOutcome.FAILED, code(err), i + 1, err, log);
                }
            }
            for (String exp : c.expected()) {
                List<String> q = MobileStep.quoted(exp);
                if (q.isEmpty()) {
                    continue; // only quoted UI text is asserted automatically
                }
                MobileStep check = new MobileStep(MobileStep.Kind.ASSERT_VISIBLE, q.get(0), "", "", exp);
                String err = execute(check);
                log.add((err == null ? "ok: " : "FAIL: ") + "expected " + exp + (err == null ? "" : " — " + err));
                if (err != null) {
                    return new Outcome(c.tcId(), CaseOutcome.FAILED, "ASSERTION_FAILED", -1, err, log);
                }
            }
            return new Outcome(c.tcId(), CaseOutcome.PASSED, "", -1, "", log);
        } catch (MobileExecuteEngine.DeviceLostException e) {
            throw e;
        } catch (RuntimeException e) {
            if (isTransport(e)) {
                throw new MobileExecuteEngine.DeviceLostException(e.getMessage());
            }
            return new Outcome(c.tcId(), CaseOutcome.FAILED, "RUNNER_ERROR", -1, String.valueOf(e.getMessage()), log);
        }
    }

    /** @return null on success, else a reason. */
    String execute(MobileStep step) {
        switch (step.kind()) {
            case BACK:
                return status(actions.pressBack(true));
            case LAUNCH:
                if (appPackage.isBlank()) return "no app package configured for the project";
                return status(actions.launch(appPackage, appActivity, true));
            case SWIPE:
                return status(actions.swipe(NormalizedSwipe.forDirection(ScrollDirection.parse(step.direction()), 0.6),
                        null, true));
            default:
                break;
        }
        MobileSnapshot snap = driver.pageSource();
        if (step.kind() == MobileStep.Kind.ASSERT_VISIBLE || step.kind() == MobileStep.Kind.ASSERT_NOT_VISIBLE) {
            List<MobileSnapshotNode> matches = textMatches(snap, step.target());
            AssertResult r = asserts.assertVisible(snap, matches, step.kind() == MobileStep.Kind.ASSERT_VISIBLE);
            return r.passed() ? null : (r.detail() + ": \"" + step.target() + "\"");
        }
        ActionKind kind = switch (step.kind()) {
            case TYPE -> ActionKind.TYPE;
            case CLEAR -> ActionKind.CLEAR;
            default -> ActionKind.TAP;
        };
        Optional<Integer> node = bind(snap, step.target(), kind);
        if (node.isEmpty()) {
            return "element not found: \"" + step.target() + "\"";
        }
        return switch (step.kind()) {
            case TYPE -> status(actions.type(node.get(), step.value(), false, true));
            case CLEAR -> status(actions.clear(node.get(), true));
            default -> status(actions.tap(node.get(), false, false, true));
        };
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
