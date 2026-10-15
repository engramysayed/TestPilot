package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileInput;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobileStep;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic mobile framework writer. Consumes AE {@link MobileCaseIr} only —
 * never ProvenStepCodec / web IR.
 * <p>M4-03: owner+phase testdata, secret-ref path, auth/setup hooks, capture/compare emit.
 * <p>M4-08: chron binds by step identity (never prefix-first/get(0)); SCROLL_UNTIL/SWIPE emit real gestures.
 */
public final class MobileCodeWriter {

    private final Path templateDir;
    private final Set<String> loginTcIds;

    public MobileCodeWriter(Path templateDir) {
        this(templateDir, Set.of());
    }

    public MobileCodeWriter(Path templateDir, Set<String> loginTcIds) {
        this.templateDir = templateDir;
        this.loginTcIds = loginTcIds == null ? Set.of() : Set.copyOf(loginTcIds);
    }

    /** Classpath templates under {@code templates/} when templateDir is null. */
    public MobileCodeWriter() {
        this(null, Set.of());
    }

    public MobileCodeWriter(Set<String> loginTcIds) {
        this(null, loginTcIds);
    }

    public void writeVerified(Path outputRoot, List<MobileCaseIr> cases, NamingMap names) throws Exception {
        NamingMap naming = names == null ? NamingMap.empty() : names;
        List<MobileCaseIr> all = cases == null ? List.of() : cases;
        Map<String, MobileCaseIr> byId = indexByTcId(all);

        List<MobileCodegenEmitGate.CaseEmitPlan> plans = MobileCodegenEmitGate.prepare(all);

        List<MobileCaseIr> forScreens = new ArrayList<>();
        for (MobileCodegenEmitGate.CaseEmitPlan plan : plans) {
            if (!plan.todo()) {
                forScreens.add(plan.original());
                // Also accumulate screens from setup refs (per-leaf independence still emits own calls)
                for (String ref : plan.original().setupRefs()) {
                    MobileCaseIr setup = byId.get(ref);
                    if (setup != null) {
                        forScreens.add(setup);
                    }
                }
            }
        }
        ScreenAccumulator screens = new ScreenAccumulator(naming);
        screens.addAll(forScreens);
        MobileCodegenSmellCheck.verify(screens.screens());

        // Testdata keys — assign across all cases first (login vs body namespaces)
        MobileTestdataKeys dataKeys = MobileTestdataKeys.assign(all, loginTcIds);

        Path screensDir = outputRoot.resolve("src/test/java/project/screens");
        Path generatedDir = outputRoot.resolve("src/test/java/project/tests/generated");
        Path todoDir = outputRoot.resolve("src/test/java/project/tests/todo");
        Files.createDirectories(screensDir);
        Files.createDirectories(generatedDir);
        Files.createDirectories(todoDir);

        Configuration cfg = freemarkerConfig();
        Template screenTpl = cfg.getTemplate("Screen.java.ftl");
        Template genTpl = cfg.getTemplate("GeneratedMobileTest.java.ftl");
        Template todoTpl = cfg.getTemplate("TodoMobileTest.java.ftl");

        Set<String> keepScreens = new HashSet<>();
        Set<String> keepGenerated = new HashSet<>();
        Set<String> keepTodo = new HashSet<>();

        for (ScreenAccumulator.ScreenModel screen : screens.screens().values()) {
            Map<String, Object> model = screenModel(screen);
            String file = screen.screenClassName() + ".java";
            Path out = screensDir.resolve(file);
            String rendered = render(screenTpl, model);
            MobileCodegenSmellCheck.verifyEmittedSource(file, rendered);
            Files.writeString(out, rendered, StandardCharsets.UTF_8);
            keepScreens.add(file);
        }

        for (MobileCodegenEmitGate.CaseEmitPlan plan : plans) {
            MobileCodegenEmitGate.CaseEmitPlan effective = plan;
            if (!plan.todo()) {
                String demoteReason = MobileCodegenEmitGate.scrollUntilMissingTargetReason(plan.original());
                if (demoteReason == null) {
                    demoteReason = unresolvedIdentityReason(plan, screens, byId);
                }
                if (demoteReason != null) {
                    effective = MobileCodegenEmitGate.CaseEmitPlan.todo(plan.original(), demoteReason);
                }
            }
            if (effective.todo()) {
                Map<String, Object> model = todoModel(effective);
                String className = MobileCodegenNaming.testClassName(effective.tcId(), false);
                String file = className + ".java";
                Path out = todoDir.resolve(file);
                String rendered = render(todoTpl, model);
                MobileCodegenSmellCheck.verifyEmittedSource(file, rendered);
                Files.writeString(out, rendered, StandardCharsets.UTF_8);
                keepTodo.add(file);
            } else {
                Map<String, Object> model = generatedModel(effective, screens, byId, dataKeys);
                String className = MobileCodegenNaming.testClassName(effective.tcId(), true);
                String file = className + ".java";
                Path out = generatedDir.resolve(file);
                String rendered = render(genTpl, model);
                MobileCodegenSmellCheck.verifyEmittedSource(file, rendered);
                // Guard: no raw secret literals from SECRET_REF values
                assertNoSecretLeaks(file, rendered, effective);
                Files.writeString(out, rendered, StandardCharsets.UTF_8);
                keepGenerated.add(file);
            }
        }

        deleteObsolete(screensDir, keepScreens);
        deleteObsolete(generatedDir, keepGenerated);
        deleteObsolete(todoDir, keepTodo);

        MobileTestdataWriter.write(outputRoot, dataKeys);
        // M4-07 package docs (README / CI example / portal stub) — best-effort
        try {
            MobilePackageDocsWriter.writeAll(outputRoot);
        } catch (Exception ignored) {
        }
        // M4-05 package-manifest fingerprint placeholder fields (filled by packager/publish)
        try {
            Path docs = outputRoot.resolve("docs");
            Files.createDirectories(docs);
            Files.writeString(docs.resolve("package-manifest.json"),
                    "{\n  \"schema\": \"mobile-package-manifest/1\",\n  \"reuseShortcut\": \"NONE\"\n}\n");
        } catch (Exception ignored) {
        }

        Path nameMapPath = outputRoot.resolve("docs/name-map.json");
        naming.save(nameMapPath);
    }

    private static Map<String, MobileCaseIr> indexByTcId(List<MobileCaseIr> cases) {
        Map<String, MobileCaseIr> map = new HashMap<>();
        for (MobileCaseIr c : cases) {
            if (c != null && c.tcId() != null) {
                map.put(c.tcId(), c);
            }
        }
        return map;
    }

    private static void assertNoSecretLeaks(String file, String rendered, MobileCodegenEmitGate.CaseEmitPlan plan) {
        for (MobileStep step : plan.emitSteps()) {
            if (step == null || step.input() == null) {
                continue;
            }
            MobileInput in = step.input();
            if (MobileCodeSecrets.isSecretKind(in.kind()) && in.value() != null && !in.value().isBlank()) {
                if (rendered.contains(in.value())) {
                    throw new IllegalStateException(
                            "Secret value leaked into emitted source " + file + " (use secret-ref key only)");
                }
            }
        }
    }

    private static Map<String, Object> screenModel(ScreenAccumulator.ScreenModel screen) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("packageName", "project.screens");
        model.put("screenClassName", screen.screenClassName());
        List<Map<String, Object>> locs = new ArrayList<>();
        for (ScreenAccumulator.LocatorModel loc : screen.locators()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldName", loc.fieldName());
            m.put("byExpression", loc.byExpression());
            locs.add(m);
        }
        model.put("locators", locs);
        List<Map<String, Object>> actions = new ArrayList<>();
        for (ScreenAccumulator.ActionModel a : screen.actions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("methodName", a.methodName());
            m.put("utilMethod", a.utilMethod());
            m.put("locatorField", a.locatorField());
            m.put("hasArg", a.hasArg());
            m.put("argType", a.argType());
            m.put("argName", a.argName());
            m.put("settleKind", a.settleKind());
            m.put("passSettleKind", a.passSettleKind());
            m.put("secretRef", a.secretRef());
            actions.add(m);
        }
        model.put("actions", actions);
        List<Map<String, Object>> asserts = new ArrayList<>();
        for (ScreenAccumulator.AssertModel a : screen.asserts()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("methodName", a.methodName());
            m.put("utilMethod", a.utilMethod());
            m.put("locatorField", a.locatorField());
            m.put("hasArg", a.hasArg());
            m.put("argType", a.argType());
            m.put("argName", a.argName());
            asserts.add(m);
        }
        model.put("asserts", asserts);
        List<Map<String, Object>> captures = new ArrayList<>();
        for (ScreenAccumulator.CaptureModel c : screen.captures()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("methodName", c.methodName());
            m.put("utilMethod", c.utilMethod());
            m.put("locatorField", c.locatorField());
            m.put("captureVar", c.captureVar());
            m.put("compare", c.compare());
            m.put("compareMode", c.compareMode() == null ? "EQUALS" : c.compareMode());
            captures.add(m);
        }
        model.put("captures", captures);
        return model;
    }

    private Map<String, Object> generatedModel(
            MobileCodegenEmitGate.CaseEmitPlan plan,
            ScreenAccumulator screens,
            Map<String, MobileCaseIr> byId,
            MobileTestdataKeys dataKeys) {
        Map<String, Object> model = new LinkedHashMap<>();
        String tcId = MobileCodegenNaming.testClassName(plan.tcId(), true);
        model.put("tcId", tcId);
        model.put("testName", plan.original().tcId());
        LinkedHashSet<String> imports = new LinkedHashSet<>();
        List<Map<String, String>> screenVars = new ArrayList<>();
        List<String> setupCalls = new ArrayList<>();
        List<String> bodyCalls = new ArrayList<>();
        Set<String> seenVars = new HashSet<>();

        // Per-leaf setup independence: each leaf inlines its own setupRefs calls
        for (String ref : plan.original().setupRefs()) {
            MobileCaseIr setupCase = byId.get(ref);
            if (setupCase == null || setupCase.steps() == null) {
                continue;
            }
            String setupPhase = loginTcIds.contains(ref)
                    ? MobileTestdataKeys.PHASE_LOGIN
                    : MobileTestdataKeys.PHASE_SETUP;
            for (MobileStep step : setupCase.steps()) {
                appendCall(step, setupCase.tcId(), setupPhase, screens, dataKeys,
                        imports, screenVars, seenVars, setupCalls);
            }
        }

        String bodyPhase = loginTcIds.contains(plan.tcId())
                ? MobileTestdataKeys.PHASE_LOGIN
                : MobileTestdataKeys.PHASE_BODY;
        for (MobileStep step : plan.emitSteps()) {
            appendCall(step, plan.tcId(), bodyPhase, screens, dataKeys,
                    imports, screenVars, seenVars, bodyCalls);
        }

        // Auth hook: property-driven; BaseMobileTest.authSetupIfConfigured() always runs in @BeforeMethod.
        // Generated tests optionally declare profile revision ref as a comment / marker property key.
        boolean authConfigured = !plan.original().setupRefs().isEmpty()
                || loginTcIds.contains(plan.tcId());
        model.put("authSetupComment", authConfigured
                ? "// authSetupIfConfigured() runs in BaseMobileTest.@BeforeMethod (profile/mode refs only)"
                : "// authSetupIfConfigured() no-op unless mobile.auth.* properties set");
        model.put("screenImports", new ArrayList<>(imports));
        model.put("screenVars", screenVars);
        model.put("setupCalls", setupCalls);
        model.put("bodyCalls", bodyCalls);
        model.put("readOnly", false);
        return model;
    }

    private static void appendCall(
            MobileStep step,
            String ownerTcId,
            String phase,
            ScreenAccumulator screens,
            MobileTestdataKeys dataKeys,
            LinkedHashSet<String> imports,
            List<Map<String, String>> screenVars,
            Set<String> seenVars,
            List<String> calls) {
        if (step == null) {
            return;
        }
        // Driver-level ops without screen
        if (step.operation() == MobileOperation.PRESS_BACK
                || step.operation() == MobileOperation.HIDE_KEYBOARD
                || step.operation() == MobileOperation.WAIT
                || step.operation() == MobileOperation.SWITCH_CONTEXT) {
            String call = driverLevelCall(step);
            if (call != null) {
                calls.add(call);
            }
            return;
        }
        if (step.screenKey() == null) {
            return;
        }
        ScreenAccumulator.ScreenModel sm = screens.screens().get(step.screenKey());
        if (sm == null) {
            return;
        }
        String type = sm.screenClassName();
        String var = MobileCodegenNaming.safeLocalVarName(type);
        imports.add("project.screens." + type);
        if (seenVars.add(var)) {
            Map<String, String> v = new LinkedHashMap<>();
            v.put("type", type);
            v.put("name", var);
            screenVars.add(v);
        }
        String call = chronCall(var, screens, sm, step, ownerTcId, phase, dataKeys);
        if (call != null) {
            calls.add(call);
        }
    }

    private static String driverLevelCall(MobileStep step) {
        return switch (step.operation()) {
            case PRESS_BACK -> "project.runtime.gestures.MobileGestures.pressBack(getAndroidDriver())";
            case HIDE_KEYBOARD -> "project.runtime.keyboard.HideKeyboard.hide(getAndroidDriver())";
            case SWITCH_CONTEXT -> MobileContextEmit.switchCall(step.context());
            default -> null;
        };
    }

    private static String chronCall(
            String var,
            ScreenAccumulator screens,
            ScreenAccumulator.ScreenModel sm,
            MobileStep step,
            String ownerTcId,
            String phase,
            MobileTestdataKeys dataKeys) {
        return switch (step.operation()) {
            case TAP -> {
                String m = screens.actionSymbol(step);
                yield m == null ? null : var + "." + m + "()";
            }
            case TYPE -> {
                String m = screens.actionSymbol(step);
                if (m == null) {
                    yield null;
                }
                MobileInput input = step.input();
                if (input != null && MobileCodeSecrets.isSecretKind(input.kind())) {
                    String ref = input.key() == null ? "" : input.key().trim();
                    // Pass logical key only — typeSecret resolves via SecretResolver; never raw value
                    yield var + "." + m + "(" + MobileCodegenNaming.javaStringLiteral(ref) + ")";
                }
                String propKey = dataKeys.keyFor(ownerTcId, phase, step);
                if (propKey == null || propKey.isBlank()) {
                    yield var + "." + m + "(\"\")";
                }
                yield var + "." + m + "(requiredProperty("
                        + MobileCodegenNaming.javaStringLiteral(propKey) + "))";
            }
            case CLEAR -> {
                String m = screens.actionSymbol(step);
                yield m == null ? null : var + "." + m + "()";
            }
            case LONG_PRESS -> {
                String m = screens.actionSymbol(step);
                yield m == null ? null : var + "." + m + "()";
            }
            case SCROLL_UNTIL -> {
                String m = screens.actionSymbol(step);
                if (m == null || step.target() == null) {
                    yield null;
                }
                String dir = step.scrollDirection() == null || step.scrollDirection().isBlank()
                        ? "DOWN" : step.scrollDirection().trim();
                int max = step.maxSwipes() == null || step.maxSwipes() <= 0 ? 5 : step.maxSwipes();
                yield var + "." + m + "(" + MobileCodegenNaming.javaStringLiteral(dir) + ", " + max + ")";
            }
            case SWIPE -> {
                String m = screens.actionSymbol(step);
                if (m == null) {
                    yield null;
                }
                String dir = step.scrollDirection() == null || step.scrollDirection().isBlank()
                        ? "UP" : step.scrollDirection().trim();
                yield var + "." + m + "(" + MobileCodegenNaming.javaStringLiteral(dir) + ")";
            }
            case ASSERT -> {
                String m = screens.assertSymbol(step);
                if (m == null) {
                    yield null;
                }
                boolean textAssert = step.assertType() != null
                        && step.assertType().toLowerCase().contains("text");
                if (textAssert) {
                    String propKey = dataKeys.keyFor(ownerTcId, phase, step);
                    if (propKey != null && !propKey.isBlank()) {
                        yield var + "." + m + "(requiredProperty("
                                + MobileCodegenNaming.javaStringLiteral(propKey) + "))";
                    }
                    // Prove semantics: expected from input literal if present
                    if (step.input() != null && step.input().value() != null) {
                        yield var + "." + m + "("
                                + MobileCodegenNaming.javaStringLiteral(step.input().value()) + ")";
                    }
                    yield var + "." + m + "(requiredProperty(\"expected\"))";
                }
                yield var + "." + m + "()";
            }
            case CAPTURE_TEXT -> {
                String m = findCapture(sm, step, false);
                if (m == null) {
                    yield null;
                }
                String capVar = step.captureVar() == null ? "captured" : step.captureVar();
                yield "project.runtime.capture.CapturedValues.put("
                        + MobileCodegenNaming.javaStringLiteral(capVar) + ", "
                        + var + "." + m + "())";
            }
            case COMPARE_CAPTURED -> {
                String m = findCapture(sm, step, true);
                yield m == null ? null : var + "." + m + "()";
            }
            default -> null;
        };
    }

    /**
     * Fail closed: if a screen-bound step has no identity-mapped method, demote rather than
     * silently binding the first prefix match (M4-08 / live P0).
     */
    private static String unresolvedIdentityReason(
            MobileCodegenEmitGate.CaseEmitPlan plan,
            ScreenAccumulator screens,
            Map<String, MobileCaseIr> byId) {
        List<MobileStep> toCheck = new ArrayList<>();
        for (String ref : plan.original().setupRefs()) {
            MobileCaseIr setup = byId.get(ref);
            if (setup != null && setup.steps() != null) {
                toCheck.addAll(setup.steps());
            }
        }
        if (plan.emitSteps() != null) {
            toCheck.addAll(plan.emitSteps());
        }
        for (MobileStep step : toCheck) {
            if (step == null || step.operation() == null) {
                continue;
            }
            switch (step.operation()) {
                case PRESS_BACK, HIDE_KEYBOARD, WAIT, SWITCH_CONTEXT -> {
                    continue;
                }
                default -> {
                }
            }
            if (step.screenKey() == null || step.screenKey().isBlank()) {
                continue;
            }
            ScreenAccumulator.ScreenModel sm = screens.screens().get(step.screenKey());
            if (sm == null) {
                return MobileCodegenEmitGate.IDENTITY_DEMOTE_REASON + " (missing screen " + step.screenKey() + ")";
            }
            switch (step.operation()) {
                case TAP, TYPE, CLEAR, LONG_PRESS, SCROLL_UNTIL, SWIPE -> {
                    if (screens.actionSymbol(step) == null) {
                        return MobileCodegenEmitGate.IDENTITY_DEMOTE_REASON
                                + " (action stepId=" + step.stepId() + ")";
                    }
                }
                case ASSERT -> {
                    if (screens.assertSymbol(step) == null) {
                        return MobileCodegenEmitGate.IDENTITY_DEMOTE_REASON
                                + " (assert stepId=" + step.stepId() + ")";
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static String findCapture(ScreenAccumulator.ScreenModel sm, MobileStep step, boolean compare) {
        for (ScreenAccumulator.CaptureModel c : sm.captures()) {
            if (c.compare() == compare
                    && step.captureVar() != null
                    && step.captureVar().equals(c.captureVar())) {
                return c.methodName();
            }
        }
        for (ScreenAccumulator.CaptureModel c : sm.captures()) {
            if (c.compare() == compare) {
                return c.methodName();
            }
        }
        return null;
    }

    private static Map<String, Object> todoModel(MobileCodegenEmitGate.CaseEmitPlan plan) {
        Map<String, Object> model = new LinkedHashMap<>();
        String className = MobileCodegenNaming.testClassName(plan.tcId(), false);
        model.put("tcId", className);
        model.put("testName", plan.original().tcId());
        model.put("reason", plan.reason() == null ? "TODO" : plan.reason());
        model.put("screenImports", List.of());
        model.put("screenVars", List.of());
        model.put("setupCalls", List.of());
        model.put("bodyCalls", List.of());
        model.put("authSetupComment", "// TODO case — auth hook skipped");
        return model;
    }

    private static String render(Template tpl, Map<String, Object> model) throws Exception {
        StringWriter sw = new StringWriter();
        tpl.process(model, sw);
        return sw.toString();
    }

    private Configuration freemarkerConfig() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        if (templateDir != null) {
            cfg.setDirectoryForTemplateLoading(templateDir.toFile());
        } else {
            cfg.setClassLoaderForTemplateLoading(MobileCodeWriter.class.getClassLoader(), "templates");
        }
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        return cfg;
    }

    private static void deleteObsolete(Path dir, Set<String> keep) throws Exception {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".java")).forEach(p -> {
                if (!keep.contains(p.getFileName().toString())) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }
}
