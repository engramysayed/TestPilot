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

    public Map<String, PageModel> pages() {
        return pages;
    }

    public String actionsClassName(ProvenStep step) {
        PageModel page = pages.get(pageKey(step));
        return page != null ? page.actionsClassName() : CodegenNaming.actionsClassName(pageKey(step));
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
        return CodegenNaming.actionsClassName(pageName);
    }

    static String locatorsClassName(String pageName) {
        return CodegenNaming.locatorsClassName(pageName);
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
            String stem = CodegenNaming.allocateUnique(usedStems, CodegenNaming.pageStem(name));
            return PageModel.withStem(stem);
        });
    }

    static String pageKey(ProvenStep step) {
        return step.pageName() == null || step.pageName().isBlank() ? "Page" : step.pageName();
    }

    private String allocateField(PageModel page, ProvenStep step) {
        String strategy = normalizeByStrategy(step.locatorStrategy());
        String locatorValue = normalizeLocatorValue(step.locatorStrategy(), step.locatorValue());
        String existing = findFieldForLocator(page, strategy, locatorValue);
        if (existing != null) {
            return existing;
        }
        String field = uniqueFieldName(page, CodegenNaming.locatorFieldName(step));
        page.fieldNames().add(field);
        page.fields().add(new FieldModel(field, strategy, locatorValue));
        return field;
    }

    private void allocateAction(PageModel page, ProvenStep step, String field) {
        String identity = actionIdentity(step);
        if (page.actionByIdentity().containsKey(identity)) {
            return;
        }
        String method = uniqueSymbol(page, CodegenNaming.actionMethodName(step));
        page.methodNames().add(method);
        page.methods().add(new MethodModel(
                method,
                step.action(),
                field,
                "type".equalsIgnoreCase(step.action()) || "select".equalsIgnoreCase(step.action())
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
                slotOf(step)
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

    private static String findFieldForLocator(PageModel page, String strategy, String value) {
        String incoming = locatorIdentity(strategy, value);
        for (FieldModel f : page.fields()) {
            if (locatorIdentity(f.strategy(), f.value()).equals(incoming)) {
                return f.name();
            }
        }
        return null;
    }

    static String actionIdentity(ProvenStep step) {
        return pageKey(step)
                + '\0'
                + locatorIdentity(normalizeByStrategy(step.locatorStrategy()),
                normalizeLocatorValue(step.locatorStrategy(), step.locatorValue()))
                + '\0'
                + (step.action() == null ? "" : step.action().trim().toLowerCase(Locale.ROOT));
    }

    static String assertIdentity(ProvenStep step) {
        String type = step.assertionType() == null ? "" : step.assertionType().trim().toLowerCase(Locale.ROOT);
        String loc = locatorIdentity(normalizeByStrategy(step.locatorStrategy()),
                normalizeLocatorValue(step.locatorStrategy(), step.locatorValue()));
        if (isParameterizedAssertion(step.assertionType())) {
            return pageKey(step) + '\0' + type + '\0' + loc;
        }
        String expected = step.assertionExpected() == null ? "" : step.assertionExpected();
        return pageKey(step) + '\0' + type + '\0' + loc + '\0' + expected;
    }

    static boolean isParameterizedAssertion(String assertionType) {
        if (assertionType == null || assertionType.isBlank()) {
            return false;
        }
        String type = assertionType.trim();
        return "textContains".equalsIgnoreCase(type)
                || "urlContains".equalsIgnoreCase(type)
                || "selected".equalsIgnoreCase(type)
                || "checked".equalsIgnoreCase(type);
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
                || "type".equalsIgnoreCase(action)
                || "select".equalsIgnoreCase(action);
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
        if ("id".equals(strat) || "name".equals(strat)) {
            return false;
        }
        String lower = loc.toLowerCase(Locale.ROOT);
        if (lower.contains("data-axis-test-id") || lower.contains("data-test") || lower.contains("data-qa")) {
            return false;
        }
        if (lower.contains("//body") || lower.contains("normalize-space(.)")) {
            return true;
        }
        if ("xpath".equals(strat) && loc.length() > 120) {
            return true;
        }
        return false;
    }

    /** Package-visible for unit tests. */
    static boolean isBodyTextAssertForTest(ProvenStep step) {
        return isBodyTextAssert(step);
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

    public record FieldModel(String name, String strategy, String value) {
    }

    public record MethodModel(String name, String action, String fieldName, boolean needsValue) {
    }

    public record AssertionModel(
            String name, String fieldName, String assertionType, String expected, boolean parameterized, String slot) {
        public AssertionModel(
                String name, String fieldName, String assertionType, String expected, boolean parameterized) {
            this(name, fieldName, assertionType, expected, parameterized, "");
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
        private final String locatorsClassName;
        private final String actionsClassName;
        private final List<FieldModel> fields = new ArrayList<>();
        private final List<MethodModel> methods = new ArrayList<>();
        private final List<AssertionModel> assertions = new ArrayList<>();
        private final Set<String> fieldNames = new LinkedHashSet<>();
        private final Set<String> methodNames = new LinkedHashSet<>();
        private final Set<String> assertNames = new LinkedHashSet<>();
        private final Map<String, String> actionByIdentity = new LinkedHashMap<>();
        private final Map<String, String> assertByIdentity = new LinkedHashMap<>();

        private PageModel(String stem) {
            this.stem = stem;
            this.locatorsClassName = stem + "_Locators";
            this.actionsClassName = stem + "_Actions";
        }

        public static PageModel fromPageName(String pageName) {
            return withStem(CodegenNaming.pageStem(pageName));
        }

        public static PageModel withStem(String stem) {
            return new PageModel(stem);
        }

        /** @deprecated use {@link #actionsClassName()} */
        @Deprecated
        public String className() {
            return actionsClassName;
        }

        public String stem() {
            return stem;
        }

        public String locatorsClassName() {
            return locatorsClassName;
        }

        public String actionsClassName() {
            return actionsClassName;
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
