package delivery.codegen;

import delivery.settle.SettlePolicy;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The template embeds Keel Automation Engine A8 verbatim: settle-policy.json, settle-probe.js and
 * settle-policy.golden.txt are byte-identical to patches/A8 (-Dkeel.a8.dir) and to the engine
 * resources; the template's SettlePolicy port produces the golden canonical form and matches the
 * engine's SettlePolicy rule for rule (incl. the per-kind sideEffects default); every action codegen
 * emits is an A8 action key.
 */
public class SettlePolicyParityTest {
    static final Path TPL_RES = Path.of("customer-framework-template/src/main/resources/keel");
    static final Path TABLE = TPL_RES.resolve("settle-policy.json");
    static final Path PROBE = TPL_RES.resolve("settle-probe.js");
    /** r3: engine page-scoped network tracker, copied verbatim (not part of the frozen v1 trio). */
    static final Path NETWORK = TPL_RES.resolve("settle-network.js");
    static final Path GOLDEN = Path.of("customer-framework-template/src/test/resources/keel/settle-policy.golden.txt");
    private URLClassLoader loader;
    private Class<?> policy;

    static Path a8Dir() {
        return Path.of(System.getProperty("keel.a8.dir", "/workspace/testpilot/patches/A8"));
    }

    @BeforeClass
    public void compile() throws Exception {
        loader = CustomerTemplateCompiler.compile(Files.createTempDirectory("settle"),
                Path.of("customer-framework-template/src/main/java/project/utils/settle/SettlePolicy.java"));
        policy = loader.loadClass("project.utils.settle.SettlePolicy");
    }

    @AfterClass(alwaysRun = true)
    public void close() throws Exception {
        if (loader != null) loader.close();
    }

    private Object parse(String json) {
        try {
            return policy.getMethod("parse", String.class).invoke(null, json);
        } catch (InvocationTargetException e) {
            throw (RuntimeException) e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private String canonical(Object p) throws Exception {
        return (String) policy.getMethod("canonical").invoke(p);
    }

    @Test
    public void byteParityWithEngineA8Artifact() throws Exception {
        Path a8 = a8Dir();
        if (!Files.isDirectory(a8)) {
            // Box-only artifact (AE's patches/A8). On customer/Windows checkouts the in-repo parity and sha256 pin
            // tests (byteParityWithEngineResources, frozenV1ShaPins) still guard the bytes.
            throw new org.testng.SkipException("A8 artifact dir absent: " + a8 + " (set -Dkeel.a8.dir to check against it)");
        }
        Assert.assertEquals(delivery.settle.LfPins.lf(Files.readAllBytes(TABLE)), delivery.settle.LfPins.lf(Files.readAllBytes(a8.resolve("settle-policy.json"))), "settle-policy.json");
        Assert.assertEquals(delivery.settle.LfPins.lf(Files.readAllBytes(PROBE)), delivery.settle.LfPins.lf(Files.readAllBytes(a8.resolve("settle-probe.js"))), "settle-probe.js");
        Assert.assertEquals(delivery.settle.LfPins.lf(Files.readAllBytes(GOLDEN)), delivery.settle.LfPins.lf(Files.readAllBytes(a8.resolve("settle-policy.golden.txt"))), "golden");
    }

    @Test
    public void byteParityWithEngineResources() throws Exception {
        Assert.assertEquals(delivery.settle.LfPins.lf(Files.readAllBytes(TABLE)), delivery.settle.LfPins.lf(Files.readAllBytes(Path.of("src/main/resources/keel/settle-policy.json"))));
        Assert.assertEquals(delivery.settle.LfPins.lf(Files.readAllBytes(PROBE)), delivery.settle.LfPins.lf(Files.readAllBytes(Path.of("src/main/resources/keel/settle-probe.js"))));
        Assert.assertEquals(delivery.settle.LfPins.lf(Files.readAllBytes(GOLDEN)), delivery.settle.LfPins.lf(Files.readAllBytes(Path.of("src/test/resources/keel/settle-policy.golden.txt"))));
        Assert.assertEquals(Files.readAllBytes(NETWORK), Files.readAllBytes(Path.of("src/main/resources/keel/settle-network.js")),
                "settle-network.js raw bytes (r3; .gitattributes -text keeps both copies LF)");
    }

    @Test
    public void templatePortMatchesA8GoldenCanonicalForm() throws Exception {
        Object p = parse(Files.readString(TABLE));
        Assert.assertEquals(canonical(p), Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n")); // r3: CRLF checkout
        Assert.assertEquals(canonical(p), SettlePolicy.builtin().canonical(), "template port == engine SettlePolicy");
    }

    @Test
    public void actionKeysMatchEngineEnum() throws Exception {
        Class<?> tplAction = loader.loadClass("project.utils.settle.SettlePolicy$Action");
        List<String> tpl = Arrays.stream(tplAction.getEnumConstants()).map(e -> {
            try {
                return (String) tplAction.getField("key").get(e);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }).toList();
        Assert.assertEquals(tpl, Arrays.stream(SettlePolicy.Action.values()).map(a -> a.key).toList());
    }

    /** Every action codegen can emit through Settle.after must be an A8 key (no aliases, no mobile kinds). */
    @Test
    public void everyGeneratedActionIsAnA8Key() throws Exception {
        for (String a : List.of("click", "type", "typeSecret", "select", "drag")) {
            SettlePolicy.Action.of(a);
            Assert.assertNotNull(policy.getMethod("rule", String.class).invoke(parse(Files.readString(TABLE)), a), a);
        }
        String ftl = Files.readString(Path.of("customer-framework-template/templates/Page.java.ftl"));
        String settled = Files.readString(Path.of(
                "customer-framework-template/src/main/java/project/utils/locators/SettledActions.java"));
        // Smart/drag ceremony lives in SettledActions; FTL passes A8 settle keys through.
        Assert.assertTrue(settled.contains("Settle.after"), "SettledActions uses Settle.after");
        Assert.assertTrue(ftl.contains("SettledActions.click") && ftl.contains("SettledActions.drag")
                && ftl.contains("\"${method.settleAction}\""), "template passes A8 keys to SettledActions");
        // settleAction is the MethodModel action, or typeSecret for IR secret: true type steps (r1/07)
        Assert.assertEquals(new PageAccumulator.MethodModel("m", "type", "f", true, true, false, "", true).getSettleAction(), "typeSecret");
        Assert.assertEquals(new PageAccumulator.MethodModel("m", "select", "f", true, true, false, "", false).getSettleAction(), "select");
        // A8 also carries the mobile kinds (tap, longPress) for the Android plan; web codegen never emits them.
        Assert.assertFalse(ftl.contains("\"tap\"") || ftl.contains("\"longPress\""));
    }

    @Test
    public void sideEffectsKindDefaultsMatchEngine() throws Exception {
        Object p = parse(Files.readString(TABLE));
        Class<?> tplAction = loader.loadClass("project.utils.settle.SettlePolicy$Action");
        for (SettlePolicy.Action a : SettlePolicy.Action.values()) {
            Object tplRule = policy.getMethod("rule", String.class).invoke(p, a.key);
            Object se = tplRule.getClass().getMethod("sideEffects").invoke(tplRule);
            Assert.assertEquals(se.getClass().getField("wire").get(se), SettlePolicy.builtin().rule(a).sideEffects().wire, a.key);
        }
        Assert.assertEquals(tplAction.getEnumConstants().length, SettlePolicy.Action.values().length);
    }

    @Test
    public void invalidPoliciesAreRejectedLikeTheEngine() throws Exception {
        String ok = Files.readString(TABLE);
        Map<String, String> cases = Map.of(
                ok.replace("\"version\": 1", "\"version\": 2"), "version 2 not supported",
                ok.replace("\"contract\": \"keel.settle-policy\"", "\"contract\": \"x\""), "contract must be",
                ok.replace("\"pollMs\": 100", "\"pollMs\": 0"), "defaults.pollMs must be > 0",
                ok.replace("\"wait\":", "\"waitX\":"), "unknown settle action 'waitX'",
                ok.replaceFirst("\"url\": \"any\"", "\"url\": \"elsewhere\""), "bad Url 'elsewhere'",
                ok.replace("\"typeSecret\":     {", "\"typeSecret\":     {\"extra\": 1, "), "typeSecret.extra is not a known field",
                ok.replaceFirst("\"sideEffects\": \"unknown\"", "\"sideEffects\": \"readonly\""), "sideEffects must be one of none|mutating|unknown");
        cases.forEach((json, msg) -> {
            IllegalArgumentException tpl = Assert.expectThrows(IllegalArgumentException.class, () -> parse(json));
            IllegalArgumentException eng = Assert.expectThrows(IllegalArgumentException.class, () -> SettlePolicy.parse(json));
            Assert.assertTrue(tpl.getMessage().contains(msg), tpl.getMessage());
            Assert.assertTrue(eng.getMessage().contains(msg), "engine: " + eng.getMessage());
        });
        // duplicate key: json-simple keeps the last one, so "assert" disappears
        String missing = ok.replace("\"assert\":", "\"wait\":");
        String m = missing;
        IllegalArgumentException e = Assert.expectThrows(IllegalArgumentException.class, () -> parse(m));
        Assert.assertTrue(e.getMessage().contains("action 'assert' is missing"), e.getMessage());
    }

    /** settle-policy v1 is frozen (r2 SPEC section 1): same sha256 pins as the engine's SettlePolicyFrozenTest. */
    @Test
    public void frozenV1ShaPins() throws Exception {
        java.util.Map<Path, String> pins = java.util.Map.of(
                TABLE, "96f0dd0596ba003df868efca65fbd25c215a10b2c826124dddd3471c5aa473a0",
                PROBE, "c73cdd8b2dbd319424b09e4fc417a3b66464571a169448d69cc6cc7b29459932",
                GOLDEN, "78b16a7b2c5fbd63e869ad51d8061437f57124f6479cb505ed8c70f8903c72b4");
        for (java.util.Map.Entry<Path, String> e : pins.entrySet()) {
            delivery.settle.LfPins.assertPinned(e.getKey().toString(), Files.readAllBytes(e.getKey()), e.getValue()); // r3: CRLF-aware
        }
    }

    /** r3: the template pins use the CRLF-aware check: CRLF warns, -Dkeel.pins.strict-eol=true fails, a real change fails. */
    @Test
    public void templatePinsAreCrlfAware() throws Exception {
        String pin = "96f0dd0596ba003df868efca65fbd25c215a10b2c826124dddd3471c5aa473a0";
        byte[] crlf = new String(Files.readAllBytes(TABLE), java.nio.charset.StandardCharsets.UTF_8).replace("\n", "\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String prev = System.getProperty("keel.pins.strict-eol");
        try {
            System.clearProperty("keel.pins.strict-eol");
            Assert.assertNull(delivery.settle.LfPins.check("tpl settle-policy.json", crlf, pin), "CRLF only warns");
            System.setProperty("keel.pins.strict-eol", "true");
            Assert.assertTrue(String.valueOf(delivery.settle.LfPins.check("tpl settle-policy.json", crlf, pin)).contains("CRLF line endings"));
        } finally {
            if (prev == null) System.clearProperty("keel.pins.strict-eol"); else System.setProperty("keel.pins.strict-eol", prev);
        }
        byte[] changed = Files.readAllBytes(TABLE).clone();
        changed[changed.length / 2] ^= 1;
        Assert.assertTrue(String.valueOf(delivery.settle.LfPins.check("tpl settle-policy.json", changed, pin)).contains("FROZEN"));
    }

    @Test
    public void networkScopeScriptMatchesTheEngine() throws Exception {
        Assert.assertEquals(delivery.settle.LfPins.lf(Files.readAllBytes(NETWORK)), delivery.settle.LfPins.lf(Files.readAllBytes(Path.of("src/main/resources/keel/settle-network.js"))),
                "settle-network.js must be byte-identical to the engine's (r3)");
    }
}
