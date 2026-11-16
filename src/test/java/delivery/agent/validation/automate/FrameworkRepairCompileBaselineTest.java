package delivery.agent.validation.automate;

import delivery.codegen.CodeWriter;
import delivery.codegen.CodegenEmitGate;
import delivery.codegen.ProvenStep;
import delivery.job.EmitCompileCheck;
import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Whether qualification fixtures are compilable before any Cursor repair. */
public class FrameworkRepairCompileBaselineTest {

    @Test
    public void codegenFixture_testCompileBeforeRepair() throws Exception {
        Path project = Files.createTempDirectory("compile-baseline-");
        ProvenStep step = new ProvenStep("TC_1", "Login", "elementAction", "click",
                "css", "button", "", "", "", true, "ok");
        var prepared = CodegenEmitGate.prepare(List.of(new TcOutcome(
                "TC_1", "login", TcStatus.PASSED, List.of(step), "", null, false, List.of())));
        FrameworkRepairQualificationFixtures.seedCompilableProject(project);
        new CodeWriter(Path.of("customer-framework-template/templates")).write(project, prepared.outcomes());
        try {
            EmitCompileCheck.runIfEnabled(project);
        } catch (Exception e) {
            Path log = project.resolve("docs/EMIT_COMPILE_FAIL.log");
            String extra = Files.isRegularFile(log) ? Files.readString(log) : "";
            Assert.fail("baseline codegen must compile for compile-on qualification: " + e.getMessage() + "\n" + extra);
        }
    }
}