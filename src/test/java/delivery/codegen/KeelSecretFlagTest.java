package delivery.codegen;

import delivery.ir.ProvenStepCodec;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import delivery.packager.FrameworkPackager;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/** Codegen r1/07: IR {@code secret: true} on type steps (contract in patches/r1/PLAN.md section 7.2). */
public class KeelSecretFlagTest {
    static final String SECRET = "s3cr3t-Pa55!";

    static List<LocatorCandidate> cands(String main, String fallback) {
        return List.of(new LocatorCandidate("keel", main, 0.9), new LocatorCandidate("keel", fallback, 0.6));
    }

    static ProvenStep user() {
        return new ProvenStep("TC_S", "Login", "elementAction", "type", "keel", "css=#user", "alice", "", "", true, "ok", "",
                cands("css=#user", "css=[name=user]"));
    }

    static ProvenStep pass() {
        return new ProvenStep("TC_S", "Login", "elementAction", "type", "keel", "css=#pass", SECRET, "", "", true, "ok", "",
                cands("css=#pass", "css=[name=pass]")).withSecret(true);
    }

    static ProvenStep submit() {
        return new ProvenStep("TC_S", "Login", "elementAction", "click", "keel", "css=#go", "", "", "", true, "ok");
    }

    @Test
    public void codecWritesSecretOnlyWhenTrueAndRoundTrips() {
        JSONObject o = ProvenStepCodec.toJson(pass());
        Assert.assertTrue(o.getBoolean("secret"));
        Assert.assertTrue(ProvenStepCodec.fromJson(o).secret());
        Assert.assertEquals(ProvenStepCodec.fromJson(o), pass());
        Assert.assertFalse(ProvenStepCodec.toJson(user()).has("secret"), "absent == false: IR stays byte-identical");
        JSONObject explicitFalse = ProvenStepCodec.toJson(user()).put("secret", false);
        Assert.assertFalse(ProvenStepCodec.fromJson(explicitFalse).secret());
        JSONObject nul = ProvenStepCodec.toJson(user()).put("secret", JSONObject.NULL);
        Assert.assertFalse(ProvenStepCodec.fromJson(nul).secret());
    }

    @Test
    public void codecRejectsEveryNonBooleanType() {
        for (Object bad : new Object[]{"true", 1, 0, new JSONObject(), new org.json.JSONArray()}) {
            IllegalArgumentException e = Assert.expectThrows(IllegalArgumentException.class,
                    () -> ProvenStepCodec.fromJson(ProvenStepCodec.toJson(user()).put("secret", bad)));
            Assert.assertTrue(e.getMessage().contains("'secret' must be a boolean"), e.getMessage());
        }
        Assert.assertEquals(CodegenSecrets.displayValue(pass()), "\u2022\u2022\u2022");
        Assert.assertEquals(CodegenSecrets.displayValue(user()), "alice");
        Assert.assertFalse(pass().toString().contains(SECRET), "toString never renders a secret value");
        Assert.assertTrue(pass().toString().contains("value=\u2022\u2022\u2022"), pass().toString());
    }

    /** r2 SPEC: allowed on any action; only type/clear switch to typeSecret; independent of sideEffects. */
    @Test
    public void settleKindAndSideEffectsIndependence() {
        Assert.assertTrue(ProvenStepCodec.fromJson(ProvenStepCodec.toJson(submit()).put("secret", true)).secret());
        Assert.assertEquals(CodegenSecrets.settleKind("type", true), "typeSecret");
        Assert.assertEquals(CodegenSecrets.settleKind("clear", true), "typeSecret");
        Assert.assertEquals(CodegenSecrets.settleKind("click", true), "click");
        Assert.assertEquals(CodegenSecrets.settleKind("type", false), "type");
        for (String se : new String[]{"none", "mutating", "unknown"}) {
            ProvenStep s = ProvenStepCodec.fromJson(ProvenStepCodec.toJson(pass().withSideEffects(se)));
            Assert.assertTrue(s.secret(), se);
            Assert.assertEquals(s.sideEffects(), se);
        }
        Assert.assertEquals(pass().sideEffects(), "unknown", "typing a password stays unknown until the submit");
        Assert.assertTrue(pass().withSideEffects("none").withDropCandidates(List.of()).secret(), "with* copies keep the flag");
    }

    @Test
    public void codecOutputValidatesAgainstSchema() throws Exception {
        Assert.assertEquals(ContractSchemaTest.validate("proven-step-ir", ProvenStepCodec.toJson(pass()).toString()), java.util.Set.of());
    }

    private static Path generate() throws Exception {
        Path out = Files.createTempDirectory("secret");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(out, List.of(
                new TcOutcome("TC_S", "Login", TcStatus.PASSED, List.of(user(), pass(), submit()), "", null, false, List.of())));
        return out;
    }

    private static String all(Path out, String suffix) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> s = Files.walk(out)) {
            for (Path f : s.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(suffix)).toList()) {
                sb.append(Files.readString(f)).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    public void codegenUsesTypeSecretRuleAndKeepsValueOutOfCode() throws Exception {
        Path out = generate();
        String java = all(out, ".java");
        Assert.assertFalse(java.contains(SECRET), "secret value must never be a Java literal");
        Assert.assertTrue(java.contains("\"typeSecret\"") && java.contains("SettledActions.type"), java);
        Assert.assertTrue(java.contains("Secrets.register(value)"), java);
        Assert.assertTrue(java.contains("SettledActions.type") && java.contains("\"type\""), "non-secret type keeps the type rule");
        Assert.assertTrue(java.contains("requiredProperty(\"TC_S.body."), java);
        Assert.assertTrue(java.contains("Secrets.property(\"TC_S.body."), "secret read from the secret store / env");

        Path data = out.resolve("src/test/resources/test-data");
        String packaged = Files.readString(data.resolve("delivery-testdata.properties"));
        Assert.assertFalse(packaged.contains(SECRET), packaged);
        Assert.assertTrue(packaged.contains("=alice"), packaged);
        Assert.assertTrue(packaged.contains("# secret: TC_S.body."), packaged);
        String local = Files.readString(data.resolve(TestDataPropertiesWriter.SECRETS_FILE));
        Assert.assertTrue(local.contains("=" + SECRET), local);
        String key = local.lines().filter(l -> l.contains("=" + SECRET)).findFirst().orElseThrow().split("=")[0];
        Assert.assertTrue(java.contains("project.utils.reports.Secrets.property(\"" + key + "\")"), key);
        Assert.assertFalse(packaged.contains(key + "="), "no empty placeholder that could shadow the local value");

        Path zip = Files.createTempDirectory("secret-zip").resolve("package.zip");
        new FrameworkPackager().zip(out, zip);
        try (ZipFile z = new ZipFile(zip.toFile())) {
            Assert.assertTrue(z.stream().noneMatch(e -> e.getName().endsWith(TestDataPropertiesWriter.SECRETS_FILE)), "secrets file is local-only");
            Assert.assertTrue(z.stream().anyMatch(e -> e.getName().endsWith("delivery-testdata.properties")));
        }
    }

    @Test
    public void generatedPagesCompile() throws Exception {
        Path out = generate();
        List<Path> srcs = new ArrayList<>();
        try (var s = Files.walk(out.resolve("src"))) {
            s.filter(f -> f.toString().endsWith(".java")).forEach(srcs::add);
        }
        Path base = Files.createTempDirectory("secret-base").resolve("BaseTest.java");
        Files.writeString(base, Files.readString(Path.of("customer-framework-template/src/test/java/project/tests/BaseTest.java"))
                .replace("project.listeners.TestNGListeners.class, ", ""));
        srcs.add(base);
        CustomerTemplateCompiler.compile(Files.createTempDirectory("secret-classes"), srcs.toArray(Path[]::new)).close();
    }

    @Test
    public void secretKeysAreTrackedForRedaction() {
        CodegenDataKeys keys = CodegenDataKeys.assign(List.of(), new PageAccumulator());
        ProvenStep a = pass();
        keys.keyFor("TC_S", CodegenDataKeys.PHASE_BODY, a);
        Assert.assertTrue(keys.isSecretKey(keys.keyFor("TC_S", CodegenDataKeys.PHASE_BODY, a)));
    }

    @Test
    public void runtimeRedactsLogsJsonlAndAllureMessages() throws Exception {
        Path classes = Files.createTempDirectory("secret-rt");
        Path t = Path.of("customer-framework-template/src/main/java/project/utils/reports");
        try (URLClassLoader l = CustomerTemplateCompiler.compile(classes, t.resolve("Secrets.java"), t.resolve("KeelResults.java"),
                t.resolve("AllureSteps.java"))) {
            Class<?> secrets = l.loadClass("project.utils.reports.Secrets");
            secrets.getMethod("register", String.class).invoke(null, SECRET);
            Method redact = secrets.getMethod("redact", String.class);
            Assert.assertEquals(redact.invoke(null, "typed " + SECRET + " into #pass"), "typed \u2022\u2022\u2022 into #pass");
            Assert.assertEquals(secrets.getMethod("register", String.class).invoke(null, "ab"), "ab");
            Assert.assertEquals(redact.invoke(null, "abc tab"), "abc tab", "1-2 char values are not substring-masked");

            Path dir = Files.createTempDirectory("keel-results");
            System.setProperty("keel.results.dir", dir.toString());
            try {
                Class<?> kr = l.loadClass("project.utils.reports.KeelResults");
                kr.getMethod("append", String.class, Map.class).invoke(null, "verdicts.jsonl",
                        Map.of("test", "TC_S", "message", "element not interactable: " + SECRET));
                Path p = (Path) kr.getMethod("dir").invoke(null);
                String jsonl = Files.readString(p.resolve("verdicts.jsonl"), StandardCharsets.UTF_8);
                Assert.assertFalse(jsonl.contains(SECRET), jsonl);
                Assert.assertTrue(jsonl.contains("\u2022\u2022\u2022"), jsonl);
                Files.deleteIfExists(p.resolve("verdicts.jsonl"));
            } finally {
                System.clearProperty("keel.results.dir");
            }

            System.setProperty("TC_X.body.k.1", "from-sysprop-secret");
            try {
                Assert.assertEquals(secrets.getMethod("property", String.class).invoke(null, "TC_X.body.k.1"), "from-sysprop-secret");
                Assert.assertEquals(redact.invoke(null, "x from-sysprop-secret"), "x \u2022\u2022\u2022", "property() registers");
            } finally {
                System.clearProperty("TC_X.body.k.1");
            }
            Assert.assertEquals(secrets.getMethod("envName", String.class).invoke(null, "TC_S.body.type_Pass.1"),
                    "KEEL_SECRET_TC_S_BODY_TYPE_PASS_1");
            Throwable missing = Assert.expectThrows(java.lang.reflect.InvocationTargetException.class,
                    () -> secrets.getMethod("property", String.class).invoke(null, "TC_NOPE.secret.1")).getCause();
            Assert.assertTrue(missing.getMessage().contains("KEEL_SECRET_TC_NOPE_SECRET_1"), missing.getMessage());

            Class<?> steps = l.loadClass("project.utils.reports.AllureSteps");
            Throwable thrown = null;
            try {
                steps.getMethod("run", String.class, Runnable.class).invoke(null, "typePass",
                        (Runnable) () -> { throw new IllegalStateException("bad value " + SECRET); });
            } catch (java.lang.reflect.InvocationTargetException e) {
                thrown = e.getCause();
            }
            Assert.assertNotNull(thrown);
            Assert.assertFalse(String.valueOf(thrown.getMessage()).contains(SECRET), String.valueOf(thrown));
            Assert.assertTrue(thrown.getMessage().contains("\u2022\u2022\u2022"), thrown.getMessage());
        }
    }
}
