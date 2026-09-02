package delivery.portal.service;

import delivery.codegen.ProvenStep;
import delivery.excel.ExcelTcReader;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GeneratedWorkbookHealPatchTest {
    private static GeneratedWorkbookService svc(Path root) {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(root.toString());
        return new GeneratedWorkbookService(props);
    }

    private static ProvenStep clearEmail() {
        return new ProvenStep("TC_01", "Page", "elementAction", "clear", "css", "input[name=email]",
                "", "", "", true, "heal:recovery");
    }

    @Test
    public void apply_savesLeaveEmptyAndBlanksTestData() throws Exception {
        Path root = Files.createTempDirectory("keel-heal-patch-ok");
        GeneratedWorkbookService svc = svc(root);
        ManualTestCase tc = new ManualTestCase(
                "TC_01", "Empty email login", "",
                "1. Enter in the Email field\n2. Click Login",
                "Shows error \"Invalid email\"", "P1", "", "",
                "typed@x.com\n", "EXECUTE");
        ManualTestCase other = new ManualTestCase(
                "TC_02", "ok", "", "1. Open /", "ok", "P1", "", "", "", "EXECUTE");
        svc.saveFromCases("p1", List.of(tc, other), "IMPORT", "t");

        GeneratedWorkbookService.HealPatchApplyResult result = svc.applyHealRecoveryPatch(
                "p1", "TC_01", List.of(clearEmail()), List.of("Keep Leave-empty"), "https://example.test");
        Assert.assertTrue(result.excelSaved(), result.reason());
        ManualTestCase saved = new ExcelTcReader().read(svc.requireExcel("p1")).stream()
                .filter(c -> "TC_01".equals(c.tcId())).findFirst().orElseThrow();
        Assert.assertTrue(saved.steps().toLowerCase().contains("leave"));
        Assert.assertTrue(saved.testData().split("\n", -1)[0].isBlank());
        ManualTestCase sibling = new ExcelTcReader().read(svc.requireExcel("p1")).stream()
                .filter(c -> "TC_02".equals(c.tcId())).findFirst().orElseThrow();
        Assert.assertEquals(sibling.title(), "ok");
        @SuppressWarnings("unchecked")
        Map<String, Object> byTc = (Map<String, Object>) svc.describe("p1").orElseThrow()
                .get("automationNotesByTc");
        Assert.assertTrue(((List<?>) byTc.get("TC_01")).contains("Keep Leave-empty"));
        String coverage = String.valueOf(svc.describe("p1").orElseThrow().get("coverageNotes"));
        Assert.assertTrue(coverage.contains("Heal notes") && coverage.contains("Keep Leave-empty"));
    }

    @Test
    public void apply_missingWorkbook_skipsExcel() throws Exception {
        Path root = Files.createTempDirectory("keel-heal-patch-miss");
        GeneratedWorkbookService svc = svc(root);
        GeneratedWorkbookService.HealPatchApplyResult result = svc.applyHealRecoveryPatch(
                "none", "TC1", List.of(clearEmail()), List.of("n"), "https://example.test");
        Assert.assertTrue(result.skipped());
        Assert.assertFalse(result.excelSaved());
    }
}
