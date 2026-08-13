package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Maps Excel manual wording to intents and binds each intent to one DOM candidate.
 * Site-agnostic: token overlap with live candidates only — no host/product hardcoding.
 * Ollama is only for shortlist ties.
 */
public final class StepIntentBinder {
    private static final Pattern LINE = Pattern.compile("(?m)^\\s*(?:\\d+[.)]\\s*)?(.*\\S)\\s*$");

    public enum IntentKind {
        TYPE_USER, TYPE_PASS, CLICK_LOGIN, TYPE_FIELD, ASSERT_VISIBLE, CLICK
    }

    public record IntentLine(IntentKind kind, String text) {
    }

    public record BindResult(List<ProvenStep> steps, String rejectReason) {
        public boolean ok() {
            return rejectReason == null || rejectReason.isBlank();
        }
    }

    private StepIntentBinder() {
    }

    public static List<IntentLine> parseIntents(ManualTestCase tc) {
        List<IntentLine> out = new ArrayList<>();
        String steps = tc == null || tc.steps() == null ? "" : tc.steps();
        var m = LINE.matcher(steps);
        while (m.find()) {
            String line = m.group(1).trim();
            if (line.isBlank()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("post-login") || lower.startsWith("do not ")) {
                continue;
            }
            if (isNavigationalLoginOrUrl(lower)) {
                continue;
            }
            IntentKind kind = classify(lower);
            if (kind != null) {
                out.add(new IntentLine(kind, line));
            }
        }
        String expected = tc == null || tc.expectedResult() == null ? "" : tc.expectedResult();
        if (out.stream().noneMatch(i -> i.kind() == IntentKind.ASSERT_VISIBLE)
                && looksLikeAssert(expected.toLowerCase(Locale.ROOT))) {
            out.add(new IntentLine(IntentKind.ASSERT_VISIBLE, expected));
        }
        return out;
    }

    /** True for "open/go to login page" / visit app URL — handled by job navigate + login prelude. */
    public static boolean isNavigationalLoginOrUrl(String lower) {
        if (lower == null || lower.isBlank()) {
            return false;
        }
        String s = lower.toLowerCase(Locale.ROOT);
        boolean nav = s.contains("open") || s.contains("go to") || s.contains("navigate")
                || s.contains("visit") || s.startsWith("go to");
        if (!nav) {
            return false;
        }
        return s.contains("login") || s.contains("sign in")
                || s.contains("base url") || s.contains("application url")
                || s.contains("website") || s.contains("home page");
    }

    private static final Pattern AT_PATH = Pattern.compile(
            "(?i)\\bat\\s+(https?://\\S+|/[\\w./~-]+)");
    /** Only when the path is the open target itself — not a slash inside "Add/Remove". */
    private static final Pattern OPEN_PATH_DIRECT = Pattern.compile(
            "(?i)(?:open|go\\s+to|navigate\\s+to|visit)\\s+(https?://\\S+|/[\\w./~-]+)\\b");

    /** Excel "Open … at /checkboxes" — handled by prove-phase URL navigate, not a DOM click. */
    public static boolean isOpenPathNavigateIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return firstOpenPath("", text) != null;
    }

    public static String firstOpenPath(String preconditions, String steps) {
        String blob = (preconditions == null ? "" : preconditions) + "\n" + (steps == null ? "" : steps);
        Matcher at = AT_PATH.matcher(blob);
        if (at.find()) {
            return cleanPath(at.group(1));
        }
        Matcher direct = OPEN_PATH_DIRECT.matcher(blob);
        if (direct.find()) {
            return cleanPath(direct.group(1));
        }
        return null;
    }

    private static String cleanPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String p = path.trim().replaceAll("[.,);]+$", "");
        if (p.equals("/") || p.isBlank()) {
            return null;
        }
        return p;
    }

    public static BindResult bind(ManualTestCase tc, List<DomCandidate> candidates) {
        return bind(tc, candidates, List.of());
    }

    public static BindResult bind(ManualTestCase tc, List<DomCandidate> candidates, List<String> preferOnTie) {
        List<IntentLine> intents = parseIntents(tc);
        if (intents.isEmpty()) {
            return new BindResult(List.of(), "No actionable intents parsed from Excel steps");
        }
        List<ProvenStep> steps = new ArrayList<>();
        String tcId = tc.tcId();
        List<String> tieBreak = preferOnTie == null ? List.of() : preferOnTie;
        for (IntentLine intent : intents) {
            if (intent.kind() == IntentKind.TYPE_USER
                    || intent.kind() == IntentKind.TYPE_PASS
                    || intent.kind() == IntentKind.CLICK_LOGIN) {
                continue;
            }
            BindResult one = bindSingle(intent, tcId, candidates, tieBreak);
            if (!one.ok()) {
                return one;
            }
            steps.addAll(one.steps());
        }
        if (steps.isEmpty()) {
            String expected = tc.expectedResult() == null ? "" : tc.expectedResult();
            if (looksLikeAssert(expected.toLowerCase(Locale.ROOT)) || !expected.isBlank()) {
                DomCandidate landmark = bestTokenMatch(expected, candidates);
                if (landmark != null) {
                    steps.add(new ProvenStep(tcId, "Page", "elementAction", "assert",
                            landmark.strategy(), landmark.value(), "", "visible", "", true,
                            "intent:ASSERT_VISIBLE"));
                }
            }
        }
        if (steps.isEmpty()) {
            return new BindResult(List.of(), "No body steps after filtering login intents");
        }
        return new BindResult(steps, "");
    }

    public static BindResult bindSingle(IntentLine intent, String tcId, List<DomCandidate> candidates,
                                        List<String> preferOnTie) {
        if (intent == null) {
            return new BindResult(List.of(), "Null intent");
        }
        if (intent.kind() == IntentKind.TYPE_USER
                || intent.kind() == IntentKind.TYPE_PASS
                || intent.kind() == IntentKind.CLICK_LOGIN) {
            return bindLoginFieldIntent(intent, tcId, candidates);
        }
        List<String> tieBreak = preferOnTie == null ? List.of() : preferOnTie;
        OrdinalControl ordinal = parseOrdinalControl(intent.text());
        List<Scored> scored = scoreCandidates(intent, candidates, ordinal);

        String stateAssert = intent.kind() == IntentKind.ASSERT_VISIBLE
                ? extractStateAssertion(intent.text())
                : null;
        String assertText = (intent.kind() == IntentKind.ASSERT_VISIBLE && stateAssert == null)
                ? extractAssertTextPhrase(intent.text())
                : null;

        if ("notVisible".equals(stateAssert)) {
            String name = extractDisappearedControlName(intent.text());
            if (name != null && !name.isBlank()) {
                String xpath = "//*[self::button or self::a or self::input or @role='button']"
                        + "[contains(normalize-space(.),'" + name.replace("'", "") + "')]";
                boolean ok = new LocatorValidator().validate(
                        new LocatorCandidate("xpath", xpath, "Page", "")).valid();
                return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                        "xpath", xpath, "", "notVisible", name, ok,
                        ok ? "intent:ASSERT_VISIBLE:notVisible" : "xpath allowlist rejected")), "");
            }
        }

        if (scored.isEmpty() && assertText != null && !assertText.isBlank()) {
            String xpath = xpathContainsText(assertText);
            boolean ok = new LocatorValidator().validate(
                    new LocatorCandidate("xpath", xpath, "Page", "")).valid();
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                    "xpath", xpath, "", "textContains", assertText, ok,
                    ok ? "intent:ASSERT_VISIBLE:text" : "xpath allowlist rejected")), "");
        }

        if (scored.isEmpty()) {
            return new BindResult(List.of(),
                    "No DOM candidate for intent " + intent.kind() + ": " + intent.text());
        }
        Scored best = scored.get(0);
        if (best.score < 2) {
            if (assertText != null && !assertText.isBlank()) {
                String xpath = xpathContainsText(assertText);
                boolean ok = new LocatorValidator().validate(
                        new LocatorCandidate("xpath", xpath, "Page", "")).valid();
                return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                        "xpath", xpath, "", "textContains", assertText, ok,
                        ok ? "intent:ASSERT_VISIBLE:text" : "xpath allowlist rejected")), "");
            }
            return new BindResult(List.of(),
                    "Weak candidate match for intent " + intent.kind() + ": " + intent.text());
        }

        // Named action + entity (e.g. "Add Red Backpack"): resolve BEFORE near-tie AMBIGUOUS
        boolean namedActionResolved = false;
        // An explicit ordinal ("checkbox 2") is a stronger selector than verb-on-label matching.
        if (intent.kind() == IntentKind.CLICK && ordinal == null
                && intentRequiresNamedActionControl(intent.text())) {
            DomCandidate match = scored.stream()
                    .map(s -> s.candidate)
                    .filter(c -> candidateMatchesNamedAction(intent.text(), c, candidates))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                match = candidates.stream()
                        .filter(c -> candidateMatchesNamedAction(intent.text(), c, candidates))
                        .findFirst()
                        .orElse(null);
            }
            if (match == null) {
                return new BindResult(List.of(),
                        "No action control matching distinctive tokens for intent "
                                + intent.kind() + ": " + intent.text());
            }
            best = new Scored(match, Math.max(best.score, 50));
            namedActionResolved = true;
        }

        if (!namedActionResolved && scored.size() >= 2 && scored.get(1).score >= best.score - 1) {
            Scored preferred = scored.stream()
                    .filter(s -> tieBreak.stream().anyMatch(id -> id.equalsIgnoreCase(s.candidate.id())))
                    .findFirst()
                    .orElse(null);
            if (preferred != null) {
                best = preferred;
            } else if (ordinal != null) {
                // Prefer exact ordinal xpath/css among near-ties
                Scored ordinalPick = scored.stream()
                        .filter(s -> matchesOrdinalCandidate(s.candidate, ordinal))
                        .findFirst()
                        .orElse(null);
                if (ordinalPick != null) {
                    best = ordinalPick;
                } else {
                    return new BindResult(
                            List.of(),
                            "AMBIGUOUS:" + intent.kind() + ":"
                                    + scored.stream().limit(5).map(s -> s.candidate.id())
                                    .collect(Collectors.joining(",")));
                }
            } else {
                return new BindResult(
                        List.of(),
                        "AMBIGUOUS:" + intent.kind() + ":"
                                + scored.stream().limit(5).map(s -> s.candidate.id())
                                .collect(Collectors.joining(",")));
            }
        }
        if (intent.kind() == IntentKind.CLICK && !namedActionResolved
                && !candidateCarriesDistinctiveTokens(intent.text(), best.candidate, candidates)) {
            return new BindResult(List.of(),
                    "No distinctive-token match for intent " + intent.kind() + ": " + intent.text());
        }

        if (intent.kind() == IntentKind.ASSERT_VISIBLE) {
            if (stateAssert != null) {
                String expected = "";
                if ("selected".equals(stateAssert)) {
                    expected = extractSelectedValuePhrase(intent.text());
                }
                return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                        best.candidate.strategy(), best.candidate.value(),
                        "", stateAssert, expected == null ? "" : expected, true,
                        "intent:" + intent.kind() + ":" + stateAssert)), "");
            }
            if (assertText != null && !assertText.isBlank()) {
                return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                        best.candidate.strategy(), best.candidate.value(),
                        "", "textContains", assertText, true, "intent:" + intent.kind())), "");
            }
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                    best.candidate.strategy(), best.candidate.value(),
                    "", "visible", "", true, "intent:" + intent.kind())), "");
        }

        if (intent.kind() == IntentKind.TYPE_FIELD) {
            String action = resolveFieldAction(best.candidate);
            String value = "";
            if ("type".equals(action) || "select".equals(action)) {
                value = DummyValueInventor.fromStepOrInvent(
                        intent.text(),
                        best.candidate.tag(),
                        inferInputType(best.candidate),
                        best.candidate.value(),
                        best.candidate.label(),
                        best.candidate.label());
            }
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", action,
                    best.candidate.strategy(), best.candidate.value(),
                    value, "", "", true, "intent:" + intent.kind())), "");
        }

        return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "click",
                best.candidate.strategy(), best.candidate.value(),
                "", "", "", true, "intent:" + intent.kind())), "");
    }

    public static BindResult bindPreferring(ManualTestCase tc, List<DomCandidate> candidates,
                                            List<String> preferredIds) {
        return bind(tc, candidates, preferredIds == null ? List.of() : preferredIds);
    }

    /** Ranked DOM candidates for heal shortlists (highest score first). */
    public static List<DomCandidate> rankedCandidates(IntentLine intent, List<DomCandidate> candidates) {
        return scoreCandidates(intent, candidates).stream()
                .map(Scored::candidate)
                .toList();
    }

    static IntentKind classify(String lower) {
        if ((lower.contains("username") || lower.contains("user name") || lower.contains("email"))
                && (lower.contains("enter") || lower.contains("type") || lower.contains("fill"))
                && (lower.contains("login") || lower.contains("sign in") || lower.contains("password")
                || looksLikeLoginCredentialLine(lower))) {
            // username/email fill on login — still TYPE_USER when clearly auth; else TYPE_FIELD
            if (lower.contains("password") || lower.contains("login") || lower.contains("sign in")) {
                return IntentKind.TYPE_USER;
            }
        }
        if ((lower.contains("username") || lower.contains("user name"))
                && (lower.contains("enter") || lower.contains("type") || lower.contains("fill"))) {
            return IntentKind.TYPE_USER;
        }
        if (lower.contains("password")
                && (lower.contains("enter") || lower.contains("type") || lower.contains("fill"))) {
            return IntentKind.TYPE_PASS;
        }
        if ((lower.contains("login") || lower.contains("sign in") || lower.contains("submit"))
                && (lower.contains("click") || lower.contains("press") || lower.contains("button"))) {
            return IntentKind.CLICK_LOGIN;
        }
        if (looksLikeAssert(lower)) {
            return IntentKind.ASSERT_VISIBLE;
        }
        // Generic field fill — any enter/type/fill/select/choose for non-login data
        if (lower.contains("enter") || lower.contains("type") || lower.contains("fill")
                || lower.contains("input") || lower.startsWith("select ")
                || lower.contains("choose") || lower.contains("pick ")) {
            if (!(lower.contains("click") || lower.contains("press"))) {
                return IntentKind.TYPE_FIELD;
            }
        }
        if (lower.contains("click") || lower.contains("open") || lower.startsWith("go to")
                || lower.contains("add ") || lower.contains("press ") || lower.contains("tap ")) {
            return IntentKind.CLICK;
        }
        return null;
    }

    private static boolean looksLikeLoginCredentialLine(String lower) {
        return lower.contains("username") || lower.contains("user name") || lower.contains("password");
    }

    private static boolean looksLikeAssert(String lower) {
        return lower.contains("confirm") || lower.contains("verify") || lower.contains("shown")
                || lower.contains("visible") || lower.contains("see ") || lower.contains("displays")
                || lower.contains("is shown") || lower.contains("are visible");
    }

    private record Scored(DomCandidate candidate, int score) {
    }

    private static List<Scored> scoreCandidates(IntentLine intent, List<DomCandidate> candidates) {
        return scoreCandidates(intent, candidates, parseOrdinalControl(intent.text()));
    }

    private static List<Scored> scoreCandidates(
            IntentLine intent, List<DomCandidate> candidates, OrdinalControl ordinal) {
        List<String> tokens = tokens(intent.text());
        String intentLower = intent.text().toLowerCase(Locale.ROOT);
        List<Scored> scored = new ArrayList<>();
        for (DomCandidate c : candidates) {
            if (!DomCandidateExtractor.isBindableStrategy(c.strategy())) {
                continue;
            }
            String hay = (c.value() + " " + c.label() + " " + c.tag()).toLowerCase(Locale.ROOT);
            int score = tokenOverlapScore(tokens, hay);
            if (intent.kind() == IntentKind.TYPE_FIELD) {
                if (!isFormControl(c)) {
                    continue;
                }
                if (score <= 0) {
                    // Soft match: any form control when intent mentions generic "field/box/input"
                    if (intentLower.contains("field") || intentLower.contains("box")
                            || intentLower.contains("input") || intentLower.contains("dropdown")
                            || intentLower.contains("radio") || intentLower.contains("checkbox")) {
                        score = 1;
                    } else {
                        continue;
                    }
                }
                score += 2; // prefer form controls for TYPE_FIELD
            } else if (score <= 0 && ordinal == null) {
                // Soft: landmark "page is shown" / selected dropdown without strong tokens
                if (intent.kind() == IntentKind.ASSERT_VISIBLE
                        && (intentLower.contains("shown") || intentLower.contains("visible")
                        || intentLower.contains("selected") || intentLower.contains("displayed"))) {
                    if (looksLikeLandmark(hay) || "select".equalsIgnoreCase(c.tag())
                            || hay.contains("flash") || hay.contains("subheader")
                            || hay.contains("heading")
                            || hay.contains("secure") || hay.contains("area")
                            || "h1".equalsIgnoreCase(c.tag()) || "h2".equalsIgnoreCase(c.tag())
                            || "h3".equalsIgnoreCase(c.tag()) || "h4".equalsIgnoreCase(c.tag())) {
                        score = 1;
                    } else if ("selected".equals(extractStateAssertion(intent.text()))
                            && ("select".equalsIgnoreCase(c.tag()) || hay.contains("dropdown")
                            || hay.contains("select"))) {
                        score = 2;
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
            } else if (score <= 0 && ordinal != null && !matchesOrdinalCandidate(c, ordinal)) {
                continue;
            }

            score += DomCandidateExtractor.strategyRank(c.strategy()) / 10;
            // Token hits beat soft landmarks for asserts (e.g. "products" → product-grid)
            if (intent.kind() == IntentKind.ASSERT_VISIBLE) {
                int overlap = tokenOverlapScore(tokens, hay);
                if (overlap > 0) {
                    score += overlap * 2;
                }
            }

            if (ordinal != null) {
                if (matchesOrdinalCandidate(c, ordinal)) {
                    score += 20;
                } else if (isContainerLikely(c, ordinal.type())) {
                    score -= 15;
                }
            }

            // Prefer real controls over containers for clicks; asserts often target div landmarks
            if (intent.kind() == IntentKind.CLICK || intent.kind() == IntentKind.ASSERT_VISIBLE) {
                String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
                if ("input".equals(tag) || "button".equals(tag) || "a".equals(tag)
                        || "select".equals(tag) || "textarea".equals(tag)) {
                    score += 3;
                }
                if (intent.kind() == IntentKind.CLICK
                        && ("form".equals(tag) || "div".equals(tag) || "section".equals(tag)
                        || "ul".equals(tag) || "li".equals(tag))) {
                    score -= 8;
                }
            }

            // Named-control clicks: distinctive tokens beat shared action fillers
            if (intent.kind() == IntentKind.CLICK) {
                score += distinctiveTokenBoost(tokens, hay);
            }

            // Excel names an entity + action verb: prefer controls that carry both
            if (intent.kind() == IntentKind.CLICK && intentRequiresNamedActionControl(intent.text())) {
                if (candidateMatchesNamedAction(intent.text(), c, candidates)) {
                    score += 14;
                } else if (candidateCarriesDistinctiveTokens(intent.text(), c, candidates)) {
                    // Same entity name but wrong control type (e.g. title/image vs action)
                    score -= 16;
                }
            }

            if (intent.kind() == IntentKind.ASSERT_VISIBLE) {
                if (looksLikeLandmark(hay)) {
                    score += 2;
                }
                // Soft boost for heading tags when confirming visible text/landmarks
                String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
                if (tag.matches("h[1-4]")) {
                    score += 3;
                }
                if (looksLikePrimaryAction(hay)) {
                    score -= 4;
                }
            }
            if (intent.kind() == IntentKind.CLICK) {
                // Prefer controls that share the intent's action verb(s)
                for (String verb : intentActionVerbs(intent.text())) {
                    if (hay.contains(verb)) {
                        score += 4;
                    }
                }
                // No mutation verb in Excel → downrank mutation-looking controls (title/link beats "Add …")
                if (intentActionVerbs(intent.text()).isEmpty()) {
                    for (String verb : INTENT_ACTION_VERBS) {
                        if (hayContainsToken(hay, verb)) {
                            score -= 8;
                            break;
                        }
                    }
                }
                if (score > 0 && ("a".equals(c.tag()) || hay.contains("link"))) {
                    score += 1;
                }
                // Images are rarely the click target named in Excel steps
                if ("img".equals(c.tag())) {
                    score -= 5;
                }
            }
            if (intent.kind() == IntentKind.ASSERT_VISIBLE
                    && intentActionVerbs(intent.text()).isEmpty()) {
                for (String verb : INTENT_ACTION_VERBS) {
                    if (hayContainsToken(hay, verb)) {
                        score -= 8;
                        break;
                    }
                }
            }
            if (score > 0) {
                scored.add(new Scored(c, score));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        return scored;
    }

    /** Bind username / password / submit-login intents from live candidates (negative login body). */
    private static BindResult bindLoginFieldIntent(
            IntentLine intent, String tcId, List<DomCandidate> candidates) {
        String[] needles = switch (intent.kind()) {
            case TYPE_USER -> new String[]{"username", "user-name", "user", "email"};
            case TYPE_PASS -> new String[]{"password"};
            case CLICK_LOGIN -> new String[]{"login-button", "submit", "sign-in", "signin", "login"};
            default -> new String[]{};
        };
        DomCandidate best = findFieldByNeedles(candidates, needles);
        if (best == null) {
            return new BindResult(List.of(),
                    "No DOM candidate for intent " + intent.kind() + ": " + intent.text());
        }
        if (intent.kind() == IntentKind.CLICK_LOGIN) {
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "click",
                    best.strategy(), best.value(), "", "", "", true, "intent:CLICK_LOGIN")), "");
        }
        String value = intent.kind() == IntentKind.TYPE_USER
                ? DummyValueInventor.fromStepOrInvent(intent.text(), best.tag(), "text",
                best.value(), best.label(), best.label())
                : DummyValueInventor.fromStepOrInvent(intent.text(), best.tag(), "password",
                best.value(), best.label(), best.label());
        // Prefer explicit Excel token over invent; job secrets still used at codegen via ${}
        if (intent.kind() == IntentKind.TYPE_USER && (value == null || value.isBlank()
                || "TestValue".equals(value))) {
            value = extractCredToken(intent.text(), true);
        }
        if (intent.kind() == IntentKind.TYPE_PASS && (value == null || value.isBlank()
                || "TestPass1!".equals(value))) {
            value = extractCredToken(intent.text(), false);
        }
        return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "type",
                best.strategy(), best.value(), value, "", "", true, "intent:" + intent.kind())), "");
    }

    private static DomCandidate findFieldByNeedles(List<DomCandidate> candidates, String[] needles) {
        DomCandidate best = null;
        int bestRank = -1;
        for (String n : needles) {
            String needle = n.toLowerCase(Locale.ROOT);
            for (DomCandidate c : candidates) {
                if (!DomCandidateExtractor.isBindableStrategy(c.strategy())) {
                    continue;
                }
                String hay = (c.value() + " " + c.label() + " " + c.tag()).toLowerCase(Locale.ROOT);
                if (hay.equals(needle) || hay.contains(needle)) {
                    int rank = DomCandidateExtractor.strategyRank(c.strategy());
                    if (needle.equals(c.value().toLowerCase(Locale.ROOT))) {
                        rank += 50;
                    }
                    if (rank > bestRank) {
                        bestRank = rank;
                        best = c;
                    }
                }
            }
            if (best != null && bestRank >= 40) {
                return best;
            }
        }
        return best;
    }

    /** Last non-field token after enter/type — e.g. student / wrongPassword. */
    private static String extractCredToken(String stepText, boolean user) {
        String extracted = DummyValueInventor.extractExplicitValue(stepText);
        if (extracted != null && !extracted.isBlank()) {
            return extracted;
        }
        if (stepText == null) {
            return user ? "user" : "pass";
        }
        String[] parts = stepText.trim().split("\\s+");
        if (parts.length >= 1) {
            return parts[parts.length - 1].replaceAll("[.,;:]+$", "");
        }
        return user ? "user" : "pass";
    }

    private record OrdinalControl(String type, int index) {
    }

    /** checkbox 1 / radio 2 / first checkbox / second radio */
    static OrdinalControl parseOrdinalControl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("\\b(checkbox|radio)\\s*(\\d+)\\b").matcher(lower);
        if (m.find()) {
            return new OrdinalControl(m.group(1), Integer.parseInt(m.group(2)));
        }
        Matcher first = Pattern.compile("\\b(first|1st)\\s+(checkbox|radio)\\b").matcher(lower);
        if (first.find()) {
            return new OrdinalControl(first.group(2), 1);
        }
        Matcher second = Pattern.compile("\\b(second|2nd)\\s+(checkbox|radio)\\b").matcher(lower);
        if (second.find()) {
            return new OrdinalControl(second.group(2), 2);
        }
        Matcher third = Pattern.compile("\\b(third|3rd)\\s+(checkbox|radio)\\b").matcher(lower);
        if (third.find()) {
            return new OrdinalControl(third.group(2), 3);
        }
        return null;
    }

    static boolean matchesOrdinalCandidate(DomCandidate c, OrdinalControl ordinal) {
        if (c == null || ordinal == null) {
            return false;
        }
        String v = c.value() == null ? "" : c.value().toLowerCase(Locale.ROOT);
        String type = ordinal.type();
        int n = ordinal.index();
        boolean typeInLocator = v.contains("type='" + type + "'") || v.contains("type=\"" + type + "\"");
        if (!typeInLocator) {
            return false;
        }
        // Match on locator index only — labels can include sibling text and must not decide ordinal.
        return v.contains("])[" + n + "]")
                || v.contains("]:nth-of-type(" + n + ")")
                || v.contains(":nth-of-type(" + n + ")");
    }

    private static boolean isContainerLikely(DomCandidate c, String controlType) {
        if (c == null) {
            return false;
        }
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        if ("form".equals(tag) || "div".equals(tag) || "section".equals(tag) || "fieldset".equals(tag)) {
            return true;
        }
        String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
        // id=checkboxes form container matching token "checkbox"
        return controlType != null && hay.contains(controlType) && !"input".equals(tag);
    }

    private static boolean isFormControl(DomCandidate c) {
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        if ("input".equals(tag) || "select".equals(tag) || "textarea".equals(tag)) {
            return true;
        }
        String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
        return hay.contains("input") || hay.contains("select") || hay.contains("textarea")
                || hay.contains("checkbox") || hay.contains("radio") || hay.contains("dropdown");
    }

    private static String resolveFieldAction(DomCandidate c) {
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
        if ("select".equals(tag) || hay.contains("select") || hay.contains("dropdown")) {
            return "select";
        }
        if (hay.contains("radio") || hay.contains("checkbox") || "checkbox".equals(inferInputType(c))
                || "radio".equals(inferInputType(c))) {
            return "click";
        }
        return "type";
    }

    private static String inferInputType(DomCandidate c) {
        String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
        if (hay.contains("password")) {
            return "password";
        }
        if (hay.contains("email")) {
            return "email";
        }
        if (hay.contains("tel") || hay.contains("phone")) {
            return "tel";
        }
        if (hay.contains("radio")) {
            return "radio";
        }
        if (hay.contains("checkbox") || hay.contains("check box")) {
            return "checkbox";
        }
        return "text";
    }

    private static int tokenOverlapScore(List<String> tokens, String hay) {
        int score = 0;
        for (String t : tokens) {
            if (t.length() < 3) {
                continue;
            }
            if (hay.contains(t)) {
                score += t.length() >= 6 ? 3 : 2;
            }
        }
        return score;
    }

    /**
     * Boost/penalize so a named control click (e.g. "Add Red Backpack") cannot bind to a sibling
     * control that only shares generic action filler tokens.
     */
    static int distinctiveTokenBoost(List<String> tokens, String hay) {
        List<String> distinctive = distinctiveTokens(tokens);
        if (distinctive.isEmpty() || hay == null) {
            return 0;
        }
        int boost = 0;
        int missing = 0;
        for (String d : distinctive) {
            if (hayContainsToken(hay, d)) {
                boost += d.length() >= 6 ? 12 : 6;
            } else {
                missing++;
                boost -= d.length() >= 6 ? 18 : (d.length() >= 5 ? 12 : 8);
            }
        }
        if (missing > 0 && boost < 0) {
            boost -= 5;
        }
        return boost;
    }

    static List<String> distinctiveTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            if (t == null || t.length() < 4) {
                continue;
            }
            if (INTENT_FILLER_TOKENS.contains(t)) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    /**
     * True when the candidate carries every discriminating distinctive token from the intent,
     * or when the intent has no such tokens (gate does not apply).
     * Shared catalog tokens that appear on most candidates (e.g. brand prefixes) are ignored
     * when a corpus is provided so entity-name tokens decide the match.
     */
    public static boolean candidateCarriesDistinctiveTokens(String intentText, DomCandidate candidate) {
        return candidateCarriesDistinctiveTokens(intentText, candidate, null);
    }

    public static boolean candidateCarriesDistinctiveTokens(
            String intentText, DomCandidate candidate, List<DomCandidate> corpus) {
        List<String> distinctive = discriminatingTokens(intentText, corpus);
        if (distinctive.isEmpty()) {
            return true;
        }
        String hay = candidate == null ? ""
                : (candidate.value() + " " + candidate.label() + " " + candidate.tag())
                .toLowerCase(Locale.ROOT);
        for (String d : distinctive) {
            if (!hayContainsToken(hay, d)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Distinctive tokens that are not shared across most of the candidate corpus.
     */
    public static List<String> discriminatingTokens(String intentText, List<DomCandidate> corpus) {
        List<String> distinctive = distinctiveTokens(tokens(intentText));
        if (distinctive.isEmpty() || corpus == null || corpus.size() < 4) {
            return distinctive;
        }
        List<String> rare = new ArrayList<>();
        for (String d : distinctive) {
            long hits = 0;
            for (DomCandidate c : corpus) {
                String hay = (c.value() + " " + c.label() + " " + c.tag()).toLowerCase(Locale.ROOT);
                if (hayContainsToken(hay, d)) {
                    hits++;
                }
            }
            if (hits * 2 < corpus.size()) {
                rare.add(d);
            }
        }
        if (!rare.isEmpty()) {
            return rare;
        }
        List<String> longOnes = distinctive.stream().filter(t -> t.length() >= 6).toList();
        return longOnes.isEmpty() ? distinctive : longOnes;
    }

    /**
     * Token present as substring, or as part of a hyphen/underscore compound
     * (e.g. intent token {@code bike} matches {@code bike-light}).
     */
    static boolean hayContainsToken(String hay, String token) {
        if (hay == null || token == null || token.isBlank()) {
            return false;
        }
        return hay.contains(token);
    }

    /**
     * Action verbs that imply a control must carry the verb (not only the entity name).
     * Navigation-style words (open/view/select) are excluded — those often bind to title links.
     * Domain-neutral: no shop or host assumptions.
     */
    private static final java.util.Set<String> INTENT_ACTION_VERBS = java.util.Set.of(
            "add", "remove", "delete", "submit",
            "clear", "upload", "download", "save", "send", "search", "filter", "sort",
            "create", "update", "edit", "cancel", "close", "expand", "collapse");

    /** Action verbs present in the Excel step text. */
    public static List<String> intentActionVerbs(String intentText) {
        List<String> out = new ArrayList<>();
        for (String t : tokens(intentText)) {
            if (INTENT_ACTION_VERBS.contains(t) && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * True when Excel names both an action verb and a distinctive entity — the bound control
     * must carry both (not a title/image that only shares the entity name).
     */
    public static boolean intentRequiresNamedActionControl(String intentText) {
        return !intentActionVerbs(intentText).isEmpty()
                && !distinctiveTokens(tokens(intentText)).isEmpty();
    }

    /**
     * Candidate carries distinctive intent tokens and at least one intent action verb.
     */
    public static boolean candidateMatchesNamedAction(
            String intentText, DomCandidate candidate, List<DomCandidate> corpus) {
        if (!candidateCarriesDistinctiveTokens(intentText, candidate, corpus)) {
            return false;
        }
        List<String> verbs = intentActionVerbs(intentText);
        if (verbs.isEmpty()) {
            return true;
        }
        String hay = candidate == null ? ""
                : (candidate.value() + " " + candidate.label() + " " + candidate.tag())
                .toLowerCase(Locale.ROOT);
        for (String verb : verbs) {
            if (hayContainsToken(hay, verb)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For CLICK intents with distinctive name tokens, keep only candidates that carry them.
     * When the intent also names an action verb, keep only controls that carry that action
     * (never fall back to name-only title/image links).
     */
    public static List<DomCandidate> retainDistinctiveMatches(
            IntentLine intent, List<DomCandidate> candidates) {
        if (intent == null || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        if (intent.kind() != IntentKind.CLICK) {
            return candidates;
        }
        List<String> distinctive = discriminatingTokens(intent.text(), candidates);
        if (distinctive.isEmpty()) {
            return candidates;
        }
        List<DomCandidate> out = new ArrayList<>();
        for (DomCandidate c : candidates) {
            if (candidateCarriesDistinctiveTokens(intent.text(), c, candidates)) {
                out.add(c);
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        if (intentRequiresNamedActionControl(intent.text())) {
            return out.stream()
                    .filter(c -> candidateMatchesNamedAction(intent.text(), c, candidates))
                    .toList();
        }
        return out;
    }

    /**
     * Common Excel/UI filler words stripped when extracting distinctive name tokens.
     * Domain-neutral: applies to any app (forms, lists, dialogs) — not a shop module.
     */
    private static final java.util.Set<String> INTENT_FILLER_TOKENS = java.util.Set.of(
            "check", "checked", "uncheck", "unchecked",
            "add", "cart", "basket", "bag", "remove", "delete", "open", "view", "details",
            "product", "item", "items", "link", "icon", "button", "buttons",
            "shopping", "checkout", "continue", "finish", "submit", "login", "logout",
            "main", "primary", "secondary", "menu", "header", "footer", "nav", "sidebar",
            "page", "shown", "visible", "display", "displayed", "confirm", "click");

    private static DomCandidate bestTokenMatch(String text, List<DomCandidate> candidates) {
        List<String> tokens = tokens(text);
        DomCandidate best = null;
        int bestScore = 0;
        for (DomCandidate c : candidates) {
            if (!DomCandidateExtractor.isBindableStrategy(c.strategy())) {
                continue;
            }
            String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
            int score = tokenOverlapScore(tokens, hay);
            if (score <= 0) {
                continue;
            }
            score += DomCandidateExtractor.strategyRank(c.strategy()) / 10;
            if (looksLikeLandmark(hay)) {
                score += 2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return bestScore >= 2 ? best : null;
    }

    private static boolean looksLikeLandmark(String hay) {
        return hay.contains("container") || hay.contains("list") || hay.contains("badge")
                || hay.contains("title") || hay.contains("heading") || hay.contains("dashboard")
                || hay.contains("main") || hay.contains("content") || hay.contains("grid")
                || hay.contains("flash") || hay.contains("subheader") || hay.contains("secure")
                || hay.contains("banner") || hay.contains("header");
    }

    private static boolean looksLikePrimaryAction(String hay) {
        return hay.contains("submit") || hay.contains("save")
                || hay.contains("continue") || hay.contains("finish")
                || hay.contains("register") || hay.contains("send");
    }

    private static List<String> tokens(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3)
                .filter(t -> !STOP.contains(t))
                .collect(Collectors.toList());
    }

    private static final java.util.Set<String> STOP = java.util.Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "only",
            "click", "open", "confirm", "verify", "shown", "visible", "page",
            "user", "already", "logged", "enter", "type", "fill", "button",
            "item", "was", "are", "has", "have", "select", "choose", "pick");

    /**
     * Checked / unchecked / selected state from Excel assert wording (DOM state, not text).
     * @return assertion type name: checked, unchecked, or selected
     */
    static String extractStateAssertion(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return null;
        }
        String lower = intentText.toLowerCase(Locale.ROOT);
        if (!lower.contains("confirm") && !lower.contains("verify") && !lower.contains("assert")
                && !lower.contains("should") && !looksLikeAssert(lower)) {
            // still allow "checkbox 1 is unchecked" style without confirm
            if (!(lower.contains(" is ") || lower.contains(" are "))) {
                return null;
            }
        }
        if (Pattern.compile("\\bis\\s+unchecked\\b|\\bare\\s+unchecked\\b|\\bnot\\s+checked\\b").matcher(lower).find()) {
            return "unchecked";
        }
        if (Pattern.compile("\\bis\\s+checked\\b|\\bare\\s+checked\\b").matcher(lower).find()) {
            return "checked";
        }
        if (Pattern.compile("\\bis\\s+(?:the\\s+)?selected\\b|\\bare\\s+selected\\b").matcher(lower).find()) {
            return "selected";
        }
        if (Pattern.compile(
                "(?i)\\b(no\\s+longer\\s+shown|not\\s+shown|is\\s+gone|disappear|is\\s+not\\s+visible|no\\s+longer\\s+visible)\\b")
                .matcher(lower).find()) {
            return "notVisible";
        }
        return null;
    }

    /** "Confirm the Delete button is no longer shown" → Delete */
    static String extractDisappearedControlName(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile(
                "(?i)\\b(?:confirm|verify)\\s+(?:a\\s+|the\\s+)?(.+?)\\s+(?:button|link|element)?\\s*"
                        + "(?:is\\s+)?(?:no\\s+longer\\s+shown|not\\s+shown|gone|not\\s+visible|no\\s+longer\\s+visible)")
                .matcher(intentText);
        if (m.find()) {
            String v = m.group(1).trim().replaceAll("(?i)\\b(button|link|element)\\b", "").trim();
            return v.isBlank() ? null : v;
        }
        return null;
    }

    /** "Confirm Option 2 is the selected value" → Option 2 */
    static String extractSelectedValuePhrase(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return "";
        }
        Matcher m = Pattern.compile(
                "(?i)\\b(?:confirm|verify)\\s+(.+?)\\s+is\\s+(?:the\\s+)?selected\\b")
                .matcher(intentText);
        if (m.find()) {
            return m.group(1).trim().replaceAll("[.,;:]+$", "");
        }
        return "";
    }

    /**
     * Pull a human-visible phrase from assert wording only when Excel names explicit text
     * (quoted, or "the text/message/label X is visible"). Does not treat landmark "page is shown"
     * or checkbox state lines as textContains phrases.
     */
    static String extractAssertTextPhrase(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return null;
        }
        if (extractStateAssertion(intentText) != null) {
            return null;
        }
        var quoted = Pattern.compile("[\"']([^\"']{2,80})[\"']").matcher(intentText);
        if (quoted.find()) {
            return quoted.group(1).trim();
        }
        var textVisible = Pattern.compile(
                "(?i)\\b(?:the\\s+)?(?:text|message|label|heading)\\s+(.+?)\\s+is\\s+(?:visible|shown|displayed)\\b")
                .matcher(intentText);
        if (textVisible.find()) {
            return textVisible.group(1).trim().replaceAll("[.,;:]+$", "");
        }
        return null;
    }

    static String xpathContainsText(String text) {
        if (text == null || text.isBlank()) {
            return "//body//*[not(self::script)][not(self::style)][not(self::noscript)]"
                    + "[contains(normalize-space(.),'')]";
        }
        String lit = text.contains("'") ? escapeXpathLiteral(text) : "'" + text + "'";
        // Prefer body content — head/title/json-ld match contains() but Selenium getText() is empty
        return "//body//*[not(self::script)][not(self::style)][not(self::noscript)]"
                + "[contains(normalize-space(.)," + lit + ")]";
    }

    static String escapeXpathLiteral(String text) {
        if (text == null) {
            return "''";
        }
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        String[] parts = text.split("'", -1);
        StringBuilder sb = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(",\"'\",");
            }
            sb.append("'").append(parts[i]).append("'");
        }
        sb.append(")");
        return sb.toString();
    }
}
