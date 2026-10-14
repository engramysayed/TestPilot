package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CoverageTechniqueCheckTest {
    @Test
    public void warnsWhenNotesEmpty() {
        ManualTestCase tc = new ManualTestCase(
                "TC_01", "Happy path only", "", "1. Open home\n2. Click OK", "Done", "P3", "smoke",
                "", "", "AUTOMATE", "");
        List<CoverageTechniqueCheck.Warning> warnings = CoverageTechniqueCheck.warn("", List.of(tc));
        Assert.assertFalse(warnings.isEmpty());
        Assert.assertEquals(warnings.get(0).technique(), "coverageNotes");
    }

    @Test
    public void noWarnWhenNotesMentionTechniquesAndCasesMatch() {
        ManualTestCase neg = new ManualTestCase(
                "TC_01", "Invalid login", "", "1. Enter wrong password\n2. Confirm error", "Error", "P1", "negative",
                "", "", "EXECUTE", "");
        List<CoverageTechniqueCheck.Warning> warnings = CoverageTechniqueCheck.warn(
                "Applied negative paths and equivalence partitioning. Boundary not in scope.",
                List.of(neg));
        boolean hasNegative = warnings.stream().anyMatch(w -> w.technique().contains("negative"));
        Assert.assertFalse(hasNegative);
    }
}
