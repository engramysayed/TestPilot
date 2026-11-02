package delivery.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PageAccumulator {
    private final Map<String, PageModel> pages = new LinkedHashMap<>();
    private final Set<String> usedStems = new LinkedHashSet<>();

    public void addAll(List<ProvenStep> steps) {
        for (ProvenStep step : steps) {
            add(step);
        }
    }

    public void add(ProvenStep step) {
        if (step == null) {
            return;
        }
        step = EmitStepNormalizer.apply(step);
        if (isBodyTextAssert(step)) {
            PageModel page = pageFor(step);
            allocateAssertion(page, step, null);
            return;
        }
        if (step.locatorValue() == null || step.locatorValue().isBlank()) {
            if (step.assertionType() != null && "urlContains".equalsIgnoreCase(step.assertionType())) {
                allocateAssertion(pageFor(step), step, null);
            }
            return;
        }
        PageModel page = pageFor(step);
        String field = allocateField(page, step);
        if (isAction(step)) {
            allocateAction(page, step, field);
        }
        if (hasAssertion(step)) {
            allocateAssertion(page, step, field);
        }
    }

    /**
     * W3: cache a validated NL->By on the existing page class (never invents a second class).
     */
    public void addNlCachedField(String pageName, NlLocatorCache.CachedBy cached) {
        if (cached == null || cached.value() == null || cached.value().isBlank()) {
            return;
        }
        ProvenStep probe = new ProvenStep(
                "TC_NL",
                pageName == null || pageName.isBlank() ? "Page" : pageName,
                "elementAction",
                "click",
                cached.strategy(),
                cached.value(),
                "",
                "",
                "",
                true,
                "nl-cache:" + cached.nlPhrase());
        PageModel page = pageFor(probe);
        String base = cached.fieldName() == null || cached.fieldName().isBlank()
                ? NlLocatorCache.fieldNameFor(cached.nlPhrase())
                : cached.fieldName();
        String field = uniqueFieldName(page, base);
        page.fieldNames().add(field);
        page.fields().add(new FieldModel(
                field,
                cached.strategy(),
                cached.value(),
                List.of(),
                "",
                false,
                List.of(),
                cached.nlPhrase()));
    }

    /** W3: merge trimmed DOM-diff assert suggestions into the named page. */
    public void addDomDiffSuggestions(String tcId, String pageName, List<DomDiffAssertSuggestor.DiffHint> hints) {
        DomDiffAssertSuggestor.apply(this, tcId, pageName, hints);
    }

    public Map<String, PageModel> pages() {
        return pages;
    }

    public String actionsClassName(ProvenStep step) {
        return pageClassNameFor(step);
    }

    public String pageClassNameFor(ProvenStep step) {
        PageModel page = pages.get(pageKey(step));
        return page != null ? page.pageClassName() : CodegenNaming.pageClassName(pageKey(step));
    }

    public String actionSymbol(ProvenStep step) {
        PageModel page = pages.get(pageKey(step));
        if (page == null) {
            return CodegenNaming.actionMethodName(step);
        }
        String found = page.actionByIdentity().get(actionIdentity(step));
        return found != null ? found : CodegenNaming.actionMethodName(step);
    }

    public String assertSymbol(ProvenStep step) {
        PageModel page = pages.get(pageKey(step));
        if (page == null) {
            return CodegenNaming.assertMethodName(step);
        }
        String found = page.assertByIdentity().get(assertIdentity(step));
        return found != null ? found : CodegenNaming.assertMethodName(step);
    }

    static String pageClassName(String pageName) {
        return CodegenNaming.pageClassName(pageName);
    }

    /** @deprecated use {@link #pageClassName(String)} */
    @Deprecated
    static String locatorsClassName(String pageName) {
        return CodegenNaming.pageClassName(pageName);
    }

    static String actionMethodName(ProvenStep step) {
        return CodegenNaming.actionMethodName(step);
    }

    static String assertMethodName(ProvenStep step) {
        return CodegenNaming.assertMethodName(step);
    }

    private PageModel pageFor(ProvenStep step) {
        String key = pageKey(step);
        return pages.computeIfAbsent(key, name -> {
            String titleStem = CodegenNaming.pageTitleStem(name);
            // Distinct page identities that share a stem still get unique stems (Order-Details
            // vs OrderDetails → OrderDetails + OrderDetails_2). Login aliases share one key.
            String uniqueStem = CodegenNaming.allocateUnique(usedStems, titleStem);
            return PageModel.withStem(uniqueStem);
        });
    }

    /**
     * Merge identity for re-generate: Login / LoginPage / LoginForm / TargetLogin collapse
     * onto one page key so emit never writes {@code Login_Page2}. Other names keep their
     * raw key (colliding stems still get allocateUnique suffixes).
     */
    static String pageKey(ProvenStep step) {
        String raw = step.pageName() == null || step.pageName().isBlank() ? "Page" : step.pageName().trim();
        if ("Login".equalsIgnoreCase(raw)
                || "LoginForm".equalsIgnoreCase(raw)
                || "LoginPage".equalsIgnoreCase(raw)
                || "TargetLogin".equals(raw)) {
            return "Login";
        }
        return raw;
    }

    private String allocateField(PageModel page, ProvenStep step) {
        step = EmitStepNormalizer.apply(step);
        String strategy = normalizeByStrategy(step.locatorStrategy());
        String locatorValue = normalizeLocatorValue(step.locatorStrategy(), step.locatorValue());
        String rowRef = rowFactoryRef(page, step, strategy, locatorValue);
        if (rowRef != null) {
            return rowRef;
        }
        List<String> ranked = rankedFor(step);
        if (ranked.isEmpty() && LocatorCandidate.KEEL.equalsIgnoreCase(
                step.locatorStrategy() == null ? "" : step.locatorStrategy().trim())) {
            throw new IllegalStateException("keel step " + step.tcId() + " has no emittable target"
                    + " (text= literal rejected by the PII filter?): " + step.locatorValue());
        }
        if (ranked.size() == 1 && LocatorCandidate.isPlainCss(ranked.get(0))) {
            // css= only: plain By.cssSelector, no runtime helper needed
            strategy = "cssSelector";
            locatorValue = LocatorCandidate.terminalCss(ranked.get(0));
            ranked = List.of();
        }
        MultiLocatorBag.Bag bag = MultiLocatorBag.build(
                step.locatorStrategy(), step.locatorValue(), step.locatorCandidates(),
                delivery.store.PreferredHooksStore.current());
        // Keep proven/ladder primary for the field; bag fallbacks are plain By companions (W3).
        if (bag.primary() != null && bag.primary().value() != null && !bag.primary().value().isBlank()) {
            strategy = normalizeByStrategy(bag.primary().strategy());
            locatorValue = normalizeLocatorValue(bag.primary().strategy(), bag.primary().value());
            ProvenStep primaryStep = step.withLocator(bag.primary().strategy(), bag.primary().value())
                    .withLocatorCandidates(step.locatorCandidates());
            ranked = rankedFor(primaryStep);
            if (ranked.size() == 1 && LocatorCandidate.isPlainCss(ranked.get(0))) {
                strategy = "cssSelector";
                locatorValue = LocatorCandidate.terminalCss(ranked.get(0));
                ranked = List.of();
            }
        }
        String existing = findFieldForLocator(page, strategy, locatorValue, ranked);
        if (existing != null) {
            return existing;
        }
        String field = uniqueFieldName(page, CodegenNaming.locatorFieldName(step));
        page.fieldNames().add(field);
        List<MultiLocatorBag.Entry> bagFalls = bag.fallbacks();
        if (ranked.isEmpty()) {
            page.fields().add(new FieldModel(field, strategy, locatorValue, List.of(), "", false, bagFalls, ""));
        } else {
            page.fields().add(new FieldModel(field, strategy, locatorValue, ranked,
                    LocatorCandidate.keelByExpression(field, ranked), false, bagFalls, ""));
        }
        return field;
    }

    /**
     * Ranked fallbacks for a step carrying the candidate model; empty for legacy steps whose single
     * locator lives in the top document (they keep the plain {@code By} field).
     */
    static List<String> rankedFor(ProvenStep step) {
        if (step == null) {
            return List.of();
        }
        boolean keel = LocatorCandidate.KEEL.equalsIgnoreCase(
                step.locatorStrategy() == null ? "" : step.locatorStrategy().trim());
        if (step.locatorCandidates().isEmpty() && !keel) {
            return List.of();
        }
        // Preferred-hook-first only: with no active hooks, keep the proven primary as a plain By
        // (do not let the candidate ladder promote a css/id fallback over it).
        List<String> hooks = delivery.store.PreferredHooksStore.current();
        if (!keel && (hooks == null || hooks.isEmpty())) {
            return List.of();
        }
        return LocatorCandidate.rankedTargets(step.locatorStrategy(), step.locatorValue(), step.locatorCandidates());
    }

    static FieldModel fieldModel(PageModel page, String fieldName) {
        for (FieldModel f : page.fields()) {
            if (f.name().equals(fieldName)) {
                return f;
            }
        }
        return new FieldModel(fieldName, "", "");
    }

    boolean isSmartField(PageModel page, String fieldName) {
        for (FieldModel f : page.fields()) {
            if (f.name().equals(fieldName)) {
                return f.smart();
            }
        }
        return false;
    }

    private void allocateAction(PageModel page, ProvenStep step, String field) {
        String identity = actionIdentity(step);
        if (page.actionByIdentity().containsKey(identity)) {
            return;
        }
        String method = uniqueSymbol(page, CodegenNaming.actionMethodName(step));
        page.methodNames().add(method);
        String dropField = "";
        if (step.isDrag()) {
            if (step.dropCandidates().isEmpty()) {
                throw new IllegalStateException("drag step " + step.tcId() + " has no dropTarget");
            }
            LocatorCandidate drop = step.dropCandidates().get(0);
            ProvenStep dropStep = new ProvenStep(step.tcId(), step.pageName(), step.actionType(), "drop",
                    drop.strategy(), drop.value(), "", "", "", step.validated(), step.rationale(), "",
                    step.dropCandidates().subList(1, step.dropCandidates().size()));
            dropField = allocateField(page, dropStep);
        }
        page.methods().add(new MethodModel(
                method,
                step.action(),
                field,
                "type".equalsIgnoreCase(step.action()) || "select".equalsIgnoreCase(step.action()),
                isSmartField(page, field),
                fieldModel(page, field).pointer(),
                dropField,
                step.secret()
        ));
        page.actionByIdentity().put(identity, method);
    }

    private void allocateAssertion(PageModel page, ProvenStep step, String field) {
        String identity = assertIdentity(step);
        if (page.assertByIdentity().containsKey(identity)) {
            return;
        }
        String name = uniqueSymbol(page, CodegenNaming.assertMethodName(step));
        page.assertNames().add(name);
        page.assertions().add(new AssertionModel(
                name,
                field,
                step.assertionType(),
                step.assertionExpected() == null ? "" : step.assertionExpected(),
                isParameterizedAssertion(step.assertionType()),
                slotOf(step),
                field != null && isSmartField(page, field)
        ));
        page.assertByIdentity().put(identity, name);
    }

    private static String uniqueSymbol(PageModel page, String base) {
        Set<String> used = new LinkedHashSet<>();
        used.addAll(page.methodNames());
        used.addAll(page.assertNames());
        return CodegenNaming.allocateUnique(used, base);
    }

    private static String uniqueFieldName(PageModel page, String field) {
        if (!page.fieldNames().contains(field)) {
            return field;
        }
        int n = 2;
        String unique = field.replace("_Locator", "_" + n + "_Locator");
        while (page.fieldNames().contains(unique)) {
            n++;
            unique = field.replace("_Locator", "_" + n + "_Locator");
        }
        return unique;
    }

    private static String findFieldForLocator(
            PageModel page, String strategy, String value, List<String> ranked) {
        String incoming = locatorIdentity(strategy, value);
        String incomingContext = contextIdentity(ranked);
        for (FieldModel f : page.fields()) {
            // Same selector text in a different frame / shadow tree is a different element.
            if (locatorIdentity(f.strategy(), f.value()).equals(incoming)
                    && contextIdentity(f.candidates()).equals(incomingContext)) {
                return f.name();
            }
        }
        return null;
    }

    private static String contextIdentity(List<String> ranked) {
        return ranked == null || ranked.isEmpty() ? "" : ranked.get(0);
    }

    static String actionIdentity(ProvenStep step) {
        return pageKey(step)
                + '\0'
                + locatorIdentity(normalizeByStrategy(step.locatorStrategy()),
                normalizeLocatorValue(step.locatorStrategy(), step.locatorValue()))
                + '\0'
                + (step.action() == null ? "" : step.action().trim().toLowerCase(Locale.ROOT))
                + (step.secret() ? "\0secret" : "")
                + contextSuffix(step);
    }

    private static String contextSuffix(ProvenStep step) {
        String ctx = contextIdentity(rankedFor(step));
        return ctx.isEmpty() ? "" : "\u0000ctx:" + ctx;
    }

    static String assertIdentity(ProvenStep step) {
        String type = step.assertionType() == null ? "" : step.assertionType().trim().toLowerCase(Locale.ROOT);
        String loc = locatorIdentity(normalizeByStrategy(step.locatorStrategy()),
                normalizeLocatorValue(step.locatorStrategy(), step.locatorValue()));
        if (isParameterizedAssertion(step.assertionType())) {
            return pageKey(step) + '\0' + type + '\0' + loc + contextSuffix(step);
        }
        String expected = step.assertionExpected() == null ? "" : step.assertionExpected();
        return pageKey(step) + '\0' + type + '\0' + loc + '\0' + expected + contextSuffix(step);
    }

    static boolean isParameterizedAssertion(String assertionType) {
        if (assertionType == null || assertionType.isBlank()) {
            return false;
        }
        String type = assertionType.trim();
        return "textContains".equalsIgnoreCase(type)
                || "urlContains".equalsIgnoreCase(type)
                || "selected".equalsIgnoreCase(type)
                || "checked".equalsIgnoreCase(type)
                || "value".equalsIgnoreCase(type)
                || "valueEquals".equalsIgnoreCase(type)
                || "textEquals".equalsIgnoreCase(type)
                || "ariaRole".equalsIgnoreCase(type)
                || "ariaLabel".equalsIgnoreCase(type)
                || "region".equalsIgnoreCase(type);
    }

    /**
     * List/row selector → shared {@code By} factory. Returns the call expression
     * ({@code productRow("Sauce Labs Backpack")}) or null when the locator stays a static field.
     */
    private String rowFactoryRef(PageModel page, ProvenStep step, String strategy, String locatorValue) {
        RowLocator row = RowLocator.parse(pageKey(step), strategy, locatorValue);
        if (row == null) {
            return null;
        }
        String arg = row.literal();
        if (row.placeholder()) {
            arg = firstNonBlank(step.value(), step.assertionExpected());
            if (arg.isBlank()) {
                return null;
            }
        }
        String factory = ensureRowFactory(page, row);
        return factory + "(" + LocatorCandidate.javaString(arg) + ")";
    }

    private static String ensureRowFactory(PageModel page, RowLocator row) {
        String existing = page.rowFactory(row.key());
        if (existing != null) {
            return existing;
        }
        java.util.Set<String> used = new java.util.LinkedHashSet<>();
        used.addAll(page.fieldNames());
        used.addAll(page.methodNames());
        used.addAll(page.assertNames());
        String name = CodegenNaming.allocateUnique(used, row.factoryBase());
        page.fieldNames().add(name);
        page.methodNames().add(name);
        page.rememberRowFactory(row.key(), name);
        page.fields().add(new FieldModel(name, row.strategy(), row.expr(), List.of(), "", true));
        return name;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b.trim();
    }

    private static String slotOf(ProvenStep step) {
        if (step == null || step.assertionType() == null) {
            return "";
        }
        String type = step.assertionType().trim();
        if ("captureText".equalsIgnoreCase(type) || "capturedEquals".equalsIgnoreCase(type)) {
            return step.value() == null ? "" : step.value();
        }
        return "";
    }

    /**
     * Proven equivalent identities only: same id/name (including {@code #id}) or exact locator.
     * Never treat a bare control id as the same element as a submit selector.
     */
    static String locatorIdentity(String strategy, String value) {
        String s = strategy == null ? "" : strategy.trim();
        String v = value == null ? "" : value;
        if (("cssSelector".equals(s) || "css".equalsIgnoreCase(s))
                && v.startsWith("#") && !v.contains(" ") && !v.contains("[")) {
            return "id:" + v.substring(1);
        }
        if ("id".equals(s)) {
            return "id:" + v;
        }
        if ("name".equals(s)) {
            return "name:" + v;
        }
        return s + ":" + v;
    }

    static boolean sameElementCandidate(FieldModel existing, String strategy, String locatorValue) {
        if (existing == null) {
            return false;
        }
        return locatorIdentity(existing.strategy(), existing.value())
                .equals(locatorIdentity(normalizeByStrategy(strategy), locatorValue));
    }

    private static boolean isAction(ProvenStep step) {
        String action = step.action() == null ? "" : step.action();
        return "click".equalsIgnoreCase(action)
                || "drag".equalsIgnoreCase(action)
                || "type".equalsIgnoreCase(action)
                || "select".equalsIgnoreCase(action)
                || "clear".equalsIgnoreCase(action)
                || "hover".equalsIgnoreCase(action)
                || "doubleclick".equalsIgnoreCase(action)
                || "rightclick".equalsIgnoreCase(action);
    }

    private static boolean hasAssertion(ProvenStep step) {
        return step.assertionType() != null && !step.assertionType().isBlank();
    }

    /** textContains without a solid locator — use body text instead of brittle XPath fields. */
    private static boolean isBodyTextAssert(ProvenStep step) {
        if (step == null || step.assertionType() == null) {
            return false;
        }
        String type = step.assertionType().trim();
        if (!"textContains".equalsIgnoreCase(type)) {
            return false;
        }
        String loc = step.locatorValue() == null ? "" : step.locatorValue().trim();
        if (loc.isBlank()) {
            return true;
        }
        String strat = step.locatorStrategy() == null ? "" : step.locatorStrategy().toLowerCase(Locale.ROOT);
        if ("xpath".equals(strat)) {
            String compact = loc.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if ("//body".equals(compact) || "/html/body".equals(compact)) {
                return false;
            }
        }
        if ("id".equals(strat) || "name".equals(strat)) {
            return false;
        }
        String lower = loc.toLowerCase(Locale.ROOT);
        if (lower.contains("data-axis-test-id") || lower.contains("data-test") || lower.contains("data-qa")) {
            return false;
        }
        return false;
    }

    /** Package-visible for unit tests. */
    static boolean isBodyTextAssertForTest(ProvenStep step) {
        return isBodyTextAssert(step);
    }

    static String normalizeByStrategyForCandidates(String strategy) {
        return normalizeByStrategy(strategy);
    }

    static String normalizeLocatorValueForCandidates(String strategy, String value) {
        return normalizeLocatorValue(strategy, value);
    }

    private static String normalizeByStrategy(String strategy) {
        if (strategy == null) {
            return "cssSelector";
        }
        return switch (strategy.trim().toLowerCase(Locale.ROOT)) {
            case "css", "cssselector" -> "cssSelector";
            case "data-testid", "data-test", "data-qa", "testid" -> "cssSelector";
            case "id" -> "id";
            case "name" -> "name";
            case "xpath" -> "xpath";
            default -> "cssSelector";
        };
    }

    private static String normalizeLocatorValue(String strategy, String value) {
        if (value == null) {
            return "";
        }
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        if (("data-testid".equals(s) || "data-test".equals(s) || "data-qa".equals(s) || "testid".equals(s))
                && !value.contains("[")) {
            String attr = "data-test".equals(s) || "testid".equals(s) ? "data-test"
                    : "data-qa".equals(s) ? "data-qa" : "data-testid";
            return "*[" + attr + "='" + value.replace("'", "") + "']";
        }
        return value;
    }

    /**
     * @param candidates ranked KeelTarget fallbacks (empty = plain By)
     * @param smartExpr  Java {@code KeelBy.of(...)} expression emitted for keel fields
     */
    public record FieldModel(String name, String strategy, String value,
                             List<String> candidates, String smartExpr, boolean parameterized,
                             List<MultiLocatorBag.Entry> bag, String nlPhrase) {
        public FieldModel(String name, String strategy, String value) {
            this(name, strategy, value, List.of(), "", false, List.of(), "");
        }

        public FieldModel(String name, String strategy, String value,
                          List<String> candidates, String smartExpr) {
            this(name, strategy, value, candidates, smartExpr, false, List.of(), "");
        }

        public FieldModel(String name, String strategy, String value,
                          List<String> candidates, String smartExpr, boolean parameterized) {
            this(name, strategy, value, candidates, smartExpr, parameterized, List.of(), "");
        }

        public FieldModel {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            smartExpr = smartExpr == null ? "" : smartExpr;
            bag = bag == null ? List.of() : List.copyOf(bag);
            nlPhrase = nlPhrase == null ? "" : nlPhrase;
        }

        /** FreeMarker: {@code field.smart}. */
        public boolean smart() {
            return !candidates.isEmpty();
        }

        /** W3 multi-locator bag fallbacks (plain By companions). */
        public boolean hasBag() {
            return bag != null && !bag.isEmpty();
        }

        public boolean isBag() {
            return hasBag();
        }

        /** NL-cached validated By (W3). */
        public boolean nlCached() {
            return nlPhrase != null && !nlPhrase.isBlank();
        }

        public boolean isNlCached() {
            return nlCached();
        }

        /** Any target with a v2 provider=/point= modifier: actions click at an offset. */
        public boolean pointer() {
            return candidates.stream().anyMatch(LocatorCandidate::hasPointer);
        }

        public boolean isPointer() {
            return pointer();
        }

        /** Highest contract version among this field's targets (1 for plain By). */
        public int contract() {
            return candidates.stream().mapToInt(LocatorCandidate::requiredVersion).max().orElse(1);
        }

        public boolean isSmart() {
            return smart();
        }

        /** FreeMarker: {@code field.parameterized}. Repeating row/list {@code By} factory. */
        public boolean isParameterized() {
            return parameterized;
        }
    }

    public record MethodModel(String name, String action, String fieldName, boolean needsValue, boolean smart,
                              boolean pointer, String dropFieldName, boolean secret) {
        public MethodModel(String name, String action, String fieldName, boolean needsValue) {
            this(name, action, fieldName, needsValue, false, false, "");
        }

        public MethodModel(String name, String action, String fieldName, boolean needsValue, boolean smart,
                           boolean pointer, String dropFieldName) {
            this(name, action, fieldName, needsValue, smart, pointer, dropFieldName, false);
        }

        public MethodModel(String name, String action, String fieldName, boolean needsValue, boolean smart) {
            this(name, action, fieldName, needsValue, smart, false, "");
        }

        public MethodModel {
            dropFieldName = dropFieldName == null ? "" : dropFieldName;
        }

        public boolean isSmart() {
            return smart;
        }

        public boolean isPointer() {
            return pointer;
        }

        public boolean isSecret() {
            return secret;
        }

        /** A8 settle-policy key: {@code typeSecret} for IR {@code secret: true} type/clear steps (r2 SPEC). */
        public String getSettleAction() {
            return CodegenSecrets.settleKind(action, secret);
        }
    }

    public record AssertionModel(
            String name, String fieldName, String assertionType, String expected, boolean parameterized, String slot,
            boolean smart) {
        public AssertionModel(
                String name, String fieldName, String assertionType, String expected, boolean parameterized) {
            this(name, fieldName, assertionType, expected, parameterized, "", false);
        }

        public AssertionModel(
                String name, String fieldName, String assertionType, String expected, boolean parameterized,
                String slot) {
            this(name, fieldName, assertionType, expected, parameterized, slot, false);
        }

        public boolean isSmart() {
            return smart;
        }

        public AssertionModel {
            slot = slot == null ? "" : slot;
            expected = expected == null ? "" : expected;
            assertionType = assertionType == null ? "" : assertionType;
            fieldName = fieldName == null ? "" : fieldName;
        }
    }

    public static final class PageModel {
        private final String stem;
        private final String pageClassName;
        private final List<FieldModel> fields = new ArrayList<>();
        private final List<MethodModel> methods = new ArrayList<>();
        private final List<AssertionModel> assertions = new ArrayList<>();
        private final Set<String> fieldNames = new LinkedHashSet<>();
        private final Set<String> methodNames = new LinkedHashSet<>();
        private final Set<String> assertNames = new LinkedHashSet<>();
        private final Map<String, String> actionByIdentity = new LinkedHashMap<>();
        private final Map<String, String> assertByIdentity = new LinkedHashMap<>();
        private final Map<String, String> rowFactories = new LinkedHashMap<>();

        private PageModel(String stem, String pageClassName) {
            this.stem = stem;
            this.pageClassName = pageClassName;
            // Template always emits isLoaded(); keep action/assert names off that method.
            methodNames.add("isLoaded");
            fieldNames.add("isLoaded");
        }

        public static PageModel fromPageName(String pageName) {
            String stem = CodegenNaming.pageTitleStem(pageName);
            return withStem(stem);
        }

        public static PageModel withStem(String stem) {
            return new PageModel(stem, CodegenNaming.pageClassNameFromStem(stem));
        }

        /** Single page class name (same as {@link #pageClassName()}). */
        public String className() {
            return pageClassName;
        }

        public String stem() {
            return stem;
        }

        public String pageClassName() {
            return pageClassName;
        }

        /** @deprecated use {@link #pageClassName()} */
        @Deprecated
        public String locatorsClassName() {
            return pageClassName;
        }

        /** @deprecated use {@link #pageClassName()} */
        @Deprecated
        public String actionsClassName() {
            return pageClassName;
        }

        public List<FieldModel> fields() {
            return fields;
        }

        public List<MethodModel> methods() {
            return methods;
        }

        public List<AssertionModel> assertions() {
            return assertions;
        }

        String rowFactory(String patternKey) {
            return rowFactories.get(patternKey);
        }

        void rememberRowFactory(String patternKey, String methodName) {
            rowFactories.put(patternKey, methodName);
        }

        /**
         * Field used by {@code isLoaded()}: preferred hook, then id, test id, name, then any static field.
         */
        public String loadedMarkerRef() {
            FieldModel best = null;
            int bestScore = -1;
            List<String> hooks = delivery.store.PreferredHooksStore.current();
            for (FieldModel f : fields) {
                if (f.parameterized()) {
                    continue;
                }
                int score = markerScore(f, hooks);
                if (score > bestScore) {
                    bestScore = score;
                    best = f;
                }
            }
            return best == null ? "" : best.name();
        }

        private static int markerScore(FieldModel f, List<String> hooks) {
            String value = f.value() == null ? "" : f.value();
            String strategy = f.strategy() == null ? "" : f.strategy();
            String smart = f.smartExpr() == null ? "" : f.smartExpr();
            if (delivery.store.PreferredHooksStore.matches(value, hooks)
                    || delivery.store.PreferredHooksStore.matches(strategy, hooks)
                    || delivery.store.PreferredHooksStore.matches(smart, hooks)) {
                return 100;
            }
            if ("id".equals(strategy)) {
                return 80;
            }
            String low = (value + " " + smart).toLowerCase(java.util.Locale.ROOT);
            if (low.contains("data-testid") || low.contains("data-test=") || low.contains("data-qa=")) {
                return 70;
            }
            if ("name".equals(strategy)) {
                return 60;
            }
            return 10;
        }

        Set<String> fieldNames() {
            return fieldNames;
        }

        Set<String> methodNames() {
            return methodNames;
        }

        Set<String> assertNames() {
            return assertNames;
        }

        Map<String, String> actionByIdentity() {
            return actionByIdentity;
        }

        Map<String, String> assertByIdentity() {
            return assertByIdentity;
        }
    }
}
