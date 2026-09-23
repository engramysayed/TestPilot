package delivery.assertions;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CaptureCompareTest {

    @Test
    public void exactTextCapturesWholeElementAndRejectsAmbiguousElements() {
        CaptureCompare.ExtractResult one = CaptureCompare.extract(List.of("Order KLA-1001"), "exact text");
        Assert.assertTrue(one.ok(), one.error());
        Assert.assertEquals(one.value(), "Order KLA-1001");

        CaptureCompare.ExtractResult none = CaptureCompare.extract(List.of(), "exactText");
        Assert.assertFalse(none.ok());
        Assert.assertTrue(none.error().toLowerCase().contains("missing")
                || none.error().toLowerCase().contains("no element"), none.error());

        CaptureCompare.ExtractResult many = CaptureCompare.extract(
                List.of("Order KLA-1001", "Order KLA-1002"), "exactText");
        Assert.assertFalse(many.ok());
        Assert.assertTrue(many.error().toLowerCase().contains("ambiguous"), many.error());
    }

    @Test
    public void regexExtractionRequiresExactlyOneMatch() {
        CaptureCompare.ExtractResult one = CaptureCompare.extract(
                List.of("Thanks Order KLA-1044 done"), "regex:Order KLA-[A-Za-z0-9]+");
        Assert.assertTrue(one.ok(), one.error());
        Assert.assertEquals(one.value(), "Order KLA-1044");

        CaptureCompare.ExtractResult two = CaptureCompare.extract(
                List.of("Order KLA-1 and Order KLA-2"), "regex:Order KLA-[A-Za-z0-9]+");
        Assert.assertFalse(two.ok());
        Assert.assertTrue(two.error().toLowerCase().contains("ambiguous"), two.error());
    }

    @Test
    public void exactCompareDoesNotTreatKla1001AsPrefixOfKla10010() {
        Assert.assertTrue(CaptureCompare.anyExactEquals(List.of("Order KLA-1001"), "Order KLA-1001"));
        Assert.assertFalse(CaptureCompare.anyExactEquals(List.of("Order KLA-10010"), "Order KLA-1001"));
        Assert.assertFalse(CaptureCompare.anyExactEquals(
                List.of("Order KLA-10010", "Order KLA-10011"), "Order KLA-1001"));
        Assert.assertTrue(CaptureCompare.anyExactEquals(
                List.of("Order KLA-10010", "Order KLA-1001"), "Order KLA-1001"));
    }

    @Test
    public void missingCaptureValueFailsCompare() {
        CaptureCompare.CompareResult missing = CaptureCompare.compareExact(List.of("Order KLA-1001"), null);
        Assert.assertFalse(missing.ok());
        Assert.assertTrue(missing.error().toLowerCase().contains("missing"), missing.error());
    }

    @Test
    public void signedOutUsesExpectedVisibilityOrExactTextNotSessionExpiredPhrase() {
        CaptureCompare.CompareResult empty = CaptureCompare.signedOut(List.of(""), true, "empty");
        Assert.assertTrue(empty.ok(), empty.error());
        CaptureCompare.CompareResult hidden = CaptureCompare.signedOut(List.of(), false, "empty");
        Assert.assertTrue(hidden.ok(), hidden.error());
        CaptureCompare.CompareResult stillIn = CaptureCompare.signedOut(
                List.of("buyer.a@example.test"), true, "empty");
        Assert.assertFalse(stillIn.ok());
        CaptureCompare.CompareResult text = CaptureCompare.signedOut(
                List.of("Not signed in"), true, "text:Not signed in");
        Assert.assertTrue(text.ok(), text.error());
        CaptureCompare.CompareResult overlay = CaptureCompare.signedOut(
                List.of("Session expired — sign in again"), true, "empty");
        Assert.assertFalse(overlay.ok());
    }
}
