package delivery.guard;

import delivery.effect.EffectSignature;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * A10: loop guard for heal, Bug Hunter and Precision (after e2e agent/loop-guards.ts). Feed it every step
 * ({@link #record}); it detects identical repeated actions on the same state, short cycles (period 2..4)
 * over (state, action), failure streaks and no-progress runs (A7 state signature unchanged), and enforces
 * attempt / wall-clock / model-call / cost budgets. A stop yields a {@link Terminal} outcome that is never
 * PASS; the caller ends the loop and records it.
 */
public final class LoopGuard {
    public static final String CONTRACT = "keel.loop-guards";
    public static final int VERSION = 1;
    static final int MAX_CYCLE_PERIOD = 4;

    public enum Loop { HEAL("heal"), BUG_HUNTER("bugHunter"), PRECISION("precision");
        final String key;
        Loop(String k) { key = k; }
    }

    /** Terminal outcomes; none of them is a success. */
    public enum Terminal { MAX_ATTEMPTS, TIME_CAP, MODEL_CALL_BUDGET, COST_BUDGET, REPEATED_ACTION, CYCLE, FAILURE_STREAK, NO_PROGRESS,
        /** A failed step whose IR sideEffects is not "none" came round again: re-running it could double-submit. */
        UNSAFE_RETRY }

    public enum Level { CONTINUE, WARN, STOP }

    public record Verdict(Level level, Terminal terminal, String reason) {
        static final Verdict CONTINUE = new Verdict(Level.CONTINUE, null, "");
        public boolean stop() { return level == Level.STOP; }
    }

    public record Limits(int maxAttempts, long maxWallMs, int maxModelCalls, double maxCostUnits, int warnRepeatAction,
                         int stopRepeatAction, int warnCycle, int stopCycle, int warnFailureStreak, int stopFailureStreak,
                         int stopNoProgress) {
    }

    /**
     * One loop iteration. state = A7 signature of the page before the action (null if unknown);
     * action = canonical action string (kind + target + value-hash); ok = the step's own result.
     */
    public record Step(EffectSignature state, String action, boolean ok, int modelCalls, double costUnits,
                       delivery.settle.SettlePolicy.SideEffects sideEffects) {
        /** sideEffects unknown == treated as mutating (Codegen r1/05). */
        public Step(EffectSignature state, String action, boolean ok, int modelCalls, double costUnits) {
            this(state, action, ok, modelCalls, costUnits, delivery.settle.SettlePolicy.SideEffects.UNKNOWN);
        }
    }

    /** Retry gate for any loop or A7 retry: only a step proven read-only may be executed again after failing. */
    public static boolean mayRetry(delivery.settle.SettlePolicy.SideEffects se) {
        return se != null && se.retrySafe();
    }

    /** Effective retry count for a configured retry budget (e.g. ActionEffectChecker retries). */
    public static int safeRetries(delivery.settle.SettlePolicy.SideEffects se, int configured) {
        return mayRetry(se) ? Math.max(0, configured) : 0;
    }

    private final Loop loop;
    private final Limits limits;
    private final LongSupplier clock;
    private final long startMs;
    private final List<String> keys = new ArrayList<>();
    private final Map<String, Integer> repeats = new HashMap<>();
    private final java.util.Set<String> failedUnsafe = new java.util.HashSet<>();
    private final Deque<String> states = new ArrayDeque<>();
    private int attempts, modelCalls, failureStreak, noProgress;
    private double cost;
    private String lastState;
    private Verdict terminal;

    public LoopGuard(Loop loop, Limits limits, LongSupplier clock) {
        this.loop = loop;
        this.limits = limits;
        this.clock = clock;
        this.startMs = clock.getAsLong();
    }

    public static LoopGuard of(Loop loop) {
        return new LoopGuard(loop, builtin(loop), System::currentTimeMillis);
    }

    /** Check before spending: true when the loop may run another iteration. */
    public Verdict beforeIteration() {
        if (terminal != null) return terminal;
        if (attempts >= limits.maxAttempts()) return stop(Terminal.MAX_ATTEMPTS, attempts + " attempts");
        if (clock.getAsLong() - startMs >= limits.maxWallMs()) return stop(Terminal.TIME_CAP, (clock.getAsLong() - startMs) + " ms");
        if (modelCalls >= limits.maxModelCalls()) return stop(Terminal.MODEL_CALL_BUDGET, modelCalls + " model calls");
        if (cost >= limits.maxCostUnits()) return stop(Terminal.COST_BUDGET, "cost " + cost);
        return Verdict.CONTINUE;
    }

    public Verdict record(Step s) {
        if (terminal != null) return terminal;
        attempts++;
        modelCalls += Math.max(0, s.modelCalls());
        cost += Math.max(0, s.costUnits());
        String state = s.state() == null ? "?" : stateKey(s.state());
        String key = state + "|" + Objects.requireNonNullElse(s.action(), "");
        keys.add(key);
        // same action, same state, previous run of it failed, and it may have side effects: never re-run
        if (failedUnsafe.contains(key))
            return stop(Terminal.UNSAFE_RETRY, "retry of failed " + (s.sideEffects() == null ? "unknown" : s.sideEffects().wire)
                    + " step '" + s.action() + "'");
        if (!s.ok() && !mayRetry(s.sideEffects())) failedUnsafe.add(key);

        // no progress: the page state did not change between consecutive iterations
        if (lastState != null && !"?".equals(state) && state.equals(lastState)) noProgress++; else noProgress = 0;
        lastState = state;
        failureStreak = s.ok() ? 0 : failureStreak + 1;
        int rep = repeats.merge(key, 1, Integer::sum);

        Verdict v = Verdict.CONTINUE;
        if (rep >= limits.stopRepeatAction()) return stop(Terminal.REPEATED_ACTION, "same action on same state x" + rep);
        if (rep >= limits.warnRepeatAction()) v = warn("same action on same state x" + rep);
        int cyc = cycleRepeats();
        if (cyc >= limits.stopCycle()) return stop(Terminal.CYCLE, "cycle repeated x" + cyc);
        if (cyc >= limits.warnCycle()) v = warn("cycle repeated x" + cyc);
        if (failureStreak >= limits.stopFailureStreak()) return stop(Terminal.FAILURE_STREAK, failureStreak + " failures in a row");
        if (failureStreak >= limits.warnFailureStreak()) v = warn(failureStreak + " failures in a row");
        if (noProgress >= limits.stopNoProgress()) return stop(Terminal.NO_PROGRESS, noProgress + " iterations without state change");
        Verdict b = beforeIteration();
        return b.stop() ? b : v;
    }

    /** Largest number of back-to-back repetitions of a period-2..4 block at the end of the history. */
    int cycleRepeats() {
        int best = 0;
        for (int p = 2; p <= MAX_CYCLE_PERIOD; p++) {
            if (keys.size() < 2 * p) continue;
            int n = keys.size();
            List<String> block = keys.subList(n - p, n);
            if (Set.copyOf(block).size() < 2) continue; // a constant run is a repeat, not a cycle
            int reps = 1;
            for (int start = n - 2 * p; start >= 0; start -= p) {
                if (keys.subList(start, start + p).equals(block)) reps++; else break;
            }
            best = Math.max(best, reps);
        }
        return best;
    }

    static String stateKey(EffectSignature s) {
        return s.url() + "#" + s.pageModelHash() + "#" + s.domDigest() + "#" + s.targetState();
    }

    public Verdict terminal() { return terminal; }

    public Loop loop() { return loop; }

    private Verdict warn(String r) { return new Verdict(Level.WARN, null, loop.key + ": " + r); }

    private Verdict stop(Terminal t, String r) {
        terminal = new Verdict(Level.STOP, t, loop.key + ": " + r);
        return terminal;
    }

    private static volatile JSONObject table;

    public static Limits builtin(Loop loop) {
        JSONObject t = table;
        if (t == null) {
            try (InputStream in = LoopGuard.class.getResourceAsStream("/keel/loop-guards.json")) {
                t = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException | NullPointerException e) {
                throw new IllegalStateException("missing /keel/loop-guards.json", e);
            }
            if (!CONTRACT.equals(t.optString("contract")) || t.optInt("version") != VERSION)
                throw new IllegalStateException("loop-guards.json: contract/version mismatch");
            table = t;
        }
        JSONObject l = t.getJSONObject("loops").getJSONObject(loop.key);
        Limits lim = new Limits(l.getInt("maxAttempts"), l.getLong("maxWallMs"), l.getInt("maxModelCalls"), l.getDouble("maxCostUnits"),
                l.getInt("warnRepeatAction"), l.getInt("stopRepeatAction"), l.getInt("warnCycle"), l.getInt("stopCycle"),
                l.getInt("warnFailureStreak"), l.getInt("stopFailureStreak"), l.getInt("stopNoProgress"));
        if (lim.warnRepeatAction() > lim.stopRepeatAction() || lim.warnCycle() > lim.stopCycle()
                || lim.warnFailureStreak() > lim.stopFailureStreak() || lim.maxAttempts() <= 0)
            throw new IllegalStateException("loop-guards.json: inconsistent limits for " + loop.key);
        return lim;
    }
}
