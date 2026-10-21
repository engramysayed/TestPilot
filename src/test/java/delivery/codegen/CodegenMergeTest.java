package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CodegenMergeTest {
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");

    @Test
    public void samePageStemEmitsOneClassWithTwoMethods() throws Exception {
        Path dir = Files.createTempDirectory("merge-login");
        ProvenStep a = step("TC_VALID", "Login", "click", "submit");
        ProvenStep b = step("TC_INVALID", "Login", "click", "submit");
        new CodeWriter(TEMPLATES).write(dir, List.of(
                outcome("TC_VALID", "Valid login", a),
                outcome("TC_INVALID", "Invalid login", b)));
        Path file = dir.resolve("src/test/java/project/tests/generated/Login_Test.java");
        Assert.assertTrue(Files.isRegularFile(file), "expected merged Login_Test.java");
        String src = Files.readString(file);
        Assert.assertTrue(src.contains("Valid login"), src);
        Assert.assertTrue(src.contains("Invalid login"), src);
        Assert.assertTrue(src.contains("login_TC_VALID"), src);
        Assert.assertTrue(src.contains("login_TC_INVALID"), src);
    }

    private static TcOutcome outcome(String id, String title, ProvenStep step) {
        return new TcOutcome(id, TcStatus.PASSED, List.of(step), "", null).withTitle(title);
    }

    private static ProvenStep step(String tcId, String page, String action, String target) {
        return new ProvenStep(tcId, page, "elementAction", action,
                "id", target, "", "", "", true, "intent:CLICK");
    }
}
