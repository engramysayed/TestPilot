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
        PageAccumulator pages = accumulate(outcomes);
        // Fail closed before any FreeMarker write — avoid half-written Actions/Locators.
        CodegenSmellCheck.verify(pages.pages());
        writeVerified(projectRoot, outcomes, pages);
    }

    static PageAccumulator accumulate(List<TcOutcome> outcomes) {
        PageAccumulator pages = new PageAccumulator();
        if (outcomes == null) {
            return pages;
        }
        for (TcOutcome outcome : outcomes) {
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

        Template locatorsTpl = cfg.getTemplate("PageLocators.java.ftl");
        Template actionsTpl = cfg.getTemplate("PageActions.java.ftl");
        for (Map.Entry<String, PageAccumulator.PageModel> e : pages.pages().entrySet()) {
            PageAccumulator.PageModel page = e.getValue();
            Map<String, Object> model = new HashMapModel();
            model.put("stem", page.stem());
            model.put("locatorsClassName", page.locatorsClassName());
            model.put("actionsClassName", page.actionsClassName());
            model.put("className", page.actionsClassName());
            model.put("fields", page.fields());
            model.put("methods", page.methods());
            model.put("assertions", page.assertions());

            String locatorsFile = page.locatorsClassName() + ".java";
            String actionsFile = page.actionsClassName() + ".java";
            registerFile(usedPageFiles, locatorsFile, "page locators", e.getKey());
            registerFile(usedPageFiles, actionsFile, "page actions", e.getKey());
            Path locatorsOut = pagesDir.resolve(locatorsFile);
            try (Writer w = Files.newBufferedWriter(locatorsOut, StandardCharsets.UTF_8)) {
                locatorsTpl.process(model, w);
            }
            keepPages.add(locatorsFile);
            Path actionsOut = pagesDir.resolve(actionsFile);
            try (Writer w = Files.newBufferedWriter(actionsOut, StandardCharsets.UTF_8)) {
                actionsTpl.process(model, w);
            }
            keepPages.add(actionsFile);
        }

        Template genTpl = cfg.getTemplate("GeneratedTest.java.ftl");
        Template todoTpl = cfg.getTemplate("TodoTest.java.ftl");
        List<TcOutcome> safeOutcomes = outcomes == null ? List.of() : outcomes;
        CodegenDataKeys keys = CodegenDataKeys.assign(safeOutcomes, pages);
        for (TcOutcome outcome : safeOutcomes) {
            boolean passed = outcome.status() == TcStatus.PASSED;
            String className = CodegenNaming.testClassName(outcome.tcId(), passed);
            String methodName = TestMethodNaming.resolve(
                    outcome.title(), outcome.tcId(), namingClient, namingOptions.ollamaNaming());
            List<Map<String, Object>> loginChron = outcome.needsLoginBeforeMethod()
                    ? buildChronologicalCalls(outcome.tcId(), outcome.loginSteps(), pages, keys,
                    CodegenDataKeys.PHASE_LOGIN)
                    : List.of();
            List<Map<String, Object>> setupChron =
                    buildChronologicalCalls(outcome.tcId(), outcome.setupSteps(), pages, keys,
                            CodegenDataKeys.PHASE_SETUP);
            List<Map<String, Object>> chronCalls =
                    buildChronologicalCalls(outcome.tcId(), outcome.provenSteps(), pages, keys,
                            CodegenDataKeys.PHASE_BODY);
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
            model.put("pageVars", pageVarsFor(chronCalls));
            model.put("loginChronCalls", loginChron);
            model.put("loginPageVars", pageVarsFor(loginChron));
            model.put("setupChronCalls", setupChron);
            model.put("beforePageVars", pageVarsFor(beforeCalls));
            model.put("pageImports", pageImportsFor(chronCalls, beforeCalls));
            model.put("needsLoginBeforeMethod", outcome.needsLoginBeforeMethod());
            model.put("reviewComments", List.of());
            String testFile = className + ".java";
            registerFile(usedTestFiles, testFile, "test class", outcome.tcId());
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
            boolean isAction = "click".equals(action) || "type".equals(action) || "select".equals(action);
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
            String pageClass = pages.actionsClassName(step);
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("pageClass", pageClass);
            call.put("pageVar", pageVarName(pageClass));
            if (isAssert && (!isAction || "assert".equals(action))) {
                putAssertCall(call, pages, step);
            } else {
                String method = pages.actionSymbol(step);
                call.put("kind", "action");
                call.put("method", method);
                call.put("needsValue", "type".equals(action) || "select".equals(action));
                String value = step.value() == null ? "" : step.value();
                call.put("value", value);
                call.put("propKey", keys.keyFor(tcId, phase, step));
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
        Map<String, Map<String, Object>> ordered = new LinkedHashMap<>();
        for (Map<String, Object> call : calls) {
            String pageClass = String.valueOf(call.get("pageClass"));
            ordered.computeIfAbsent(pageClass, cls -> {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("className", cls);
                v.put("varName", pageVarName(cls));
                return v;
            });
        }
        return new ArrayList<>(ordered.values());
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
