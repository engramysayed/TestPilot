package delivery.portal.service;

import delivery.excel.ExcelTcReader;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import delivery.store.StaleLibraryRevisionException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class GeneratedWorkbookRevisionFlowTest {
    @Test public void generatedBatchAppendsAndConflictsPreserveTheLibrary() throws Exception {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(Files.createTempDirectory("generation-append-").toString());
        GeneratedWorkbookService service = new GeneratedWorkbookService(props);
        String projectId = "project";
        service.saveFromCases(projectId, List.of(tc("TC_01", "Existing")), "IMPORT", "owner");
        Path csv = Files.createTempFile("generation-batch-", ".csv");
        try {
            Files.writeString(csv, delivery.excel.GeneratedTcCsvParser.toCsv(List.of(tc("TC_02", "New"))));
            service.saveFromCsvFile(projectId, csv, "GENERATE_BATCH", "job-new");
            Assert.assertEquals(service.readCases(projectId).stream().map(ManualTestCase::tcId).toList(), List.of("TC_01", "TC_02"));
            String head = service.headRevisionId(projectId).orElseThrow();
            byte[] latest = Files.readAllBytes(service.generatedDir(projectId).resolve("latest.xlsx"));
            Files.writeString(csv, delivery.excel.GeneratedTcCsvParser.toCsv(List.of(tc("TC_03", "Other"), tc("TC_01", "Overwrite"))));
            try { service.saveFromCsvFile(projectId, csv, "GENERATE_BATCH", "job-conflict"); Assert.fail("Expected conflict"); }
            catch (IllegalArgumentException e) { Assert.assertTrue(e.getMessage().startsWith("LIBRARY_ID_CONFLICT:")); }
            Assert.assertEquals(service.headRevisionId(projectId).orElseThrow(), head);
            Assert.assertEquals(Files.readAllBytes(service.generatedDir(projectId).resolve("latest.xlsx")), latest);
            Assert.assertEquals(service.readCases(projectId).get(0).title(), "Existing");
            Files.writeString(csv, delivery.excel.GeneratedTcCsvParser.toCsv(List.of()));
            try { service.saveFromCsvFile(projectId, csv, "GENERATE_BATCH", "empty"); Assert.fail("Expected empty batch rejection"); }
            catch (IllegalArgumentException e) { Assert.assertNotNull(e.getMessage()); }
            Assert.assertEquals(service.headRevisionId(projectId).orElseThrow(), head);
        } finally { Files.deleteIfExists(csv); }
    }

    @Test public void restoreCopiesAllCasesAndDeletionHasItsOwnHistoryLabel() throws Exception {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(Files.createTempDirectory("restore-all-").toString());
        GeneratedWorkbookService service = new GeneratedWorkbookService(props);
        service.saveFromCases("project", List.of(tc("TC_01", "One"), tc("TC_02", "Two"), tc("TC_03", "Three")), "IMPORT", "owner");
        String original = service.headRevisionId("project").orElseThrow();
        service.deleteCases("project", List.of("TC_03"));
        service.restoreRevision("project", original, "owner");
        Assert.assertEquals(service.readCases("project").size(), 3);
        service.updateCaseFields("project", "TC_01", Map.of("title", "Edited"), "https://example.test");
        Assert.assertEquals(service.describe("project").orElseThrow().get("source"), "CASE_EDIT");
        service.deleteCases("project", List.of("TC_03"));
        Assert.assertEquals(service.describe("project").orElseThrow().get("source"), "CASE_DELETE");
        Assert.assertEquals(service.readRevisionCases("project", original).size(), 3);
    }

    private GeneratedWorkbookService service() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot("./target/test-delivery-store-lib-rev-flow");
        return new GeneratedWorkbookService(props);
    }

    private static ManualTestCase tc(String id, String title) {
        return new ManualTestCase(id, title, "", "1. Open page", "Page opens", "P1", "smoke", "", "", "AUTOMATE");
    }

    @Test
    public void restoreCreatesNewHeadAndRewritesLatestWorkbook() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-restore-head";
        service.saveFromCases(projectId, List.of(tc("TC_01", "First")), "GENERATE", "author-a");
        String firstId = String.valueOf(service.describe(projectId).orElseThrow().get("revisionId"));
        service.saveFromCases(projectId, List.of(tc("TC_01", "Second")), "EDIT", "author-b", null, firstId);

        Map<String, Object> restored = service.restoreRevision(projectId, firstId, "author-c");
        Assert.assertNotEquals(restored.get("revisionId"), firstId);
        Assert.assertEquals(service.readCases(projectId).get(0).title(), "First");
        Assert.assertTrue(String.valueOf(restored.get("source")).startsWith("restore:"));
    }

    @Test
    public void copyRevisionForJobSurvivesLaterLibraryEdit() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-pin-copy";
        service.saveFromCases(projectId, List.of(tc("TC_01", "Pinned")), "GENERATE", "author-a");
        String rev = String.valueOf(service.describe(projectId).orElseThrow().get("revisionId"));
        Path jobCopy = service.copyRevisionForJob(projectId, rev);

        service.saveFromCases(projectId, List.of(tc("TC_01", "Later edit")), "EDIT", "author-b", null, rev);

        Assert.assertEquals(new ExcelTcReader().read(jobCopy).get(0).title(), "Pinned");
        Assert.assertEquals(service.readRevisionCases(projectId, rev).get(0).title(), "Pinned");
        Assert.assertEquals(service.readCases(projectId).get(0).title(), "Later edit");
        Files.deleteIfExists(jobCopy);
    }

    @Test
    public void staleBaseRevisionIsRejectedOnCaseEdit() throws Exception {
        GeneratedWorkbookService service = service();
        String projectId = "proj-stale-edit";
        service.saveFromCases(projectId, List.of(tc("TC_01", "Original")), "GENERATE", "author-a");
        String first = String.valueOf(service.describe(projectId).orElseThrow().get("revisionId"));
        service.saveFromCases(projectId, List.of(tc("TC_01", "Second")), "EDIT", "author-b", null, first);
        try {
            service.updateCaseFields(projectId, "TC_01", Map.of("title", "Stale"), "https://example.test", first);
            Assert.fail("expected stale conflict");
        } catch (StaleLibraryRevisionException e) {
            Assert.assertEquals(e.baseRevisionId(), first);
        }
        Assert.assertEquals(service.readCases(projectId).get(0).title(), "Second");
    }
}
