package delivery.codegen;

import delivery.authoring.LocalLlmClient;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CodeWriter {
    private final Path templateDir;
    private final CodegenOptions namingOptions;
    private final LocalLlmClient namingClient;

    public CodeWriter(Path templateDir) {
        this(templateDir, CodegenOptions.DEFAULT);
    }

    public CodeWriter(Path templateDir, CodegenOptions namingOptions) {
        this.templateDir = templateDir;
        this.namingOptions = namingOptions == null ? CodegenOptions.DEFAULT : namingOptions;
        if (this.namingOptions.ollamaNaming()
                && !this.namingOptions.llmBaseUrl().isBlank()
                && !this.namingOptions.llmModel().isBlank()) {
            this.namingClient = new LocalLlmClient(
                    this.namingOptions.llmBaseUrl(),
                    this.namingOptions.llmModel(),
                    Duration.ofSeconds(15),
                    64);
        } else {
            this.namingClient = null;
        }
    }

    public void write(Path projectRoot, List<TcOutcome> outcomes) throws Exception {
        List<TcOutcome> emitOutcomes = filterForEmit(outcomes);
        CodegenEmitGate.Prepared prepared = CodegenEmitGate.prepare(emitOutcomes);
        writeVerified(projectRoot, prepared.outcomes(), prepared.pages());
    }

    static List<TcOutcome> filterForEmit(List<TcOutcome> outcomes) {
        if (outcomes == null) {
            return List.of();
        }
        return outcomes.stream().map(CodegenEmitFilters::filterOutcome).toList();
    }

    public static PageAccumulator accumulate(List<TcOutcome> outcomes) {
        PageAccumulator pages = new PageAccumulator();
        List<TcOutcome> emitOutcomes = filterForEmit(outcomes);
        if (emitOutcomes.isEmpty()) {
            return pages;
        }
        for (TcOutcome outcome : emitOutcomes) {
            pages.addAll(outcome.provenSteps());
            if (outcome.needsLoginBeforeMethod() && outcome.loginSteps() != null) {
                pages.addAll(outcome.loginSteps());
            }
            if (outcome.setupSteps() != null) {
                pages.addAll(outcome.setupSteps());
            }
        }
        return pages;
    }

    /**
     * Writes after {@link CodegenSmellCheck#verify} — used by {@link #write} and tests.
     */
    void writeVerified(Path projectRoot, List<TcOutcome> outcomes, PageAccumulator pages)
            throws Exception {
        Configuration cfg = freemarkerConfig();
        Path pagesDir = projectRoot.resolve("src/main/java/project/pages");
        Path generatedDir = projectRoot.resolve("src/test/java/project/tests/generated");
        Path todoDir = projectRoot.resolve("src/test/java/project/tests/todo");
        Files.createDirectories(pagesDir);
        Files.createDirectories(generatedDir);
        Files.createDirectories(todoDir);
        java.util.Set<String> keepPages = new java.util.HashSet<>();
        java.util.Set<String> keepGenerated = new java.util.HashSet<>();
        java.util.Set<String> keepTodo = new java.util.HashSet<>();
        java.util.Set<String> usedPageFiles = new java.util.HashSet<>();

        Template pageTpl = cfg.getTemplate("Page.java.ftl");
        for (Map.Entry<String, PageAccumulator.PageModel> e : pages.pages().entrySet()) {
            PageAccumulator.PageModel page = e.getValue();
            Map<String, Object> model = new HashMapModel();
            model.put("stem", page.stem());
            model.put("pageClassName", page.pageClassName());
            model.put("className", page.pageClassName());
            // Compat aliases for any leftover template references
            model.put("locatorsClassName", page.pageClassName());
            model.put("actionsClassName", page.pageClassName());
            model.put("fields", page.fields());
            model.put("methods", page.methods());
            model.put("assertions", page.assertions());
            var loginHelper = LoginPageHelper.detect(page);
            if (loginHelper != null) {
                model.put("loginHelper", loginHelper);
            }

            String pageFile = page.pageClassName() + ".java";
            registerFile(usedPageFiles, pageFile, "page", e.getKey());
            Path pageOut = pagesDir.resolve(pageFile);
            try (Writer w = Files.newBufferedWriter(pageOut, StandardCharsets.UTF_8)) {
                pageTpl.process(model, w);
            }
            keepPages.add(pageFile);
        }

        Template genTpl = cfg.getTemplate("GeneratedTest.java.ftl");
        Template todoTpl = cfg.getTemplate("TodoTest.java.ftl");
        List<TcOutcome> safeOutcomes = outcomes == null ? List.of() : outcomes;
        String envPrefix = namingOptions.testDataEnvPrefix();
        CodegenDataKeys keys = CodegenDataKeys.assign(safeOutcomes, pages, envPrefix);

        LinkedHashMap<String, List<TcOutcome>> groups = new LinkedHashMap<>();
        for (TcOutcome outcome : safeOutcomes) {
            boolean passed = outcome.status() == TcStatus.PASSED;
            groups.computeIfAbsent(CodegenTestClasses.groupKey(outcome, passed), k -> new ArrayList<>())
                    .add(outcome);
        }

        for (Map.Entry<String, List<TcOutcome>> group : groups.entrySet()) {
            List<TcOutcome> members = group.getValue();
            TcOutcome first = members.get(0);
            boolean passed = first.status() == TcStatus.PASSED;
            String className = CodegenTestClasses.classNameFor(first, passed);
            Set<String> usedMethodNames = new HashSet<>();
            List<Map<String, Object>> testMethods = new ArrayList<>();
            List<Map<String, Object>> unionPageFields = new ArrayList<>();
            Map<String, Map<String, Object>> unionPageFieldIndex = new LinkedHashMap<>();
            List<String> unionImports = new ArrayList<>();
            Map<String, Boolean> importSet = new LinkedHashMap<>();

            boolean mergedClass = members.size() > 1;
            for (TcOutcome outcome : members) {
                Map<String, Object> tm = buildTestMethodModel(
                        outcome, pages, keys, usedMethodNames, mergedClass);
                testMethods.add(tm);
                for (Map<String, Object> pv : pageVarsFor(
                        castCalls(tm.get("chronCalls")),
                        castCalls(tm.get("beforeCalls")))) {
                    unionPageFieldIndex.putIfAbsent(String.valueOf(pv.get("className")), pv);
                }
                for (String imp : pageImportsFor(
                        castCalls(tm.get("chronCalls")), castCalls(tm.get("beforeCalls")))) {
                    importSet.putIfAbsent(imp, true);
                }
            }
            unionPageFields.addAll(unionPageFieldIndex.values());
            unionImports.addAll(importSet.keySet());

            boolean anyReadOnly = testMethods.stream()
                    .anyMatch(tm -> Boolean.TRUE.equals(tm.get("readOnly")));
            Map<String, Object> model = new HashMapModel();
            model.put("className", className);
            model.put("pageFields", unionPageFields);
            model.put("pageImports", unionImports);
            model.put("testMethods", testMethods);
            model.put("anyReadOnly", anyReadOnly);

            String testFile = className + ".java";
            Path out = (passed ? generatedDir : todoDir).resolve(testFile);
            Template tpl = passed ? genTpl : todoTpl;
            try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                tpl.process(model, w);
            }
            if (passed) {
                keepGenerated.add(testFile);
            } else {
                keepTodo.add(testFile);
            }
        }

        TestDataPropertiesWriter.write(projectRoot, safeOutcomes, pages, keys);
        deleteObsoleteJava(pagesDir, keepPages);
        deleteObsoleteJava(generatedDir, keepGenerated);
        deleteObsoleteJava(todoDir, keepTodo);
    }

    /**
     * A case is read-only (retry-eligible, see KeelReadOnly in the template) when its test body has
     * at least one call and every call in the body and the non-login setup is either an assertion or
     * an action step that prove marked {@code sideEffects:"none"}. Without the marker (absent ==
     * "unknown") an action is treated as mutating, so the rule falls back to "assertions only".
     * Login in @BeforeMethod is allowed because it re-runs on a fresh driver for every attempt.
     */
    static boolean isReadOnlyCase(List<Map<String, Object>> body, List<Map<String, Object>> setup) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        java.util.function.Predicate<Map<String, Object>> assertOnly = c -> "assert".equals(c.get("kind"))
                || ("action".equals(c.get("kind")) && ProvenStep.SIDE_EFFECTS_NONE.equals(c.get("sideEffects")));
        return body.stream().allMatch(assertOnly) && (setup == null || setup.stream().allMatch(assertOnly));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castCalls(Object o) {
        if (o instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private Map<String, Object> buildTestMethodModel(
            TcOutcome outcome,
            PageAccumulator pages,
            CodegenDataKeys keys,
            Set<String> usedMethodNames,
            boolean mergedClass) {
        String primaryPage = PageUnderTest.primaryPageName(outcome);
        String baseMethod = TestMethodNaming.resolveFromPage(
                primaryPage, outcome.tcId(), namingClient, namingOptions.ollamaNaming());
        String stem = mergedClass
                ? baseMethod + "_" + sanitizeMethod(outcome.tcId())
                : baseMethod;
        String methodName = allocateMethodName(usedMethodNames, stem, outcome.tcId());

        List<Map<String, Object>> loginChron = outcome.needsLoginBeforeMethod()
                ? buildChronologicalCalls(outcome.tcId(), outcome.loginSteps(), pages, keys,
                CodegenDataKeys.PHASE_LOGIN)
                : List.of();
        loginChron = LoginPageHelper.collapseLeadingLogin(loginChron, pages);
        List<Map<String, Object>> setupChron =
                buildChronologicalCalls(outcome.tcId(), outcome.setupSteps(), pages, keys,
                        CodegenDataKeys.PHASE_SETUP);
        setupChron = LoginPageHelper.collapseLeadingLogin(setupChron, pages);
        List<Map<String, Object>> chronCalls =
                buildChronologicalCalls(outcome.tcId(), outcome.provenSteps(), pages, keys,
                        CodegenDataKeys.PHASE_BODY);
        chronCalls = LoginPageHelper.collapseLeadingLogin(chronCalls, pages);
        List<Map<String, Object>> beforeCalls = new ArrayList<>();
        beforeCalls.addAll(loginChron);
        beforeCalls.addAll(setupChron);

        Map<String, Object> tm = new LinkedHashMap<>();
        tm.put("tcId", outcome.tcId());
        tm.put("testDescription", testDescription(outcome.tcId(), outcome.title()));
        tm.put("reason", CodegenTodoReason.summarize(outcome.failureReason(), 120));
        tm.put("methodName", methodName);
        tm.put("chronCalls", chronCalls);
        tm.put("pageFields", pageVarsFor(chronCalls, beforeCalls));
        tm.put("beforeCalls", beforeCalls);
        tm.put("beforePageVars", pageVarsFor(beforeCalls));
        tm.put("needsLoginBeforeMethod", outcome.needsLoginBeforeMethod());
        tm.put("prerequisiteMethod", beforeCalls.isEmpty()
                ? "" : "runPrerequisite_" + sanitizeMethod(outcome.tcId()));
        tm.put("reviewComments", List.of());
        tm.put("readOnly", isReadOnlyCase(chronCalls, setupChron));
        return tm;
    }

    static String allocateMethodName(Set<String> used, String base, String tcId) {
        String candidate = base == null || base.isBlank() ? "runCase" : base;
        if (used.add(candidate)) {
            return candidate;
        }
        String suffix = sanitizeMethod(tcId);
        candidate = base + "_" + suffix;
        if (used.add(candidate)) {
            return candidate;
        }
        int i = 2;
        while (!used.add(base + "_" + i)) {
            i++;
        }
        return base + "_" + i;
    }

    static String testDescription(String tcId, String title) {
        String id = tcId == null ? "" : tcId.trim();
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            return id;
        }
        if (id.isEmpty()) {
            return t;
        }
        return id + " — " + t;
    }

    List<Map<String, Object>> buildChronologicalCalls(
            String tcId, List<ProvenStep> steps, PageAccumulator pages, CodegenDataKeys keys, String phase) {
        List<Map<String, Object>> calls = new ArrayList<>();
        if (steps == null) {
            return calls;
        }
        for (ProvenStep step : steps) {
            String action = step.action() == null ? "" : step.action().toLowerCase(Locale.ROOT);
            boolean isAssert = step.assertionType() != null && !step.assertionType().isBlank();
            boolean isAction = "click".equals(action) || "type".equals(action) || "select".equals(action)
                    || "drag".equals(action) || "clear".equals(action) || "hover".equals(action)
                    || "doubleclick".equals(action) || "rightclick".equals(action);
            if (!isAssert && !isAction) {
                continue;
            }
            if (!isAssert && (step.locatorValue() == null || step.locatorValue().isBlank())) {
                continue;
            }
            if (isAssert && !isLocatorFreeAssert(step.assertionType())
                    && (step.locatorValue() == null || step.locatorValue().isBlank())) {
                continue;
            }
            String pageClass = pages.pageClassNameFor(step);
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("pageClass", pageClass);
            call.put("pageVar", pageVarName(pageClass));
            if (isAssert && (!isAction || "assert".equals(action))) {
                putAssertCall(call, pages, step);
            } else {
                String method = pages.actionSymbol(step);
                call.put("kind", "action");
                call.put("method", method);
                call.put("sideEffects", step.sideEffects());
                call.put("needsValue", "type".equals(action) || "select".equals(action));
                String value = step.value() == null ? "" : step.value();
                String propKey = keys.keyFor(tcId, phase, step);
                if (step.secret()) {
                    // IR secret: the value lives only in the secrets/data file, never as a Java literal.
                    if (propKey.isBlank() && !value.isEmpty()) {
                        throw new IllegalStateException("secret step " + step.tcId() + " got no property key");
                    }
                    value = "";
                    call.put("secret", true);
                }
                call.put("value", value);
                call.put("propKey", propKey);
            }
            calls.add(call);
            if (isAction && isAssert && !"assert".equals(action)) {
                Map<String, Object> assertCall = new LinkedHashMap<>();
                assertCall.put("pageClass", pageClass);
                assertCall.put("pageVar", pageVarName(pageClass));
                putAssertCall(assertCall, pages, step);
                calls.add(assertCall);
            }
        }
        return calls;
    }

    private static void putAssertCall(
            Map<String, Object> call, PageAccumulator pages, ProvenStep step) {
        boolean parameterized = PageAccumulator.isParameterizedAssertion(step.assertionType());
        call.put("kind", "assert");
        call.put("method", pages.assertSymbol(step));
        call.put("needsValue", parameterized);
        call.put("value", parameterized && step.assertionExpected() != null ? step.assertionExpected() : "");
        call.put("propKey", "");
    }

    static List<Map<String, Object>> pageVarsFor(List<Map<String, Object>> calls) {
        return pageVarsFor(calls, List.of());
    }

    static List<Map<String, Object>> pageVarsFor(List<Map<String, Object>> bodyCalls, List<Map<String, Object>> beforeCalls) {
        Map<String, Map<String, Object>> ordered = new LinkedHashMap<>();
        for (Map<String, Object> call : beforeCalls) {
            addPageVar(ordered, call);
        }
        for (Map<String, Object> call : bodyCalls) {
            addPageVar(ordered, call);
        }
        return new ArrayList<>(ordered.values());
    }

    private static void addPageVar(Map<String, Map<String, Object>> ordered, Map<String, Object> call) {
        String pageClass = String.valueOf(call.get("pageClass"));
        ordered.computeIfAbsent(pageClass, cls -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("className", cls);
            v.put("varName", pageVarName(cls));
            return v;
        });
    }

    private static String sanitizeMethod(String tcId) {
        String id = tcId == null ? "Tc" : tcId.replaceAll("[^A-Za-z0-9_]", "_");
        return CodegenNaming.sanitizeJavaIdentifier(id, "Tc");
    }

    static List<String> pageImportsFor(List<Map<String, Object>> bodyCalls, List<Map<String, Object>> loginCalls) {
        Map<String, Boolean> ordered = new LinkedHashMap<>();
        for (Map<String, Object> call : loginCalls) {
            ordered.put(String.valueOf(call.get("pageClass")), true);
        }
        for (Map<String, Object> call : bodyCalls) {
            ordered.put(String.valueOf(call.get("pageClass")), true);
        }
        return new ArrayList<>(ordered.keySet());
    }

    static String pageVarName(String pageClass) {
        return CodegenNaming.safeLocalVarName(pageClass);
    }

    static boolean isLocatorFreeAssert(String assertionType) {
        if (assertionType == null || assertionType.isBlank()) {
            return false;
        }
        String type = assertionType.trim();
        return "urlContains".equalsIgnoreCase(type)
                || "textContains".equalsIgnoreCase(type);
    }

    /**
     * Map typed values to property keys. Login placeholders stay TARGET_*;
     * invented / Excel form values go to delivery-testdata.properties.
     */
    static String specialPropKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if ("${TARGET_USERNAME}".equals(value)) {
            return "TARGET_USERNAME";
        }
        if ("${TARGET_PASSWORD}".equals(value)) {
            return "TARGET_PASSWORD";
        }
        if (value.startsWith("${") && value.endsWith("}")) {
            return value.substring(2, value.length() - 1);
        }
        return null;
    }

    static String propKeyFor(String tcId, String methodName, String value) {
        return CodegenDataKeys.assign(List.of(), new PageAccumulator()).next(tcId, methodName, value);
    }

    private static void registerFile(java.util.Set<String> usedLower, String fileName, String kind, String source) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!usedLower.add(lower)) {
            throw new IllegalStateException(
                    "Generated " + kind + " file collision for " + source + " -> " + fileName);
        }
    }

    private Configuration freemarkerConfig() throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
        cfg.setDirectoryForTemplateLoading(templateDir.toFile());
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }

    public static String toClassName(String tcId) {
        return CodegenNaming.tcIdToClassName(tcId);
    }

    private static void deleteObsoleteJava(Path dir, java.util.Set<String> keepFileNames) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        java.util.Set<String> keep = keepFileNames == null ? java.util.Set.of() : keepFileNames;
        try (var stream = Files.list(dir)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".java") && !keep.contains(name)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static final class HashMapModel extends LinkedHashMap<String, Object> {
    }
}
