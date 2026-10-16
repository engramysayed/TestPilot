package delivery.authoring;

import delivery.store.PreferredHooksStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class CandidateScorer {
    public static final int MARGIN = 12;
    public static final int MIN_TOTAL = 15;
    public static final int SHORTLIST_MAX = 5;

    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "only",
            "click", "open", "confirm", "verify", "shown", "visible", "page",
            "user", "already", "logged", "enter", "type", "fill", "button",
            "item", "was", "are", "has", "have", "select", "choose", "pick",
            "clicks", "sidebar", "toolbar", "menu");

    private CandidateScorer() {}

    public static int stability(DomCandidate c) {
        if (c == null) return 0;
        String s = c.strategy() == null ? "" : c.strategy().toLowerCase(Locale.ROOT);
        String v = c.value() == null ? "" : c.value();
        if (PreferredHooksStore.matches(v, PreferredHooksStore.current())
                || PreferredHooksStore.matches(s, PreferredHooksStore.current())) {
            return 50;
        }
        if (("css".equals(s) || "cssselector".equals(s) || "xpath".equals(s))
                && (v.contains("data-axis-test-id") || containsPreferredHook(v))) {
            return 50;
        }
        if ("data-testid".equals(s) || "data-test".equals(s) || "data-qa".equals(s) || "testid".equals(s)) {
            return 45;
        }
        if (v.contains("data-testid") || v.contains("data-test=") || v.contains("data-qa=")) {
            return 45;
        }
        if (v.contains("[role=") && (v.contains("aria-label") || v.contains("name="))) {
            return 40;
        }
        if (v.contains("aria-label") || s.contains("aria")) {
            return 36;
        }
        if ("name".equals(s)) {
            return 30;
        }
        if ("id".equals(s)) {
            return GeneratedIdDetector.looksGenerated(v) ? 0 : 24;
        }
        if ("css".equals(s) || "cssselector".equals(s)) {
            if (v.contains(":nth-")) return 2;
            return 12;
        }
        if ("xpath".equals(s)) {
            if (v.matches(".*\\[\\d+\\].*")) return 2;
            return 8;
        }
        return 0;
    }

    public static List<CandidateScore> score(
            StepIntentBinder.IntentLine intent, List<DomCandidate> candidates) {
        if (intent == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<DomCandidate> corpus = List.copyOf(candidates);
        List<CandidateScore> out = new ArrayList<>();
        for (DomCandidate c : candidates) {
            if (c == null) continue;
            int stab = stability(c);
            int uniq = uniqueness(c, corpus);
            int intentMatch = intentMatch(intent, c);
            int role = roleFit(intent, c);
            int labelDist = labelDistance(intent, c);
            CandidateScore cs = new CandidateScore(c, stab, uniq, intentMatch, role, labelDist);
            // Require an intent signal so stability/role alone cannot CLEAR a wrong control.
            if (cs.total() > 0 && (intentMatch > 0 || labelDist > 0)) {
                out.add(cs);
            }
        }
        out.sort(Comparator.comparingInt(CandidateScore::total).reversed());
        return out;
    }

    public static BindDecision decide(List<CandidateScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return BindDecision.empty();
        }
        CandidateScore best = scores.get(0);
        if (best.total() < MIN_TOTAL) {
            return BindDecision.empty();
        }
        List<DomCandidate> corpus = scores.stream().map(CandidateScore::candidate).toList();
        CandidateScore runner = null;
        for (int i = 1; i < scores.size(); i++) {
            CandidateScore s = scores.get(i);
            if (!sameControl(best.candidate(), s.candidate(), corpus)) {
                runner = s;
                break;
            }
        }
        if (runner == null || best.total() - runner.total() >= MARGIN) {
            return BindDecision.clear(best.candidate(), scores);
        }
        List<DomCandidate> shortlist = new ArrayList<>();
        for (CandidateScore s : scores) {
            if (best.total() - s.total() > MARGIN) break;
            boolean twinOfShortlisted = shortlist.stream()
                    .anyMatch(existing -> sameControl(existing, s.candidate(), corpus));
            if (twinOfShortlisted) continue;
            shortlist.add(s.candidate());
            if (shortlist.size() >= SHORTLIST_MAX) break;
        }
        if (shortlist.size() <= 1) {
            return BindDecision.clear(best.candidate(), scores);
        }
        return BindDecision.tie(shortlist, scores);
    }

    public static List<DomCandidate> shortlist(
            StepIntentBinder.IntentLine intent, List<DomCandidate> candidates) {
        return decide(score(intent, candidates)).shortlist();
    }

    static boolean sameControl(DomCandidate a, DomCandidate b, List<DomCandidate> corpus) {
        if (a == null || b == null) return false;
        if (StepIntentBinder.describesSameControl(a, b)) return true;
        return StepIntentBinder.locatorTwins(a, b, corpus == null ? List.of(a, b) : corpus);
    }

    private static int uniqueness(DomCandidate c, List<DomCandidate> corpus) {
        String label = normalizeLabel(c);
        if (label.isEmpty()) return 0;
        int others = 0;
        for (DomCandidate o : corpus) {
            if (o == c) continue;
            if (!label.equals(normalizeLabel(o))) continue;
            if (sameControl(c, o, corpus)) continue;
            others++;
        }
        if (others == 0) return 15;
        if (others == 1) return 0;
        return -10;
    }

    private static int intentMatch(StepIntentBinder.IntentLine intent, DomCandidate c) {
        String quoted = StepIntentBinder.quotedVisibleName(intent.text());
        String label = normalizeLabel(c);
        if (quoted != null && !quoted.isBlank()
                && label.equals(quoted.trim().toLowerCase(Locale.ROOT))) {
            return 40;
        }
        List<String> toks = tokens(intent.text());
        int onLabel = 0;
        int onValue = 0;
        String valueHay = c.value() == null ? "" : c.value().toLowerCase(Locale.ROOT);
        for (String t : toks) {
            if (label.contains(t)) onLabel++;
            if (valueHay.contains(t)) onValue++;
        }
        return Math.min(30, onLabel * 6) + Math.min(10, onValue * 2);
    }

    private static int roleFit(StepIntentBinder.IntentLine intent, DomCandidate c) {
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        String value = c.value() == null ? "" : c.value().toLowerCase(Locale.ROOT);
        boolean clickish = intent.kind() == StepIntentBinder.IntentKind.CLICK
                || intent.kind() == StepIntentBinder.IntentKind.CLICK_LOGIN;
        boolean typeish = intent.kind() == StepIntentBinder.IntentKind.TYPE_FIELD
                || intent.kind() == StepIntentBinder.IntentKind.TYPE_USER
                || intent.kind() == StepIntentBinder.IntentKind.TYPE_PASS;
        if (clickish) {
            if ("button".equals(tag) || "a".equals(tag) || "menuitem".equals(tag) || "summary".equals(tag)
                    || value.contains("role='button'") || value.contains("role=\"button\"")
                    || value.contains("[role=button]") || value.contains("role='menuitem'")
                    || value.contains("role=\"menuitem\"")) {
                return 10;
            }
            if (("div".equals(tag) || "span".equals(tag) || "li".equals(tag))
                    && !value.contains("menuitem")) {
                return 2;
            }
            return 4;
        }
        if (typeish) {
            if ("input".equals(tag) || "select".equals(tag) || "textarea".equals(tag)) {
                return 10;
            }
            return 4;
        }
        return 4;
    }

    private static int labelDistance(StepIntentBinder.IntentLine intent, DomCandidate c) {
        String quoted = StepIntentBinder.quotedVisibleName(intent.text());
        String label = normalizeLabel(c);
        String valueHay = c.value() == null ? "" : c.value().toLowerCase(Locale.ROOT);
        if (quoted != null && !quoted.isBlank()
                && label.equals(quoted.trim().toLowerCase(Locale.ROOT))) {
            return 10;
        }
        List<String> toks = distinctiveTokens(tokens(intent.text()));
        if (toks.isEmpty()) return 0;
        boolean allOnLabel = true;
        boolean anyOnLabel = false;
        boolean anyOnValue = false;
        for (String t : toks) {
            if (label.contains(t)) {
                anyOnLabel = true;
            } else {
                allOnLabel = false;
            }
            if (valueHay.contains(t)) anyOnValue = true;
        }
        if (allOnLabel && anyOnLabel) return 10;
        if (anyOnLabel) return 6;
        if (anyOnValue) return 2;
        return 0;
    }

    private static List<String> distinctiveTokens(List<String> toks) {
        return toks.stream()
                .filter(t -> t.length() >= 4)
                .filter(t -> !STOP.contains(t))
                .toList();
    }

    private static List<String> tokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3)
                .filter(t -> !STOP.contains(t))
                .collect(Collectors.toList());
    }

    private static String normalizeLabel(DomCandidate c) {
        if (c == null || c.label() == null) return "";
        return c.label().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean containsPreferredHook(String value) {
        if (value == null || value.isBlank()) return false;
        String hay = value.toLowerCase(Locale.ROOT);
        for (String hook : PreferredHooksStore.current()) {
            if (hook != null && !hook.isBlank() && hay.contains(hook.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}