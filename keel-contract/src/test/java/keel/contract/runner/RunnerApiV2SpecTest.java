package keel.contract.runner;

import org.testng.annotations.Test;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

/** Keeps runner-api-v2.yaml and the Java records/enums in lock-step (schema validation without a YAML dependency). */
public class RunnerApiV2SpecTest {
    private static String spec() throws Exception {
        try (InputStream in = RunnerApiV2SpecTest.class.getResourceAsStream("/keel/contract/runner-api-v2.yaml")) {
            assertNotNull(in, "runner-api-v2.yaml on classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String schemaBlock(String spec, String name) {
        int at = spec.indexOf("\n    " + name + ":");
        assertTrue(at >= 0, "schema " + name + " missing");
        int end = spec.indexOf("\n    ", at + 5);
        while (end >= 0 && spec.startsWith("      ", end + 1)) {
            end = spec.indexOf("\n    ", end + 5);
        }
        return end < 0 ? spec.substring(at) : spec.substring(at, end);
    }

    @Test
    public void everyRecordComponentIsInTheSchema() throws Exception {
        String spec = spec();
        for (Class<?> c : RunnerDtos.class.getDeclaredClasses()) {
            String block = schemaBlock(spec, c.getSimpleName());
            for (RecordComponent rc : c.getRecordComponents()) {
                assertTrue(block.contains(" " + rc.getName() + ":"), c.getSimpleName() + "." + rc.getName());
            }
            long props = block.lines().filter(l -> l.startsWith("        ") && !l.startsWith("         ")).count();
            assertEquals(props, c.getRecordComponents().length, "no extra schema properties for " + c.getSimpleName());
        }
    }

    @Test
    public void enumsMatch() throws Exception {
        String spec = spec();
        for (Class<? extends Enum<?>> e : java.util.List.of(WireJobKind.class, WireStage.class, RunnerCapability.class)) {
            String values = Arrays.stream(e.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
            assertTrue(spec.contains(e.getSimpleName() + ": {type: string, enum: [" + values + "]}"), e.getSimpleName());
        }
    }

    @Test
    public void documentsAuthAndFencingHeaders() throws Exception {
        String spec = spec();
        for (String h : new String[]{RunnerHeaders.SIGNATURE, RunnerHeaders.TIMESTAMP, RunnerHeaders.NONCE,
                RunnerHeaders.ATTEMPT, RunnerHeaders.CANCEL_GENERATION, RunnerHeaders.FENCING_TOKEN}) {
            assertTrue(spec.contains(h), h);
        }
        for (String p : new String[]{"/claim:", "/lease:", "/stage:", "/progress:", "/chunks/{index}:", "/result:", "/cancel:"}) {
            assertTrue(spec.contains(RunnerHeaders.V2_PREFIX) && spec.contains(p), p);
        }
    }
}
