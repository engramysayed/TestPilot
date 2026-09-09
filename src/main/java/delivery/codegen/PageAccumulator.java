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

    public void addAll(List<ProvenStep> steps) {
        for (ProvenStep step : steps) {
            add(step);
        }
    }

    public void add(ProvenStep step) {
        if (step.locatorValue() == null || step.locatorValue().isBlank()) {
            if (step.assertionType() != null && "urlContains".equalsIgnoreCase(step.assertionType())) {
                String pageName = step.pageName() == null || step.pageName().isBlank() ? "Page" : step.pageName();
                PageModel page = pages.computeIfAbsent(pageName, PageModel::fromPageName);
                String assertName = CodegenNaming.assertMethodName(step);
                if (page.assertNames().add(assertName)) {
                    page.assertions().add(new AssertionModel(assertName, null, "urlContains",
                            step.assertionExpected() == null ? "" : step.assertionExpected()));
                }
            }
            return;
        }
        // Pure body-text visibility: Actions use bodyTextContains(expected) — skip brittle XPath fields.
        if (isBodyTextAssert(step)) {
            String pageName = step.pageName() == null || step.pageName().isBlank() ? "Page" : step.pageName();
            PageModel page = pages.computeIfAbsent(pageName, PageModel::fromPageName);
            String assertName = CodegenNaming.assertMethodName(step);
            if (page.assertNames().add(assertName)) {
                page.assertions().add(new AssertionModel(
                        assertName,
                        null,
                        "textContains",
                        step.assertionExpected() == null ? "" : step.assertionExpected()));
            }
            return;
        }
        String pageName = step.pageName() == null || step.pageName().isBlank() ? "Page" : step.pageName();
        PageModel page = pages.computeIfAbsent(pageName, PageModel::fromPageName);
        String field = CodegenNaming.locatorFieldName(step);
        String strategy = normalizeByStrategy(step.locatorStrategy());
        String locatorValue = normalizeLocatorValue(step.locatorStrategy(), step.locatorValue());
        String existingField = findFieldForLocator(page, strategy, locatorValue);
        boolean skipActionMethod = false;
        if (existingField != null) {
            field = existingField;
        } else {
            FieldResolution resolution = resolveClickField(page, step, field, strategy, locatorValue);
            field = resolution.fieldName();
            skipActionMethod = resolution.skipActionMethod();
        }
        if ("click".equalsIgnoreCase(step.action())
                || "type".equalsIgnoreCase(step.action())
                || "select".equalsIgnoreCase(step.action())) {
            String method = CodegenNaming.actionMethodName(step);
            if (!skipActionMethod && page.methodNames().add(method)) {
                page.methods().add(new MethodModel(
                        method,
                        step.action(),
                        field,
                        "type".equalsIgnoreCase(step.action()) || "select".equalsIgnoreCase(step.action())
                ));
            }
        }
        if (step.assertionType() != null && !step.assertionType().isBlank()) {
            String assertName = CodegenNaming.assertMethodName(step);
            if (page.assertNames().add(assertName)) {
                page.assertions().add(new AssertionModel(
                        assertName,
                        field,
                        step.assertionType(),
                        step.assertionExpected() == null ? "" : step.assertionExpected()
                ));
            }
        }
    }

    public Map<String, PageModel> pages() {
        return pages;
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

    private static String findFieldForLocator(PageModel page, String strategy, String value) {
        for (FieldModel f : page.fields()) {
            if (f.strategy().equals(strategy) && f.value().equals(value)) {
                return f.name();
            }
        }
        return null;
    }

    private record FieldResolution(String fieldName, boolean skipActionMethod) {
    }

    private static FieldResolution resolveClickField(
            PageModel page, ProvenStep step, String field, String strategy, String locatorValue) {
        if (page.fieldNames().contains(field)) {
            FieldModel existing = findFieldByName(page, field);
            if (existing != null && isClickAction(step)) {
                if (LocatorPreference.prefer(strategy, locatorValue, existing.strategy(), existing.value())) {
                    replaceFieldLocator(page, field, strategy, locatorValue);
                    removeClickMethodsForField(page, field);
                }
                return new FieldResolution(field, false);
            }
            int n = 2;
            String unique = field;
            while (!page.fieldNames().add(unique)) {
                unique = field.replace("_Locator", "_" + n + "_Locator");
                n++;
            }
            page.fields().add(new FieldModel(unique, strategy, locatorValue));
            return new FieldResolution(unique, false);
        }

        if (isClickAction(step) && field.endsWith("_Btn_Locator")) {
            FieldModel mergeTarget = findSameElementBtnClickField(page, strategy, locatorValue);
            if (mergeTarget != null) {
                return mergeClickFields(page, mergeTarget, strategy, locatorValue);
            }
        }

        page.fieldNames().add(field);
        page.fields().add(new FieldModel(field, strategy, locatorValue));
        return new FieldResolution(field, false);
    }

    private static FieldResolution mergeClickFields(
            PageModel page, FieldModel existing, String strategy, String locatorValue) {
        if (LocatorPreference.prefer(strategy, locatorValue, existing.strategy(), existing.value())) {
            replaceFieldLocator(page, existing.name(), strategy, locatorValue);
            removeClickMethodsForField(page, existing.name());
            return new FieldResolution(existing.name(), false);
        }
        return new FieldResolution(existing.name(), true);
    }

    /**
     * Merge only when locators likely refer to the <em>same</em> control (e.g. bare login id vs
     * {@code button[type=submit]}). Never merge solely on LocatorPreference score gaps — that
     * over-merged unrelated Inventory buttons (backpack add-to-cart vs shopping cart).
     */
    private static FieldModel findSameElementBtnClickField(PageModel page, String strategy, String locatorValue) {
        FieldModel best = null;
        int bestDiff = -1;
        for (FieldModel field : page.fields()) {
            if (!field.name().endsWith("_Btn_Locator")) {
                continue;
            }
            if (!sameElementCandidate(field, strategy, locatorValue)) {
                continue;
            }
            int diff = Math.abs(LocatorPreference.score(strategy, locatorValue)
                    - LocatorPreference.score(field.strategy(), field.value()));
            if (best == null || diff > bestDiff) {
                best = field;
                bestDiff = diff;
            }
        }
        return best;
    }

    static boolean sameElementCandidate(FieldModel existing, String strategy, String locatorValue) {
        if (existing == null) {
            return false;
        }
        String a = normalizeIdentity(existing.strategy(), existing.value());
        String b = normalizeIdentity(strategy, locatorValue);
        if (!a.isEmpty() && a.equals(b)) {
            return true;
        }
        boolean existingSubmit = looksLikeSubmit(existing.strategy(), existing.value());
        boolean incomingSubmit = looksLikeSubmit(strategy, locatorValue);
        boolean existingControlId = looksLikeBareControlId(existing.strategy(), existing.value());
        boolean incomingControlId = looksLikeBareControlId(strategy, locatorValue);
        return (existingSubmit && incomingControlId) || (incomingSubmit && existingControlId);
    }

    private static String normalizeIdentity(String strategy, String value) {
        if (value == null) {
            return "";
        }
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        String v = value.trim().toLowerCase(Locale.ROOT);
        if ("id".equals(s) || "name".equals(s)) {
            return s + ":" + v;
        }
        if (v.startsWith("#") && !v.contains(" ") && !v.contains("[")) {
            return "id:" + v.substring(1);
        }
        return s + ":" + v;
    }

    private static boolean looksLikeSubmit(String strategy, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        return v.contains("type='submit'")
                || v.contains("type=\"submit\"")
                || v.contains("type=submit")
                || v.contains("input[type=submit]")
                || (v.contains("button") && (v.contains("submit") || v.contains("type=")));
    }

    private static boolean looksLikeBareControlId(String strategy, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String s = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        if (!"id".equals(s) && !"name".equals(s)) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        return !v.contains("button") && !v.contains("submit") && !v.contains("type=");
    }

    private static FieldModel findFieldByName(PageModel page, String name) {
        for (FieldModel field : page.fields()) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        return null;
    }

    private static void replaceFieldLocator(PageModel page, String fieldName, String strategy, String value) {
        List<FieldModel> fields = page.fields();
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).name().equals(fieldName)) {
                fields.set(i, new FieldModel(fieldName, strategy, value));
                return;
            }
        }
    }

    private static void removeClickMethodsForField(PageModel page, String fieldName) {
        List<MethodModel> methods = page.methods();
        for (int i = methods.size() - 1; i >= 0; i--) {
            MethodModel method = methods.get(i);
            if (fieldName.equals(method.fieldName()) && "click".equalsIgnoreCase(method.action())) {
                page.methodNames().remove(method.name());
                methods.remove(i);
            }
        }
    }

    private static boolean isClickAction(ProvenStep step) {
        return "click".equalsIgnoreCase(step.action());
    }

    /** textContains without a solid locator — use body text instead of brittle XPath fields. */
    private static boolean isBodyTextAssert(ProvenStep step) {
        if (step == null || step.assertionType() == null) {
            return false;
        }
        if (!"textContains".equalsIgnoreCase(step.assertionType())) {
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

    public record AssertionModel(String name, String fieldName, String assertionType, String expected) {
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

        private PageModel(String stem) {
            this.stem = stem;
            this.locatorsClassName = stem + "_Locators";
            this.actionsClassName = stem + "_Actions";
        }

        public static PageModel fromPageName(String pageName) {
            return new PageModel(CodegenNaming.pageStem(pageName));
        }

        /** @deprecated use {@link #actionsClassName()} */
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
    }
}
