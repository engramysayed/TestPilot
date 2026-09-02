package delivery.job;

import delivery.codegen.CodeWriter;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RevisePhaseTest {
    @Test
    public void flagsRemainingIntentsOnPartial() {
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "",
                "1. Click checkout\n2. Click finish\n3. Confirm thank you",
                "ok", "", "");
        ProvenStep one = new ProvenStep(
                "TC1", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        TcDraft draft = new TcDraft(
                "TC1", "t", tc.steps(), tc.expectedResult(),
                TcDraftStatus.PARTIAL, List.of(one), List.of(), false,
                1, "Click finish", "No DOM candidate", "", 0, "https://x/cart");
        List<String> issues = RevisePhase.findIssues(draft, tc);
        Assert.assertTrue(issues.stream().anyMatch(s -> s.contains("Remaining")), issues.toString());
    }

    @Test
    public void writesReviseNotesFile() throws Exception {
        Path temp = Files.createTempDirectory("revise");
        Path templates = Path.of("customer-framework-template/templates");
        CodeWriter writer = new CodeWriter(templates);
        ProvenStep one = new ProvenStep(
                "TC1", "Cart", "elementAction", "click",
                "data-test", "checkout", "", "", "", true, "intent:CLICK");
        writer.write(temp, List.of(new TcOutcome(
                "TC1", TcStatus.PARTIAL, List.of(one), "blocked", null)));
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "",
                "1. Click checkout\n2. Click finish",
                "ok", "", "");
        TcDraft draft = new TcDraft(
                "TC1", "t", tc.steps(), tc.expectedResult(),
                TcDraftStatus.PARTIAL, List.of(one), List.of(), false,
                1, "Click finish", "blocked", "", 0, "https://x/cart");
        new RevisePhase().revise(temp, List.of(draft), List.of(tc));
        Path notes = temp.resolve("docs/REVISE_NOTES.md");
        Assert.assertTrue(Files.exists(notes));
        String md = Files.readString(notes);
        Assert.assertTrue(md.contains("TC1"));
        String todo = Files.readString(temp.resolve("src/test/java/project/tests/todo/TC1TodoTest.java"));
        Assert.assertTrue(todo.contains("// REVIEW:"));
    }
}
