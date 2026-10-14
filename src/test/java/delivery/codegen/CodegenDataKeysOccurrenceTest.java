package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 0061: occurrence-scoped keys + fail-closed value conflict on same control (F03/F04). */
public class CodegenDataKeysOccurrenceTest {

    @Test
    public void sameMethodDifferentLocatorsGetDistinctOccurrenceKeys() throws Exception {
        Path temp = Files.createTempDirectory("keys-occ");
        ProvenStep a = new ProvenStep("TC_K", "Form", "elementAction", "type",
                "id", "first", "Ada", "", "", true, "ok");
        ProvenStep b = new ProvenStep("TC_K", "Form", "elementAction", "type",
                "id", "last", "Lovelace", "", "", true, "ok");
        new CodeWriter(Path.of("customer-framework-template/templates")).write(temp, List.of(
                new TcOutcome("TC_K", "names", TcStatus.PASSED, List.of(a, b), "", null, false, List.of())));
        String data = Files.readString(temp.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertTrue(data.contains("TC_K.body.") && data.contains("=Ada"), data);
        Assert.assertTrue(data.contains("=Lovelace"), data);
        // Two distinct keys (occurrence suffixes), not one overwritten value
        long valueLines = data.lines().filter(l -> l.contains("=") && !l.trim().startsWith("#")).count();
        Assert.assertTrue(valueLines >= 2, data);
    }

    @Test
    public void sameControlDifferentValuesFailClosed() {
        PageAccumulator pages = new PageAccumulator();
        ProvenStep a = new ProvenStep("TC_C", "Form", "elementAction", "type",
                "id", "user", "alice", "", "", true, "ok");
        ProvenStep b = new ProvenStep("TC_C", "Form", "elementAction", "type",
                "id", "user", "bob", "", "", true, "ok");
        pages.add(a);
        pages.add(b);
        CodegenDataKeys keys = CodegenDataKeys.assign(List.of(), pages);
        keys.keyFor("TC_C", CodegenDataKeys.PHASE_BODY, a);
        IllegalStateException ex = Assert.expectThrows(IllegalStateException.class,
                () -> keys.keyFor("TC_C", CodegenDataKeys.PHASE_BODY, b));
        Assert.assertTrue(ex.getMessage().contains("Conflicting test data"), ex.getMessage());
    }
}
