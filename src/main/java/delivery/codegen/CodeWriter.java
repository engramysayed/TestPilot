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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        java.util.Set<String> usedTestFiles = new java.util.HashSet<>();

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
        for (TcOutcome outcome : safeOutcomes) {
            boolean passed = outcome.status() == TcStatus.PASSED;
            String className = resolveTestClassName(usedTestFiles, outcome, passed);
            String methodName = TestMethodNaming.resolve(
                    outcome.title(), outcome.tcId(), namingClient, namingOptions.ollamaNaming());
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
            Map<String, Object> model = new HashMapModel();
            model.put("className", className);
            model.put("tcId", outcome.tcId());
            model.put("title", outcome.title() == null ? "" : outcome.title());
            model.put("testDescription", testDescription(outcome.tcId(), outcome.title()));
            model.put("reason", CodegenTodoReason.summarize(outcome.failureReason(), 120));
            model.put("methodName", methodName);
            model.put("steps", outcome.provenSteps());
            model.put("chronCalls", chronCalls);
            List<Map<String, Object>> allPageVars = pageVarsFor(chronCalls, beforeCalls);
            model.put("pageFields", allPageVars);
            model.put("pageVars", pageVarsFor(chronCalls));
            model.put("loginChronCalls", loginChron);
            model.put("setupChronCalls", setupChron);
            model.put("beforeCalls", beforeCalls);
            model.put("beforePageVars", pageVarsFor(beforeCalls));
            model.put("pageImports", pageImportsFor(chronCalls, beforeCalls));
            model.put("prerequisiteMethod", beforeCalls.isEmpty() ? "" : "runPrerequisite_" + sanitizeMethod(outcome.tcId()));
            model.put("needsLoginBeforeMethod", outcome.needsLoginBeforeMethod());
            model.put("reviewComments", List.of());
            model.put("readOnly", isReadOnlyCase(chronCalls, setupChron));
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
        if (keepGenerated.size() + keepTodo.size() != safeOutcomes.size()) {
            throw new IllegalStateException(
                    "emitted " + (keepGenerated.size() + keepTodo.size())
                            + " test classes for " + safeOutcomes.size() + " cases");
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

    private static String resolveTestClassName(
            java.util.Set<String> usedTestFiles, TcOutcome outcome, boolean passed) {
        String fromTitle = CodegenNaming.testClassName(outcome.tcId(), outcome.title(), passed);
        String testFile = fromTitle + ".java";
        String lower = testFile.toLowerCase(Locale.ROOT);
        if (usedTestFiles.contains(lower)) {
            fromTitle = CodegenNaming.testClassName(outcome.tcId(), "", passed);
            testFile = fromTitle + ".java";
        }
        registerFile(usedTestFiles, testFile, "test class", outcome.tcId());
        return fromTitle;
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
