package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** F13 / P1-04 source contracts: replayable setup and occurrence-scoped evidence. */
public class CallBeforeReplayContractTest {

    @Test
    public void generatedTemplatesEmitSetupChainInBeforeMethod() throws Exception {
        String generated = Files.readString(Path.of(
                "customer-framework-template/templates/GeneratedTest.java.ftl"));
        String todo = Files.readString(Path.of(
                "customer-framework-template/templates/TodoTest.java.ftl"));
        Assert.assertTrue(generated.contains("setupChronCalls"), generated);
        Assert.assertTrue(todo.contains("setupChronCalls"), todo);
        int before = generated.indexOf("@BeforeMethod");
        int test = generated.indexOf("@Test");
        int setup = generated.indexOf("setupChronCalls");
        Assert.assertTrue(before >= 0 && setup > before && setup < test, generated);
    }

    @Test
    public void provePhaseScopesEvidenceByOccurrence() throws Exception {
        String source = Files.readString(Path.of("src/main/java/delivery/job/ProvePhase.java"));
        Assert.assertTrue(source.contains("OccurrenceIdentity.folder"),
                "repeated Call-before executions must not share one evidence folder");
        Assert.assertTrue(source.contains("beginTc("), source);
    }

    @Test
    public void setupFailureFlushesAssertionsBeforeTheTestBodyAndAlwaysQuits() throws Exception {
        String generated = Files.readString(Path.of(
                "customer-framework-template/templates/GeneratedTest.java.ftl"));
        String todo = Files.readString(Path.of(
                "customer-framework-template/templates/TodoTest.java.ftl"));
        int setupList = generated.lastIndexOf("<#list setupChronCalls as call>");
        int setUpEnd = generated.indexOf("    }", setupList);
        int assertAllInSetUp = generated.indexOf("Validation.assertAll();", setupList);
        Assert.assertTrue(setupList >= 0 && assertAllInSetUp > setupList && assertAllInSetUp < setUpEnd,
                "setup assertion failures must fail @BeforeMethod so the leaf body does not run:\n" + generated);
        Assert.assertTrue(generated.contains("@AfterMethod(alwaysRun = true)"), generated);
        Assert.assertTrue(todo.contains("Validation.assertAll();"), todo);
        Assert.assertTrue(todo.contains("@AfterMethod(alwaysRun = true)"), todo);
        Assert.assertTrue(generated.contains("} finally {"), generated);
        Assert.assertTrue(generated.contains("driver.quit();"), generated);
    }
}
