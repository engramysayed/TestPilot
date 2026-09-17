package delivery.hunt;

import delivery.excel.ManualTestCase;
import delivery.store.StaleLibraryRevisionException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class HuntPromotionTest {

    @Test
    public void previewMintsValidIdsAndFlagsExactAndSemanticDuplicates() {
        String json = "[{\"title\":\"Login\",\"steps\":\"Open login\",\"expected\":\"Home\"},"
                + "{\"title\":\"Login\",\"steps\":\"Open sign-in\",\"expected\":\"Dashboard\"}]";
        Map<String, ManualTestCase> library = HuntPromotion.index(List.of(
                new ManualTestCase("TC_01", "Login", "", "Open login", "Home", "P1", "smoke")));
        HuntPromotion.Preview preview = HuntPromotion.preview(json, library, "rev_1", "rev_1");
        Assert.assertEquals(preview.cases().size(), 2);
        Assert.assertTrue(preview.cases().get(0).tcId().startsWith("TC_HUNT_"));
        Assert.assertTrue(preview.duplicates().stream()
                .anyMatch(d -> d.kind() == HuntPromotion.DuplicateKind.EXACT));
        Assert.assertTrue(preview.duplicates().stream()
                .anyMatch(d -> d.kind() == HuntPromotion.DuplicateKind.SEMANTIC_SUGGESTED));
        Assert.assertEquals(HuntPromotion.provenance("job_hunt"), "HUNT_PROMOTE:job_hunt");
    }

    @Test
    public void stalePinnedRevisionMustRebase() {
        Assert.assertThrows(StaleLibraryRevisionException.class, () ->
                HuntPromotion.preview("[]", Map.of(), "rev_old", "rev_new"));
    }

    @Test
    public void applyEditsOverlaysReviewedStepsWithoutAddingUnsignedCases() {
        List<ManualTestCase> base = List.of(
                new ManualTestCase("TC_HUNT_001", "Login", "", "Open login", "Home", "P2", "hunt"));
        List<ManualTestCase> edited = HuntPromotion.applyEdits(base, List.of(
                new HuntPromotion.CaseEdit("TC_HUNT_001", "Login reviewed", "Open sign-in", "Dashboard"),
                new HuntPromotion.CaseEdit("TC_HUNT_999", "Injected", "Skip review", "Should not land")));
        Assert.assertEquals(edited.size(), 1);
        Assert.assertEquals(edited.get(0).title(), "Login reviewed");
        Assert.assertEquals(edited.get(0).steps(), "Open sign-in");
        Assert.assertEquals(edited.get(0).expectedResult(), "Dashboard");
        Assert.assertEquals(edited.get(0).tcId(), "TC_HUNT_001");
    }
}
