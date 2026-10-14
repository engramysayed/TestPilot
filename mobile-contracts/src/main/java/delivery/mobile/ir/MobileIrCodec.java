package delivery.mobile.ir;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Strict Mobile IR codec (M0-03). Never falls through to web ProvenStepCodec.
 * Unknown schemaVersion / operation / required envelope fields → {@link MobileIrException}.
 * Unknown metadata keys may be retained on the tree but unknown behavioral fields on steps
 * are rejected when they would change execution semantics (strict required-field validation).
 */
public final class MobileIrCodec {
    private static final ObjectMapper MAPPER = buildMapper();

    private MobileIrCodec() {
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        m.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        SimpleModule mod = new SimpleModule("mobile-ir");
        mod.addDeserializer(MobileCaseIr.class, new CaseDeserializer());
        m.registerModule(mod);
        return m;
    }

    public static MobileCaseIr parse(String json) {
        if (json == null || json.isBlank()) {
            throw new MobileIrException("EMPTY", "mobile IR JSON is empty");
        }
        return parse(new StringReader(json));
    }

    public static MobileCaseIr parse(Reader reader) {
        try {
            return MAPPER.readValue(reader, MobileCaseIr.class);
        } catch (MobileIrException e) {
            throw e;
        } catch (IOException e) {
            throw new MobileIrException("PARSE", "mobile IR parse failed: " + e.getMessage());
        }
    }

    public static String write(MobileCaseIr ir) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toTree(ir));
        } catch (IOException e) {
            throw new MobileIrException("WRITE", "mobile IR write failed: " + e.getMessage());
        }
    }

    /** Round-trip helper for tests. */
    public static MobileCaseIr roundTrip(MobileCaseIr ir) {
        return parse(write(ir));
    }

    static ObjectNode toTree(MobileCaseIr ir) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", ir.schemaVersion());
        root.put("platform", ir.platform().name());
        root.put("tcId", ir.tcId());
        root.put("libraryRevisionId", ir.libraryRevisionId());
        root.put("appRevisionId", ir.appRevisionId());
        root.put("caseSettingsRevisionId", ir.caseSettingsRevisionId());
        root.put("namingRevisionId", ir.namingRevisionId());
        ObjectNode proof = root.putObject("proof");
        proof.put("status", ir.proof().status());
        proof.put("source", ir.proof().source());
        if (ir.proof().attemptId() == null) {
            proof.putNull("attemptId");
        } else {
            proof.put("attemptId", ir.proof().attemptId());
        }
        var setup = root.putArray("setupRefs");
        for (String s : ir.setupRefs()) {
            setup.add(s);
        }
        var steps = root.putArray("steps");
        for (MobileStep st : ir.steps()) {
            steps.add(stepNode(st));
        }
        return root;
    }

    private static ObjectNode stepNode(MobileStep st) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("stepId", st.stepId());
        if (st.screenKey() != null) {
            n.put("screenKey", st.screenKey());
        }
        n.put("operation", st.operation().name());
        if (st.context() != null) {
            ObjectNode c = n.putObject("context");
            c.put("kind", st.context().kind());
            if (st.context().packageName() != null) {
                c.put("package", st.context().packageName());
            }
            if (st.context().webViewUrlMatcher() != null) {
                c.put("webViewUrlMatcher", st.context().webViewUrlMatcher());
            }
            if (st.context().webViewTitleMatcher() != null) {
                c.put("webViewTitleMatcher", st.context().webViewTitleMatcher());
            }
        }
        if (st.target() != null) {
            ObjectNode t = n.putObject("target");
            t.put("strategy", st.target().strategy());
            t.put("value", st.target().value());
            t.put("origin", st.target().origin());
            if (st.target().expectedMatches() != null) {
                t.put("expectedMatches", st.target().expectedMatches());
            }
        }
        if (st.input() != null) {
            ObjectNode in = n.putObject("input");
            in.put("kind", st.input().kind());
            if (st.input().key() != null) {
                in.put("key", st.input().key());
            }
            if (st.input().value() != null) {
                in.put("value", st.input().value());
            }
            if (st.input().sensitive() != null) {
                in.put("sensitive", st.input().sensitive());
            }
        }
        if (st.timeoutMs() != null) {
            n.put("timeoutMs", st.timeoutMs());
        }
        if (st.retryPolicy() != null) {
            n.put("retryPolicy", st.retryPolicy());
        }
        n.put("retryIfNoChange", Boolean.TRUE.equals(st.retryIfNoChange()));
        ObjectNode ev = n.putObject("evidence");
        if (st.evidence().snapshotId() == null) {
            ev.putNull("snapshotId");
        } else {
            ev.put("snapshotId", st.evidence().snapshotId());
        }
        var arts = ev.putArray("artifactRefs");
        for (String a : st.evidence().artifactRefs()) {
            arts.add(a);
        }
        if (st.assertType() != null) {
            n.put("assertType", st.assertType());
        }
        if (st.captureVar() != null) {
            n.put("captureVar", st.captureVar());
        }
        if (st.compareMode() != null) {
            n.put("compareMode", st.compareMode());
        }
        if (st.scrollDirection() != null) {
            n.put("scrollDirection", st.scrollDirection());
        }
        if (st.maxSwipes() != null) {
            n.put("maxSwipes", st.maxSwipes());
        }
        if (st.visibilityPercentage() != null) {
            n.put("visibilityPercentage", st.visibilityPercentage());
        }
        return n;
    }

    private static final class CaseDeserializer extends StdDeserializer<MobileCaseIr> {
        CaseDeserializer() {
            super(MobileCaseIr.class);
        }

        @Override
        public MobileCaseIr deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode root = p.getCodec().readTree(p);
            if (root == null || !root.isObject()) {
                throw new MobileIrException("PARSE", "mobile IR root must be an object");
            }
            // Reject unknown behavioral top-level keys that look like execution config
            rejectUnknownBehavioral(root, List.of(
                    "schemaVersion", "platform", "tcId", "libraryRevisionId", "appRevisionId",
                    "caseSettingsRevisionId", "namingRevisionId", "proof", "setupRefs", "steps",
                    "metadata", "notes", "author", "createdAt"));

            String schema = text(root, "schemaVersion");
            MobilePlatform platform = MobilePlatform.parse(text(root, "platform"));
            MobileProof proof = parseProof(root.get("proof"));
            List<String> setup = stringList(root.get("setupRefs"));
            List<MobileStep> steps = parseSteps(root.get("steps"));
            return new MobileCaseIr(
                    schema,
                    platform,
                    text(root, "tcId"),
                    text(root, "libraryRevisionId"),
                    text(root, "appRevisionId"),
                    text(root, "caseSettingsRevisionId"),
                    text(root, "namingRevisionId"),
                    proof,
                    setup,
                    steps);
        }
    }

    private static void rejectUnknownBehavioral(JsonNode root, List<String> allowed) {
        Iterator<String> it = root.fieldNames();
        while (it.hasNext()) {
            String f = it.next();
            if (allowed.contains(f)) {
                continue;
            }
            // metadata-like keys ok; anything that looks behavioral is rejected
            if (f.startsWith("meta") || f.equals("notes") || f.equals("author") || f.equals("createdAt")) {
                continue;
            }
            throw new MobileIrException("UNKNOWN_FIELD",
                    "unknown behavioral field on mobile IR: " + f);
        }
    }

    private static MobileProof parseProof(JsonNode n) {
        if (n == null || n.isNull() || !n.isObject()) {
            throw new MobileIrException("PROOF_REQUIRED", "proof is required");
        }
        String attempt = n.has("attemptId") && !n.get("attemptId").isNull()
                ? n.get("attemptId").asText() : null;
        return new MobileProof(text(n, "status"), text(n, "source"), attempt);
    }

    private static List<MobileStep> parseSteps(JsonNode n) {
        if (n == null || n.isNull()) {
            throw new MobileIrException("STEPS_REQUIRED", "steps is required");
        }
        if (!n.isArray()) {
            throw new MobileIrException("STEPS_TYPE", "steps must be an array");
        }
        List<MobileStep> out = new ArrayList<>();
        for (JsonNode step : n) {
            out.add(parseStep(step));
        }
        return out;
    }

    private static MobileStep parseStep(JsonNode n) {
        if (n == null || !n.isObject()) {
            throw new MobileIrException("STEP_TYPE", "step must be an object");
        }
        MobileOperation op = MobileOperation.parse(text(n, "operation"));
        MobileContext ctx = parseContext(n.get("context"));
        MobileTarget target = parseTarget(n.get("target"));
        MobileInput input = parseInput(n.get("input"));
        MobileStepEvidence ev = parseEvidence(n.get("evidence"));
        Integer timeout = n.has("timeoutMs") && !n.get("timeoutMs").isNull()
                ? n.get("timeoutMs").asInt() : null;
        Boolean retryNoChange = n.has("retryIfNoChange") && !n.get("retryIfNoChange").isNull()
                && n.get("retryIfNoChange").asBoolean();
        Integer vis = n.has("visibilityPercentage") && !n.get("visibilityPercentage").isNull()
                ? n.get("visibilityPercentage").asInt() : null;
        Integer maxSwipes = n.has("maxSwipes") && !n.get("maxSwipes").isNull()
                ? n.get("maxSwipes").asInt() : null;
        return new MobileStep(
                text(n, "stepId"),
                optionalText(n, "screenKey"),
                op,
                ctx,
                target,
                input,
                timeout,
                optionalText(n, "retryPolicy"),
                retryNoChange,
                ev,
                optionalText(n, "assertType"),
                optionalText(n, "captureVar"),
                optionalText(n, "compareMode"),
                optionalText(n, "scrollDirection"),
                maxSwipes,
                vis);
    }

    private static MobileContext parseContext(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String pkg = n.has("package") ? optionalText(n, "package")
                : optionalText(n, "packageName");
        return new MobileContext(
                text(n, "kind"),
                pkg,
                optionalText(n, "webViewUrlMatcher"),
                optionalText(n, "webViewTitleMatcher"));
    }

    private static MobileTarget parseTarget(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        Integer matches = n.has("expectedMatches") && !n.get("expectedMatches").isNull()
                ? n.get("expectedMatches").asInt() : null;
        return new MobileTarget(
                text(n, "strategy"),
                n.has("value") && !n.get("value").isNull() ? n.get("value").asText() : null,
                text(n, "origin"),
                matches);
    }

    private static MobileInput parseInput(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        Boolean sens = n.has("sensitive") && !n.get("sensitive").isNull()
                ? n.get("sensitive").asBoolean() : null;
        return new MobileInput(
                text(n, "kind"),
                optionalText(n, "key"),
                optionalText(n, "value"),
                sens);
    }

    private static MobileStepEvidence parseEvidence(JsonNode n) {
        if (n == null || n.isNull()) {
            return new MobileStepEvidence(null, List.of());
        }
        String snap = n.has("snapshotId") && !n.get("snapshotId").isNull()
                ? n.get("snapshotId").asText() : null;
        return new MobileStepEvidence(snap, stringList(n.get("artifactRefs")));
    }

    private static List<String> stringList(JsonNode n) {
        if (n == null || n.isNull()) {
            return List.of();
        }
        if (!n.isArray()) {
            throw new MobileIrException("LIST_TYPE", "expected JSON array");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode x : n) {
            out.add(x.asText());
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) {
            throw new MobileIrException("FIELD_REQUIRED", field + " is required");
        }
        String s = v.asText();
        if (s == null || s.isBlank()) {
            throw new MobileIrException("FIELD_REQUIRED", field + " is required");
        }
        return s;
    }

    private static String optionalText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }
}
