package delivery.portal.service;

import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GeneratedWorkbookCoverageNotesTest {

    @Test
    public void updateCoverageNotes_persistsInMetaAndDescribe() throws Exception {
        Path root = Files.createTempDirectory("keel-coverage-notes");
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(root.toString());
        GeneratedWorkbookService svc = new GeneratedWorkbookService(props);
        String projectId = "p1";
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "", "1. Open /", "ok", "P1", "", "", "", "EXECUTE");
        svc.saveFromCases(projectId, List.of(tc), "IMPORT", "test");
        Map<String, Object> updated = svc.updateCoverageNotes(projectId, "Covered login negatives.");
        Assert.assertEquals(String.valueOf(updated.get("coverageNotes")), "Covered login negatives.");
        Map<String, Object> described = svc.describe(projectId).orElseThrow();
        Assert.assertEquals(String.valueOf(described.get("coverageNotes")), "Covered login negatives.");
    }

    @Test
    public void saveFromCases_preservesCoverageNotes() throws Exception {
        Path root = Files.createTempDirectory("keel-coverage-preserve");
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(root.toString());
        GeneratedWorkbookService svc = new GeneratedWorkbookService(props);
        String projectId = "p2";
        ManualTestCase tc = new ManualTestCase(
                "TC1", "t", "", "1. Open /", "ok", "P1", "", "", "", "EXECUTE");
        svc.saveFromCases(projectId, List.of(tc), "IMPORT", "test");
        svc.updateCoverageNotes(projectId, "Keep me");
        ManualTestCase tc2 = new ManualTestCase(
                "TC1", "t2", "", "1. Open /x", "ok", "P1", "", "", "", "EXECUTE");
        svc.saveFromCases(projectId, List.of(tc2), "IMPORT", "test2");
        Assert.assertEquals(String.valueOf(svc.describe(projectId).orElseThrow().get("coverageNotes")), "Keep me");
    }
}
