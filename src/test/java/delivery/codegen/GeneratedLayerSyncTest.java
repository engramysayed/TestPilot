package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** F04 / P1-03: the generated layer is rewritten from the full IR set, not incrementally patched. */
public class GeneratedLayerSyncTest {

    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");

    private static ProvenStep type(String tcId, String page, String control, String value) {
        return new ProvenStep(tcId, page, "elementAction", "type",
                "id", control, value, "", "", true, "audit");
    }

    @Test
    public void fullSuiteKeepsUnchangedCaseMethodsAndData() throws Exception {
        Path root = Files.createTempDirectory("gen-layer-keep");
        CodeWriter writer = new CodeWriter(TEMPLATES);
        TcOutcome a = new TcOutcome("TC_A", "A", TcStatus.PASSED,
                List.of(type("TC_A", "Checkout", "first-name", "Alice")), "", null, false, List.of());
        TcOutcome b = new TcOutcome("TC_B", "B", TcStatus.PASSED,
                List.of(type("TC_B", "Checkout", "last-name", "Smith")), "", null, false, List.of());
        writer.write(root, List.of(a));
        writer.write(root, List.of(a, b));

        String page = Files.readString(root.resolve("src/main/java/project/pages/Checkout_Actions.java"));
        Assert.assertTrue(page.contains("type_First_Name"), page);
        Assert.assertTrue(page.contains("type_Last_Name"), page);
        Assert.assertTrue(Files.exists(root.resolve("src/test/java/project/tests/generated/TC_A.java")));
        Assert.assertTrue(Files.exists(root.resolve("src/test/java/project/tests/generated/TC_B.java")));
        String data = Files.readString(root.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertTrue(data.contains("TC_A."), data);
        Assert.assertTrue(data.contains("TC_B."), data);
    }

    @Test
    public void removedCaseDisappearsFromGeneratedLayer() throws Exception {
        Path root = Files.createTempDirectory("gen-layer-remove");
        CodeWriter writer = new CodeWriter(TEMPLATES);
        TcOutcome a = new TcOutcome("TC_A", "A", TcStatus.PASSED,
                List.of(type("TC_A", "Checkout", "first-name", "Alice")), "", null, false, List.of());
        TcOutcome b = new TcOutcome("TC_B", "B", TcStatus.PASSED,
                List.of(type("TC_B", "Checkout", "last-name", "Smith")), "", null, false, List.of());
        writer.write(root, List.of(a, b));
        writer.write(root, List.of(b));

        Assert.assertFalse(Files.exists(root.resolve("src/test/java/project/tests/generated/TC_A.java")));
        Assert.assertTrue(Files.exists(root.resolve("src/test/java/project/tests/generated/TC_B.java")));
        String page = Files.readString(root.resolve("src/main/java/project/pages/Checkout_Actions.java"));
        Assert.assertFalse(page.contains("type_First_Name"), page);
        Assert.assertTrue(page.contains("type_Last_Name"), page);
        String data = Files.readString(root.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertFalse(data.contains("TC_A."), data);
        Assert.assertTrue(data.contains("TC_B."), data);
    }

    @Test
    public void passToTodoRemovesStalePassedClass() throws Exception {
        Path root = Files.createTempDirectory("gen-layer-demote");
        CodeWriter writer = new CodeWriter(TEMPLATES);
        ProvenStep step = type("TC_B", "Checkout", "last-name", "Smith");
        writer.write(root, List.of(new TcOutcome(
                "TC_B", "B", TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        writer.write(root, List.of(new TcOutcome(
                "TC_B", "B", TcStatus.TODO, List.of(), "blocked", null, false, List.of())));

        Assert.assertFalse(Files.exists(root.resolve("src/test/java/project/tests/generated/TC_B.java")));
        Assert.assertTrue(Files.exists(root.resolve("src/test/java/project/tests/todo/TC_BTodo.java")));
    }
}
