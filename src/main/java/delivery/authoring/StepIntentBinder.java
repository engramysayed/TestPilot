package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ExcelStepText;
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
    /** Near-tie honesty gate: different controls within this many points → AMBIGUOUS. */
    static final int NEAR_TIE_SCORE_WINDOW = 1;

    private static final Pattern AUTH_ENTRY_HREF = Pattern.compile(
            "(?i)(/login|/signin|/sign-in)(/|\\?|#|'|\"|$)|href\\s*=\\s*['\"][^'\"]*(/login|/signin|/sign-in)");
    private static final Pattern AUTH_ENTRY_LABEL = Pattern.compile("(?i)log\\s*in|sign\\s*in|sign\\s*on");
    private static final Pattern REGISTER_PATH_HREF = Pattern.compile(
            "(?i)(/reg|/register)(/|\\?|#|'|\"|$)|href\\s*=\\s*['\"][^'\"]*(/reg|/register)(/|\\?|#|'|\"|$)");

    public enum IntentKind {
        TYPE_USER, TYPE_PASS, CLICK_LOGIN, TYPE_FIELD, ASSERT_VISIBLE, CLICK
    }

    public record IntentLine(IntentKind kind, String text, String testData) {
        public IntentLine {
            text = text == null ? "" : text;
            testData = testData == null ? "" : testData;
        }

        public IntentLine(IntentKind kind, String text) {
            this(kind, text, "");
        }
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
        String steps = tc == null || tc.steps() == null ? "" : ExcelStepText.normalizeMultiline(tc.steps());
        String[] stepLines = steps.split("\n", -1);
        String testDataRaw = tc == null || tc.testData() == null ? "" : tc.testData();
        String[] dataLines = ExcelStepText.normalizeMultiline(testDataRaw).split("\n", -1);
        for (int i = 0; i < stepLines.length; i++) {
            String line = stripStepNumber(stepLines[i]);
            if (line.isBlank()) {
                continue;
            }
            String data = i < dataLines.length ? dataLines[i].trim() : "";
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("post-login") || lower.startsWith("do not ")) {
                continue;
            }
            if (isNavigationalLoginOrUrl(lower)) {
                continue;
            }
            IntentKind kind = classify(lower);
            if (kind != null) {
                out.add(new IntentLine(kind, line, data));
            }
        }
        String expected = tc == null || tc.expectedResult() == null ? "" : tc.expectedResult();
        if (out.stream().noneMatch(i -> i.kind() == IntentKind.ASSERT_VISIBLE)
                && looksLikeAssert(expected.toLowerCase(Locale.ROOT))) {
            out.add(new IntentLine(IntentKind.ASSERT_VISIBLE, expected, ""));
        }
        return out;
    }

    private static String stripStepNumber(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceFirst("^\\d+[.)]\\s*", "").trim();
    }

    /**
     * Body intents for prove / honesty / revise. Credential kinds stay when this case is the
     * form under test (registration, negative login, public pages). They are stripped only when
     * a login prelude already typed them.
     */
    public static List<IntentLine> bodyIntents(ManualTestCase tc, boolean loginPreludeOwnsCredentials) {
        boolean negativeLogin = LoginStepDetector.isLoginFailureCase(tc);
        return parseIntents(tc).stream()
                .filter(i -> keepInBody(i, loginPreludeOwnsCredentials, negativeLogin))
                .filter(i -> !isNavigationalLoginOrUrl(i.text() == null ? "" : i.text().toLowerCase(Locale.ROOT)))
                .filter(i -> !isOpenPathNavigateIntent(i.text()))
                .toList();
    }

    static boolean isPreludeCredentialKind(IntentKind kind) {
        return kind == IntentKind.TYPE_USER
                || kind == IntentKind.TYPE_PASS
                || kind == IntentKind.CLICK_LOGIN;
    }

    private static boolean keepInBody(
            IntentLine intent,
            boolean loginPreludeOwnsCredentials,
            boolean negativeLogin) {
        if (intent == null || intent.kind() == null) {
            return false;
        }
        if (negativeLogin || !loginPreludeOwnsCredentials) {
            return true;
        }
        return !isPreludeCredentialKind(intent.kind());
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
            return bindLoginFieldIntent(intent, tcId, candidates, preferOnTie);
        }
        List<String> tieBreak = preferOnTie == null ? List.of() : preferOnTie;
        OrdinalControl ordinal = parseOrdinalControl(intent.text());

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
                        + "[contains(normalize-space(.)," + XpathLiterals.quote(name) + ")]";
                boolean ok = new LocatorValidator().validate(
                        new LocatorCandidate("xpath", xpath, "Page", "")).valid();
                return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                        "xpath", xpath, "", "notVisible", name, ok,
                        ok ? "intent:ASSERT_VISIBLE:notVisible" : "xpath allowlist rejected")), "");
            }
        }

        // "Confirm the text X is visible" is a page-phrase check — never score chrome candidates.
        if (assertText != null && !assertText.isBlank()) {
            String xpath = xpathContainsText(assertText);
            boolean ok = new LocatorValidator().validate(
                    new LocatorCandidate("xpath", xpath, "Page", "")).valid();
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                    "xpath", xpath, "", "textContains", assertText, ok,
                    ok ? "intent:ASSERT_VISIBLE:text" : "xpath allowlist rejected")), "");
        }

        List<Scored> scored = scoreCandidates(intent, candidates, ordinal);
        if (scored.isEmpty()) {
            return new BindResult(List.of(),
                    "No DOM candidate for intent " + intent.kind() + ": " + intent.text());
        }
        Scored best = scored.get(0);
        if (wantsFormSubmit(intent.text())) {
            Scored submitBest = scored.stream()
                    .filter(s -> looksLikeFormSubmitControl(s.candidate)
                            && !looksLikeNonSubmitNavigation(s.candidate))
                    .max(Comparator.comparingInt((Scored s) -> formSubmitPreference(s.candidate))
                            .thenComparingInt(Scored::score))
                    .orElse(null);
            if (submitBest != null) {
                best = submitBest;
            } else if (intent.kind() == IntentKind.CLICK) {
                return new BindResult(List.of(),
                        "No form-submit control for intent " + intent.kind() + ": " + intent.text());
            }
        }
        if (best.score < 2) {
            return new BindResult(List.of(),
                    "Weak candidate match for intent " + intent.kind() + ": " + intent.text());
        }

        // Named action + entity (e.g. "Add Red Backpack"): resolve BEFORE near-tie AMBIGUOUS
        boolean namedActionResolved = false;
        if (wantsFormSubmit(intent.text()) && looksLikeFormSubmitControl(best.candidate)
                && !looksLikeNonSubmitNavigation(best.candidate)) {
            namedActionResolved = true;
        }
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

        Scored runnerUp = firstDifferentControl(scored, best);
        if (!namedActionResolved && runnerUp != null
                && runnerUp.score >= best.score - NEAR_TIE_SCORE_WINDOW) {
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
                && !(wantsFormSubmit(intent.text()) && looksLikeFormSubmitControl(best.candidate))
                && !candidateCarriesDistinctiveTokens(intent.text(), best.candidate, candidates)) {
            return new BindResult(List.of(),
                    "No distinctive-token match for intent " + intent.kind() + ": " + intent.text());
        }
        if ((intent.kind() == IntentKind.TYPE_FIELD || intent.kind() == IntentKind.ASSERT_VISIBLE)
                && stateAssert == null
                && !candidateSharesFieldToken(intent.text(), best.candidate)) {
            return new BindResult(List.of(),
                    "No field-name match for intent " + intent.kind() + ": " + intent.text());
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
                        intentRationale(intent) + ":" + stateAssert)), "");
            }
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
                    best.candidate.strategy(), best.candidate.value(),
                    "", "visible", "", true, intentRationale(intent))), "");
        }

        if (intent.kind() == IntentKind.TYPE_FIELD) {
            if (isLeaveOrKeepEmptyStep(intent.text())) {
                return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "clear",
                        best.candidate.strategy(), best.candidate.value(),
                        "", "", "", true, intentRationale(intent))), "");
            }
            String action = resolveFieldAction(best.candidate);
            String value = "";
            if ("type".equals(action) || "select".equals(action)) {
                value = DummyValueInventor.fromStepOrInvent(
                        intent.text(),
                        intent.testData(),
                        best.candidate.tag(),
                        inferInputType(best.candidate),
                        best.candidate.value(),
                        best.candidate.label(),
                        best.candidate.label());
            }
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", action,
                    best.candidate.strategy(), best.candidate.value(),
                    value, "", "", true, intentRationale(intent))), "");
        }

        return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "click",
                best.candidate.strategy(), best.candidate.value(),
                "", "", "", true, intentRationale(intent))), "");
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
        if ((lower.contains("login") || lower.contains("sign in") || lower.contains("signin"))
                && (lower.contains("click") || lower.contains("press") || lower.contains("button"))) {
            return IntentKind.CLICK_LOGIN;
        }
        if (lower.contains("submit")
                && (lower.contains("click") || lower.contains("press") || lower.contains("button"))) {
            return IntentKind.CLICK;
        }
        if (looksLikeAssert(lower)) {
            return IntentKind.ASSERT_VISIBLE;
        }
        // Generic field fill — any enter/type/fill/select/choose for non-login data
        if (lower.contains("enter") || lower.contains("type") || lower.contains("fill")
                || lower.contains("input") || lower.startsWith("select ")
                || lower.contains("choose") || lower.contains("pick ")
                || ((lower.contains("leave") || lower.contains("keep")) && lower.contains("empty"))
                || (lower.contains("leave") && lower.contains("blank"))
                || lower.contains("do not fill") || lower.contains("don't fill") || lower.contains("dont fill")
                || (lower.contains("skip the") && lower.contains("field"))) {
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
            if (looksLikeRevealOrMaskToggle(c) && isFieldTargetIntent(intent.text())) {
                continue;
            }
            if (wantsFormSubmit(intent.text()) && looksLikeNonSubmitNavigation(c)) {
                continue;
            }
            String hay = (c.value() + " " + c.label() + " " + c.tag()).toLowerCase(Locale.ROOT);
            int score = tokenOverlapScore(tokens, hay);
            if (wantsFormSubmit(intent.text()) && looksLikeFormSubmitControl(c)) {
                score += 18 + formSubmitPreference(c);
            }
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
                } else {
                    // The field the step names must clear the soft floor that every sibling gets,
                    // otherwise Day and Month look equally good and the step reads as ambiguous.
                    score += score * 2;
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
                // Do not boost bare anchors for Excel "Submit" — buttons must win Sign-up CTAs.
                if (score > 0 && ("a".equals(c.tag()) || hay.contains("link"))
                        && !wantsFormSubmit(intent.text())) {
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
            IntentLine intent, String tcId, List<DomCandidate> candidates, List<String> preferOnTie) {
        if (preferOnTie != null) {
            for (String id : preferOnTie) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                DomCandidate preferred = DomCandidateExtractor.findById(candidates, id);
                if (preferred == null || !DomCandidateExtractor.isBindableStrategy(preferred.strategy())) {
                    continue;
                }
                if (wantsFormSubmit(intent.text()) && looksLikeNonSubmitNavigation(preferred)) {
                    continue;
                }
                if (intent.kind() == IntentKind.CLICK_LOGIN) {
                    return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "click",
                            preferred.strategy(), preferred.value(), "", "", "", true,
                            "intent:CLICK_LOGIN")), "");
                }
                if (isLeaveOrKeepEmptyStep(intent.text())) {
                    return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "clear",
                            preferred.strategy(), preferred.value(),
                            "", "", "", true, "intent:" + intent.kind())), "");
                }
                String prefValue = intent.kind() == IntentKind.TYPE_USER
                        ? DummyValueInventor.fromStepOrInvent(intent.text(), intent.testData(),
                        preferred.tag(), "text",
                        preferred.value(), preferred.label(), preferred.label())
                        : DummyValueInventor.fromStepOrInvent(intent.text(), intent.testData(),
                        preferred.tag(), "password",
                        preferred.value(), preferred.label(), preferred.label());
                prefValue = resolveLoginTypedValue(intent.kind(), intent.text(), prefValue);
                return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "type",
                        preferred.strategy(), preferred.value(), prefValue, "", "", true,
                        "intent:" + intent.kind())), "");
            }
        }
        String[] needles = switch (intent.kind()) {
            case TYPE_USER -> new String[]{"username", "user-name", "user", "email"};
            case TYPE_PASS -> new String[]{"password"};
            case CLICK_LOGIN -> wantsFormSubmit(intent.text())
                    ? new String[]{"submit", "websubmit", "sign up", "sign-up", "signup",
                    "create account", "create new", "register"}
                    : new String[]{"login-button", "submit", "sign-in", "signin", "login"};
            default -> new String[]{};
        };
        DomCandidate best = findFieldByNeedles(candidates, needles, intent.text());
        if (best == null || (wantsFormSubmit(intent.text()) && looksLikeNonSubmitNavigation(best))) {
            return new BindResult(List.of(),
                    "No DOM candidate for intent " + intent.kind() + ": " + intent.text());
        }
        if (intent.kind() == IntentKind.CLICK_LOGIN) {
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "click",
                    best.strategy(), best.value(), "", "", "", true, "intent:CLICK_LOGIN")), "");
        }
        if (isLeaveOrKeepEmptyStep(intent.text())) {
            return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "clear",
                    best.strategy(), best.value(),
                    "", "", "", true, "intent:" + intent.kind())), "");
        }
        String value = intent.kind() == IntentKind.TYPE_USER
                ? DummyValueInventor.fromStepOrInvent(intent.text(), intent.testData(), best.tag(), "text",
                best.value(), best.label(), best.label())
                : DummyValueInventor.fromStepOrInvent(intent.text(), intent.testData(), best.tag(), "password",
                best.value(), best.label(), best.label());
        value = resolveLoginTypedValue(intent.kind(), intent.text(), value);
        return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "type",
                best.strategy(), best.value(), value, "", "", true, "intent:" + intent.kind())), "");
    }

    /**
     * Login type intents: concrete Excel/TestData wins; otherwise job credentials via ${TARGET_*}.
     * Never leave faker invent or trailing field nouns ("Password", "field") as typed values.
     */
    public static String resolveLoginTypedValue(IntentKind kind, String stepText, String raw) {
        if (kind != IntentKind.TYPE_USER && kind != IntentKind.TYPE_PASS) {
            return raw == null ? "" : raw;
        }
        boolean user = kind == IntentKind.TYPE_USER;
        String extracted = DummyValueInventor.extractExplicitValue(stepText);
        if (extracted != null && !extracted.isBlank() && !DummyValueInventor.looksLikeUnspecifiedValue(extracted)) {
            return extracted;
        }
        if (raw != null && !raw.isBlank()
                && !DummyValueInventor.looksLikeUnspecifiedValue(raw)
                && !"${TARGET_USERNAME}".equals(raw)
                && !"${TARGET_PASSWORD}".equals(raw)
                && !"TestValue".equals(raw)
                && !"TestPass1!".equals(raw)) {
            return raw;
        }
        return user ? "${TARGET_USERNAME}" : "${TARGET_PASSWORD}";
    }

    /**
     * Excel "Click the Submit button" is a form submit, not "go to the login page".
     * "Click the Login button" still binds an auth control.
     */
    public static boolean wantsFormSubmit(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return false;
        }
        String t = intentText.toLowerCase(Locale.ROOT);
        if (!t.contains("submit")) {
            return false;
        }
        return !(t.contains("log in") || t.contains("login") || t.contains("sign in")
                || t.contains("signin"));
    }

    /**
     * Anchors whose href is an auth entry path. A real submit/login-button id must not match.
     */
    public static boolean looksLikeAuthNavigation(DomCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String tag = candidate.tag() == null ? "" : candidate.tag().toLowerCase(Locale.ROOT);
        String value = candidate.value() == null ? "" : candidate.value().toLowerCase(Locale.ROOT);
        String label = candidate.label() == null ? "" : candidate.label().toLowerCase(Locale.ROOT).trim();
        boolean hrefToAuth = AUTH_ENTRY_HREF.matcher(value).find();
        boolean anchor = "a".equals(tag) || value.contains("a[href") || value.startsWith("a[");
        if (hrefToAuth && anchor) {
            return true;
        }
        return "a".equals(tag) && AUTH_ENTRY_LABEL.matcher(label).matches();
    }

    /**
     * Login/signin hrefs, “already have an account”, and register-path anchors that are not
     * a form-submit CTA. Site-agnostic path tokens only — not a product host.
     */
    public static boolean looksLikeNonSubmitNavigation(DomCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        if (looksLikeAuthNavigation(candidate)) {
            return true;
        }
        String label = candidate.label() == null ? "" : candidate.label().toLowerCase(Locale.ROOT);
        if (label.contains("already have")) {
            return true;
        }
        return looksLikeRegisterPathHref(candidate);
    }

    public static boolean looksLikeNonSubmitNavigationLocator(String locatorValue) {
        if (locatorValue == null || locatorValue.isBlank()) {
            return false;
        }
        String loc = locatorValue.toLowerCase(Locale.ROOT);
        if (AUTH_ENTRY_HREF.matcher(loc).find()) {
            return true;
        }
        boolean registerHref = REGISTER_PATH_HREF.matcher(loc).find();
        return registerHref;
    }

    private static boolean looksLikeRegisterPathHref(DomCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String tag = candidate.tag() == null ? "" : candidate.tag().toLowerCase(Locale.ROOT);
        String value = candidate.value() == null ? "" : candidate.value().toLowerCase(Locale.ROOT);
        boolean anchor = "a".equals(tag) || value.contains("a[href") || value.startsWith("a[");
        return anchor && REGISTER_PATH_HREF.matcher(value).find();
    }

    public static boolean looksLikeFormSubmitControl(DomCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        if (looksLikeRegisterPathHref(candidate) || looksLikeAuthNavigation(candidate)) {
            return false;
        }
        String hay = ((candidate.value() == null ? "" : candidate.value())
                + " " + (candidate.label() == null ? "" : candidate.label()))
                .toLowerCase(Locale.ROOT);
        return hay.contains("submit") || hay.contains("websubmit")
                || hay.contains("sign up") || hay.contains("signup") || hay.contains("sign-up")
                || hay.contains("create account") || hay.contains("create new")
                || hayContainsToken(hay, "register");
    }

    /**
     * Prefer literal Submit / buttonish CTAs over bare {@code <a>Sign up</a>} when Excel says Submit.
     */
    static int formSubmitPreference(DomCandidate candidate) {
        if (candidate == null) {
            return 0;
        }
        String tag = candidate.tag() == null ? "" : candidate.tag().toLowerCase(Locale.ROOT);
        String value = candidate.value() == null ? "" : candidate.value().toLowerCase(Locale.ROOT);
        String label = candidate.label() == null ? "" : candidate.label().toLowerCase(Locale.ROOT);
        String hay = value + " " + label;
        int rank = 0;
        if (hay.contains("submit") || hay.contains("websubmit")) {
            rank += 40;
        }
        if ("button".equals(tag) || "input".equals(tag)
                || value.contains("button") || value.contains("[type='submit']")
                || value.contains("[type=\"submit\"]")) {
            rank += 20;
        }
        if (looksLikeBareSignUpAnchor(candidate)) {
            rank -= 25;
        }
        return rank;
    }

    static boolean looksLikeBareSignUpAnchor(DomCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String tag = candidate.tag() == null ? "" : candidate.tag().toLowerCase(Locale.ROOT);
        String value = candidate.value() == null ? "" : candidate.value().toLowerCase(Locale.ROOT);
        boolean anchor = "a".equals(tag) || value.contains("//a[") || value.startsWith("a[")
                || value.startsWith("a ") || "a".equals(value.trim());
        if (!anchor) {
            return false;
        }
        String hay = value + " " + (candidate.label() == null ? "" : candidate.label().toLowerCase(Locale.ROOT));
        return hay.contains("sign up") || hay.contains("signup") || hay.contains("sign-up");
    }

    private static DomCandidate findFieldByNeedles(List<DomCandidate> candidates, String[] needles,
                                                   String intentText) {
        DomCandidate best = null;
        int bestRank = -1;
        boolean formSubmit = wantsFormSubmit(intentText);
        for (String n : needles) {
            String needle = n.toLowerCase(Locale.ROOT);
            for (DomCandidate c : candidates) {
                if (!DomCandidateExtractor.isBindableStrategy(c.strategy())) {
                    continue;
                }
                if (looksLikeRevealOrMaskToggle(c)) {
                    continue;
                }
                if (formSubmit && looksLikeNonSubmitNavigation(c)) {
                    continue;
                }
                String hay = (c.value() + " " + c.label() + " " + c.tag()).toLowerCase(Locale.ROOT);
                if (hay.equals(needle) || hay.contains(needle)) {
                    int rank = DomCandidateExtractor.strategyRank(c.strategy());
                    if (needle.equals(c.value().toLowerCase(Locale.ROOT))) {
                        rank += 50;
                    }
                    String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
                    if ("input".equals(tag) || "textarea".equals(tag)) {
                        rank += 20;
                    }
                    if (formSubmit && ("button".equals(tag) || "input".equals(tag))) {
                        rank += 25;
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

    /** Unused — login typing uses {@link #resolveLoginTypedValue}. Kept temporarily for binary compat. */
    @Deprecated
    private static String extractCredToken(String stepText, boolean user) {
        return user ? "${TARGET_USERNAME}" : "${TARGET_PASSWORD}";
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

    /**
     * The extractor emits a CSS and an XPath form of the same attribute selector, so the runner-up
     * is usually the winner wearing a different hat. Comparing against it would report every such
     * control as ambiguous and send a perfectly bindable step into heal.
     */
    private static Scored firstDifferentControl(List<Scored> scored, Scored best) {
        for (Scored s : scored) {
            if (s != best && !describesSameControl(s.candidate, best.candidate)) {
                return s;
            }
        }
        return null;
    }

    static boolean describesSameControl(DomCandidate a, DomCandidate b) {
        if (a == null || b == null) {
            return false;
        }
        return selectorFingerprint(a).equals(selectorFingerprint(b));
    }

    private static final Pattern ATTR_ID = Pattern.compile(
            "(?:\\[#?id\\s*=\\s*['\"]([^'\"]+)['\"]\\]|\\[@id\\s*=\\s*['\"]([^'\"]+)['\"]\\]"
                    + "|\\[id=['\"]([^'\"]+)['\"]\\])",
            Pattern.CASE_INSENSITIVE);

    private static String selectorFingerprint(DomCandidate c) {
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        String strategy = c.strategy() == null ? "" : c.strategy().toLowerCase(Locale.ROOT);
        String value = c.value() == null ? "" : c.value();
        if ("id".equals(strategy) && !value.isBlank()) {
            return tag + "|id:" + value.toLowerCase(Locale.ROOT);
        }
        Matcher idMatch = ATTR_ID.matcher(value);
        if (idMatch.find()) {
            String id = firstNonBlank(idMatch.group(1), idMatch.group(2), idMatch.group(3));
            if (id != null && !id.isBlank()) {
                return tag + "|id:" + id.toLowerCase(Locale.ROOT);
            }
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("//", "")
                .replace("@", "")
                .replace(" and ", " ")
                .replaceAll("[\\[\\]'\"]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return tag + "|" + normalized;
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return null;
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return null;
    }

    private static final java.util.Set<String> FORM_CONTROL_KINDS = java.util.Set.of(
            "input", "select", "textarea",
            "combobox", "listbox", "textbox", "searchbox", "checkbox", "radio", "switch",
            "spinbutton", "slider");

    private static boolean isFormControl(DomCandidate c) {
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        if (FORM_CONTROL_KINDS.contains(tag)) {
            return true;
        }
        String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
        return hay.contains("input") || hay.contains("select") || hay.contains("textarea")
                || hay.contains("checkbox") || hay.contains("radio") || hay.contains("dropdown");
    }

    static boolean isLeaveOrKeepEmptyStep(String stepText) {
        if (stepText == null || stepText.isBlank()) {
            return false;
        }
        String lower = stepText.toLowerCase(Locale.ROOT);
        if ((lower.contains("leave") || lower.contains("keep")) && lower.contains("empty")) {
            return true;
        }
        if (lower.contains("leave") && lower.contains("blank")) {
            return true;
        }
        if (lower.contains("do not fill") || lower.contains("don't fill") || lower.contains("dont fill")) {
            return true;
        }
        return lower.contains("skip the") && lower.contains("field");
    }

    private static String resolveFieldAction(DomCandidate c) {
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
        if ("select".equals(tag) || "combobox".equals(tag) || "listbox".equals(tag)
                || hay.contains("select") || hay.contains("dropdown")) {
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
            if (hayContainsToken(hay, t)) {
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

    /** Words that name the kind of widget rather than which one the step means. */
    private static final java.util.Set<String> WIDGET_NOUNS = java.util.Set.of(
            "dropdown", "combobox", "listbox", "field", "box", "input", "textbox", "list",
            "menu", "option", "options", "value", "values", "selector", "picker", "control", "area");

    /**
     * Possessives left over after the option value is stripped ("Select Female from the Select
     * your gender dropdown" → "your gender"). They match nothing useful and must not satisfy
     * every "Select …" combobox on the page.
     */
    private static final java.util.Set<String> FIELD_PRONOUNS = java.util.Set.of(
            "your", "my", "our", "their", "its");

    private static final Pattern IN_THE_FIELD = Pattern.compile(
            "(?i)\\bin\\s+the\\s+(.+?)\\s+field\\b");
    private static final Pattern FROM_THE_WIDGET = Pattern.compile(
            "(?i)\\bfrom\\s+the\\s+(.+?)\\s+(?:dropdown|combobox|listbox|menu|select)\\b");
    private static final Pattern THE_WIDGET = Pattern.compile(
            "(?i)\\bthe\\s+(.+?)\\s+(?:dropdown|combobox|listbox|field|menu)\\b");

    /**
     * Best-effort field name from Excel wording, e.g. {@code First name} or {@code gender}.
     * Used to stamp {@code field=} on ProvenStep rationale for codegen when locators lack labels.
     */
    public static String intentFieldPhrase(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return null;
        }
        String named = intentText;
        String value = DummyValueInventor.extractExplicitValue(intentText);
        if (value != null && !value.isBlank()) {
            named = named.replace(value, " ");
        }
        String phrase = firstGroup(IN_THE_FIELD, named);
        if (phrase == null) {
            phrase = firstGroup(FROM_THE_WIDGET, named);
        }
        if (phrase == null) {
            phrase = firstGroup(THE_WIDGET, named);
        }
        if (phrase == null) {
            return null;
        }
        phrase = phrase.trim().replaceAll("\\s+", " ");
        // Drop leading "Select your" / action verbs / pronouns left in the capture.
        phrase = phrase.replaceAll("(?i)^(select|choose|pick)\\s+", "");
        phrase = phrase.replaceAll("(?i)^(your|my|our|their|its)\\s+", "");
        phrase = phrase.replaceAll("(?i)^(select|choose|pick)\\s+", "");
        phrase = phrase.trim();
        if (phrase.isBlank() || WIDGET_NOUNS.contains(phrase.toLowerCase(Locale.ROOT))) {
            return null;
        }
        if (INTENT_FILLER_TOKENS.contains(phrase.toLowerCase(Locale.ROOT))
                || INTENT_ACTION_VERBS.contains(phrase.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return phrase;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    static String intentRationale(IntentLine intent) {
        if (intent == null) {
            return "intent:UNKNOWN";
        }
        String base = "intent:" + intent.kind();
        String field = intentFieldPhrase(intent.text());
        if (field == null || field.isBlank()) {
            return base;
        }
        String slug = field.trim().replaceAll("\\s+", "_");
        return base + ":field=" + slug;
    }

    /**
     * Drop controls this TC already typed into or selected. Asserts keep the original table so
     * "confirm Day shows 15" can still see the dropdown that was just filled.
     */
    public static List<DomCandidate> withoutSpentControls(
            List<DomCandidate> candidates, List<ProvenStep> spent) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        if (spent == null || spent.isEmpty()) {
            return candidates;
        }
        List<DomCandidate> used = spent.stream()
                .filter(StepIntentBinder::isMutation)
                .map(StepIntentBinder::asCandidate)
                .toList();
        if (used.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(c -> used.stream().noneMatch(u -> sameSpentTarget(c, u)))
                .toList();
    }

    /**
     * Drop locators that already failed for this intent, including same-control twins
     * (css vs xpath of the same node).
     */
    public static List<DomCandidate> withoutFailedLocators(
            List<DomCandidate> candidates, List<delivery.heal.FailedLocator> failed) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        if (failed == null || failed.isEmpty()) {
            return candidates;
        }
        List<DomCandidate> banned = failed.stream()
                .filter(f -> f != null && f.value() != null && !f.value().isBlank())
                .map(f -> new DomCandidate(
                        "failed",
                        f.strategy() == null ? "" : f.strategy(),
                        f.value(),
                        "",
                        f.value()))
                .toList();
        if (banned.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(c -> banned.stream().noneMatch(b ->
                        locatorEquals(c, b) || describesSameControl(c, b) || sameSpentTarget(c, b)))
                .toList();
    }

    public static boolean isSpentLocator(DomCandidate candidate, List<ProvenStep> spent) {
        if (candidate == null || spent == null || spent.isEmpty()) {
            return false;
        }
        return spent.stream()
                .filter(StepIntentBinder::isMutation)
                .map(StepIntentBinder::asCandidate)
                .anyMatch(u -> sameSpentTarget(candidate, u));
    }

    /** Proven steps have no tag, so spent matching is on the locator text, not the tag prefix. */
    private static boolean sameSpentTarget(DomCandidate live, DomCandidate spent) {
        if (live == null || spent == null) {
            return false;
        }
        if (locatorEquals(live, spent)) {
            return true;
        }
        String a = selectorFingerprint(live);
        String b = selectorFingerprint(spent);
        int aBar = a.indexOf('|');
        int bBar = b.indexOf('|');
        String aBody = aBar >= 0 ? a.substring(aBar + 1) : a;
        String bBody = bBar >= 0 ? b.substring(bBar + 1) : b;
        return !aBody.isBlank() && aBody.equals(bBody);
    }

    private static boolean isMutation(ProvenStep step) {
        if (step == null || step.action() == null) {
            return false;
        }
        String action = step.action().toLowerCase(Locale.ROOT);
        return "type".equals(action) || "select".equals(action);
    }

    private static DomCandidate asCandidate(ProvenStep step) {
        return new DomCandidate(
                "spent",
                step.locatorStrategy() == null ? "" : step.locatorStrategy(),
                step.locatorValue() == null ? "" : step.locatorValue(),
                "",
                step.locatorValue() == null ? "" : step.locatorValue());
    }

    private static boolean locatorEquals(DomCandidate a, DomCandidate b) {
        if (a == null || b == null) {
            return false;
        }
        String av = a.value() == null ? "" : a.value();
        String bv = b.value() == null ? "" : b.value();
        return av.equalsIgnoreCase(bv);
    }

    /**
     * A field intent names its target ("… in the First name field"), so the control must carry at
     * least one of those words. Without this, every field intent on a page of anonymous inputs
     * binds to whichever input scores first and the value lands in the wrong box.
     * The typed value is excluded — it never appears in a locator.
     * Selected/checked state asserts are different: they target the control just filled, so a
     * select/combobox (or a row that still shows the expected option text) is enough.
     */
    public static boolean candidateSharesFieldToken(String intentText, DomCandidate candidate) {
        if (intentText == null || intentText.isBlank() || candidate == null) {
            return true;
        }
        String state = extractStateAssertion(intentText);
        if ("selected".equals(state) || "checked".equals(state) || "unchecked".equals(state)) {
            return candidateMatchesStateAssert(intentText, candidate, state);
        }
        String named = intentText;
        String value = DummyValueInventor.extractExplicitValue(intentText);
        if (value != null && !value.isBlank()) {
            named = named.replace(value, " ");
        }
        List<String> wanted = tokens(named).stream()
                .filter(t -> !WIDGET_NOUNS.contains(t))
                .filter(t -> !FIELD_PRONOUNS.contains(t))
                .toList();
        if (wanted.isEmpty()) {
            return true;
        }
        if (looksLikeRevealOrMaskToggle(candidate) && isFieldTargetIntent(intentText)) {
            return false;
        }
        String hay = (candidate.value() + " " + candidate.label() + " " + candidate.tag())
                .toLowerCase(Locale.ROOT);
        return wanted.stream().anyMatch(t -> hayContainsToken(hay, t));
    }

    /**
     * Show/hide/reveal controls name the field they mask, so token overlap alone would steal
     * TYPE/ASSERT_VISIBLE binds from the actual input.
     */
    public static boolean looksLikeRevealOrMaskToggle(DomCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String hay = (safeHay(candidate.value()) + " " + safeHay(candidate.label()))
                .toLowerCase(Locale.ROOT);
        return REVEAL_TOGGLE.matcher(hay).find();
    }

    static boolean isFieldTargetIntent(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return false;
        }
        String lower = intentText.toLowerCase(Locale.ROOT);
        if ((lower.contains("click") || lower.contains("press"))
                && REVEAL_TOGGLE.matcher(lower).find()) {
            return false;
        }
        return lower.contains("field")
                || lower.contains("enter")
                || lower.contains("type")
                || lower.contains("fill")
                || lower.contains("input")
                || (lower.contains("visible") && !lower.contains("click"));
    }

    private static final Pattern REVEAL_TOGGLE = Pattern.compile(
            "(?i)\\b(show|hide|reveal|unhide|toggle|mask|unmask)\\b.{0,48}"
                    + "\\b(password|pin|secret|passcode|credential)s?\\b"
                    + "|\\b(password|pin|secret|passcode|credential)s?\\b.{0,48}"
                    + "\\b(show|hide|reveal|unhide|toggle|mask|unmask)\\b");

    private static String safeHay(String value) {
        return value == null ? "" : value;
    }

    private static boolean candidateMatchesStateAssert(
            String intentText, DomCandidate candidate, String state) {
        String hay = (candidate.value() + " " + candidate.label() + " " + candidate.tag())
                .toLowerCase(Locale.ROOT);
        if ("selected".equals(state)) {
            String expected = extractSelectedValuePhrase(intentText);
            if (expected != null && !expected.isBlank()
                    && hay.contains(expected.toLowerCase(Locale.ROOT))) {
                return true;
            }
            String tag = candidate.tag() == null ? "" : candidate.tag().toLowerCase(Locale.ROOT);
            return "select".equals(tag) || "combobox".equals(tag) || "listbox".equals(tag)
                    || hayContainsToken(hay, "select") || hayContainsToken(hay, "dropdown")
                    || hayContainsToken(hay, "combobox");
        }
        // checked / unchecked — any checkbox/radio-shaped control
        String tag = candidate.tag() == null ? "" : candidate.tag().toLowerCase(Locale.ROOT);
        return "input".equals(tag) || "checkbox".equals(tag) || "radio".equals(tag)
                || hayContainsToken(hay, "checkbox") || hayContainsToken(hay, "radio");
    }

    /**
     * Token present as a whole word or hyphen/underscore compound
     * (e.g. {@code bike} matches {@code bike-light}), but not as a prefix of a longer word
     * ({@code element} does not match {@code elemental}).
     */
    static boolean hayContainsToken(String hay, String token) {
        if (hay == null || token == null || token.isBlank()) {
            return false;
        }
        String h = hay.toLowerCase(Locale.ROOT);
        String t = token.toLowerCase(Locale.ROOT);
        Pattern word = Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(t) + "([^a-z0-9]|$)");
        return word.matcher(h).find();
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
        String lit = XpathLiterals.quote(text);
        // Prefer body content — head/title/json-ld match contains() but Selenium getText() is empty.
        // The trailing predicate keeps the innermost match; every ancestor also contains the text.
        return "//body//*[not(self::script)][not(self::style)][not(self::noscript)]"
                + "[contains(normalize-space(.)," + lit + ")]"
                + "[not(.//*[contains(normalize-space(.)," + lit + ")])]";
    }

    static String escapeXpathLiteral(String text) {
        return XpathLiterals.quote(text);
    }
}
