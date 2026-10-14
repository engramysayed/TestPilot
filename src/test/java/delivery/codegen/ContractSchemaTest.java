package delivery.codegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import delivery.ir.ProvenStepCodec;
import delivery.snapshot.KeelTarget;
import delivery.snapshot.RankedLocators;
import delivery.snapshot.ScoredLocator;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every contract format has a JSON Schema (src/main/resources/keel/contracts) plus valid and invalid
 * fixtures (src/test/resources/keel/contracts/fixtures/<schema>/{valid,invalid}). Also: the golden
 * vectors validate, the grammar schemas agree with the real parser, and IR emitted by the engine and
 * by codegen validates.
 */
public class ContractSchemaTest {
    static final Path SCHEMAS = Path.of("src/main/resources/keel/contracts");
    static final Path FIXTURES = Path.of("src/test/resources/keel/contracts/fixtures");
    static final Path VECTORS = Path.of("src/test/resources/keel/contract-v2-vectors.json");
    static final ObjectMapper JSON = new ObjectMapper();
    static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
            b -> b.schemaMappers(m -> m.mapPrefix("https://keel.dev/contracts/", "classpath:keel/contracts/")));

    static JsonSchema schema(String name) {
        return FACTORY.getSchema(SchemaLocation.of("https://keel.dev/contracts/" + name + ".schema.json"));
    }

    static Set<ValidationMessage> validate(String schema, JsonNode node) {
        return schema(schema).validate(node);
    }

    static Set<ValidationMessage> validate(String schema, String json) throws IOException {
        return validate(schema, JSON.readTree(json));
    }

    static List<String> schemaNames() throws IOException {
        try (Stream<Path> s = Files.list(SCHEMAS)) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".schema.json"))
                    .map(n -> n.substring(0, n.length() - ".schema.json".length())).sorted().toList();
        }
    }

    @DataProvider
    public Object[][] fixtures() throws IOException {
        List<Object[]> rows = new ArrayList<>();
        try (Stream<Path> s = Files.walk(FIXTURES)) {
            for (Path f : s.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Path rel = FIXTURES.relativize(f);
                rows.add(new Object[]{rel.getName(0).toString(), rel.getName(1).toString(), f});
            }
        }
        return rows.toArray(Object[][]::new);
    }

    @Test
    public void everySchemaIsValidAndHasBothFixtureKinds() throws IOException {
        JsonSchema meta = FACTORY.getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
        for (String name : schemaNames()) {
            JsonNode node = JSON.readTree(SCHEMAS.resolve(name + ".schema.json").toFile());
            Assert.assertEquals(meta.validate(node), Set.of(), name + " against the 2020-12 meta-schema");
            Assert.assertEquals(node.path("$id").asText(), "https://keel.dev/contracts/" + name + ".schema.json");
            for (String kind : List.of("valid", "invalid")) {
                Path dir = FIXTURES.resolve(name).resolve(kind);
                Assert.assertTrue(Files.isDirectory(dir) && Files.list(dir).findAny().isPresent(),
                        "missing " + kind + " fixture for " + name);
            }
        }
    }

    @Test(dataProvider = "fixtures")
    public void fixtureValidatesAsLabelled(String schema, String kind, Path file) throws IOException {
        Set<ValidationMessage> errors = validate(schema, Files.readString(file));
        if (kind.equals("valid")) {
            Assert.assertEquals(errors, Set.of(), file.toString());
        } else {
            Assert.assertFalse(errors.isEmpty(), file + " must be rejected by " + schema);
        }
    }

    /** The grammar schemas are pinned to the real parser: schema-valid iff KeelTarget.parse accepts. */
    @Test(dataProvider = "fixtures")
    public void grammarSchemasAgreeWithParser(String schema, String kind, Path file) throws IOException {
        if (!schema.startsWith("keel-target-v")) {
            return;
        }
        String target = JSON.readTree(file.toFile()).asText();
        boolean parses;
        int version = 0;
        try {
            version = KeelTarget.parse(target).requiredVersion();
            parses = true;
        } catch (RuntimeException e) {
            parses = false;
        }
        boolean expected = schema.equals("keel-target-v1") ? parses && version == 1 : parses;
        Assert.assertEquals(kind.equals("valid"), expected, file + " parser=" + parses + " v" + version);
    }

    @Test
    public void goldenVectorsValidate() throws IOException {
        JsonNode v = JSON.readTree(VECTORS.toFile());
        Assert.assertEquals(validate("contract-v2-vectors", v), Set.of());
        for (JsonNode p : v.get("parse")) {
            String in = JSON.writeValueAsString(p.get("in"));
            Assert.assertEquals(validate("keel-target-v2", in), Set.of(), in);
            Assert.assertEquals(validate("keel-target-v1", in).isEmpty(), p.get("version").asInt() == 1, "v1 schema: " + in);
            Assert.assertEquals(validate("keel-target-v2", JSON.writeValueAsString(p.get("format"))), Set.of());
        }
        for (JsonNode bad : v.get("invalid")) {
            Assert.assertFalse(validate("keel-target-v2", JSON.writeValueAsString(bad)).isEmpty(), "must reject " + bad);
        }
    }

    @Test
    public void irEmittedByEngineAndCodegenValidates() throws IOException {
        RankedLocators v1 = new RankedLocators(new ScoredLocator("keel", "css=[id=\"a\"]", 0.6, "id"),
                List.of(new ScoredLocator("keel", "css=button.btn", 0.4, "class")), "h/x");
        RankedLocators v2 = new RankedLocators(new ScoredLocator("keel", "text=\"Refresh\"", 0.65, "text"),
                List.of(new ScoredLocator("keel", "css=button.btn", 0.40, "class")), "h/x");
        for (RankedLocators r : List.of(v1, v2)) {
            JSONObject ir = r.toIrJson();
            Assert.assertEquals(validate("ranked-locators-ir", ir.toString()), Set.of(), ir.toString());
            ir.put("tcId", "T").put("pageName", "P").put("action", "click");
            JSONObject step = ProvenStepCodec.toJson(ProvenStepCodec.fromJson(ir));
            Assert.assertEquals(validate("proven-step-ir", step.toString()), Set.of(), step.toString());
        }
        ProvenStep drag = new ProvenStep("T", "Board", "elementAction", "drag", "keel", "css=#card", "", "", "", true, "ok")
                .withDropCandidates(List.of(LocatorCandidate.of("keel", "css=#lane >> point=5,5@10x10")));
        String dragJson = ProvenStepCodec.toJson(drag).toString();
        Assert.assertEquals(validate("proven-step-ir", dragJson), Set.of(), dragJson);
    }

    @Test
    public void validStepFixturesDecodeAndInvalidContractIsRejectedByCodec() throws IOException {
        try (Stream<Path> s = Files.list(FIXTURES.resolve("proven-step-ir/valid"))) {
            for (Path f : s.toList()) {
                ProvenStep step = ProvenStepCodec.fromJson(new JSONObject(Files.readString(f)));
                Assert.assertNotNull(step.locatorValue(), f.toString());
            }
        }
        for (String bad : List.of("contract-3", "contract-0", "contract-1-with-v2-hop", "side-effects-bad-value", "side-effects-boolean",
                "secret-string", "secret-number")) {
            JSONObject o = new JSONObject(Files.readString(FIXTURES.resolve("proven-step-ir/invalid/" + bad + ".json")));
            Assert.expectThrows(IllegalArgumentException.class, () -> ProvenStepCodec.fromJson(o));
        }
    }

    @Test
    public void explicitContractOneIsAcceptedAndEqualsAbsent() throws IOException {
        JSONObject one = new JSONObject(Files.readString(FIXTURES.resolve("proven-step-ir/valid/explicit-contract-1.json")));
        JSONObject absent = new JSONObject(one.toString());
        absent.remove("contract");
        Assert.assertEquals(ProvenStepCodec.fromJson(one), ProvenStepCodec.fromJson(absent));
        Assert.assertFalse(ProvenStepCodec.toJson(ProvenStepCodec.fromJson(one)).has("contract"), "1 is written as absent");
        JSONObject ranked = new JSONObject(Files.readString(FIXTURES.resolve("ranked-locators-ir/valid/explicit-contract-1.json")));
        Assert.assertEquals(RankedLocators.fromIrJson(ranked).main().value(), ranked.getString("locatorValue"));
    }

    @Test
    public void embeddedAndEngineSettlePoliciesValidate() throws IOException {
        Assert.assertEquals(validate("settle-policy", Files.readString(SettlePolicyParityTest.TABLE)), Set.of());
        Assert.assertEquals(validate("settle-policy", Files.readString(Path.of("src/main/resources/keel/settle-policy.json"))), Set.of());
    }
}
