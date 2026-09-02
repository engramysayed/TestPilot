package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class TcImportRepairTest {

    @Test
    public void stripMarkdownFences_removesJsonFence() {
        String raw = "```json\n{\"testCases\":[]}\n```";
        Assert.assertTrue(TcImportRepair.stripMarkdownFences(raw).trim().startsWith("{"));
    }

    @Test
    public void repairMultilineField_expandsLiteralBackslashN() {
        String in = "1. Open login\\n2. Enter in the Email or phone field";
        String out = TcImportRepair.repairMultilineField(in);
        Assert.assertTrue(out.contains("\n"));
        Assert.assertFalse(out.contains("\\n"));
    }

    @Test
    public void repairCases_expandsLiteralBackslashNInSteps() {
        ManualTestCase tc = new ManualTestCase(
                "TC_01", "t", "", "1. A\\n2. B", "1. ok", "", "", "", "", "");
        List<ManualTestCase> out = TcImportRepair.repairCases(List.of(tc));
        Assert.assertTrue(out.get(0).steps().contains("\n"));
    }
}
