package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class LibraryRevisionDiffTest {

    @Test
    public void quotedMultilineFieldsAndSetupChangesRemainReviewable() {
        String header = "TC_ID,Title,Steps,ExpectedResult,CallBefore,TestData\n";
        String before = header + "TC_1,\"Cart, checkout\",\"Click \"\"Add\"\"\nOpen cart\",Ready,TC_LOGIN,qty=1\n";
        String after = header + "TC_1,\"Cart, checkout\",\"Click \"\"Add\"\"\nOpen checkout\",Ready,TC_CART,qty=2\n";
        var diff = LibraryRevisionDiff.compareCsv(before.getBytes(StandardCharsets.UTF_8),
                after.getBytes(StandardCharsets.UTF_8));
        Assert.assertTrue(diff.added().isEmpty());
        Assert.assertTrue(diff.removed().isEmpty());
        var fields = diff.changed().get("TC_1");
        Assert.assertEquals(fields.get("Steps.before"), "Click \"Add\"\nOpen cart");
        Assert.assertEquals(fields.get("Steps.after"), "Click \"Add\"\nOpen checkout");
        Assert.assertEquals(fields.get("CallBefore.after"), "TC_CART");
        Assert.assertEquals(fields.get("TestData.after"), "qty=2");
        Assert.assertFalse(fields.containsKey("Title.before"));
    }

    @Test
    public void fieldLevelDiffShowsAddedChangedRemovedCases() {
        byte[] before = """
                TC_ID,Title,Steps,ExpectedResult
                TC_1,Login,Open login,Dashboard
                TC_2,Cart,Add item,Cart has 1
                """.getBytes(StandardCharsets.UTF_8);
        byte[] after = """
                TC_ID,Title,Steps,ExpectedResult
                TC_1,Login,Open login,Welcome
                TC_3,Pay,Place order,Confirmed
                """.getBytes(StandardCharsets.UTF_8);
        LibraryRevisionDiff.Result diff = LibraryRevisionDiff.compareCsv(before, after);
        Assert.assertEquals(diff.added(), List.of("TC_3"));
        Assert.assertEquals(diff.removed(), List.of("TC_2"));
        Assert.assertEquals(diff.changed().size(), 1);
        Map<String, String> fields = diff.changed().get("TC_1");
        Assert.assertEquals(fields.get("ExpectedResult.before"), "Dashboard");
        Assert.assertEquals(fields.get("ExpectedResult.after"), "Welcome");
        Assert.assertFalse(fields.containsKey("Title.before"));
    }

    @Test
    public void filterKeepsOnlyRequestedChangeKindsAndFields() {
        byte[] before = """
                TC_ID,Title,Steps,ExpectedResult
                TC_1,Login,Open login,Dashboard
                TC_2,Cart,Add item,Cart has 1
                """.getBytes(StandardCharsets.UTF_8);
        byte[] after = """
                TC_ID,Title,Steps,ExpectedResult
                TC_1,Login,Open login,Welcome
                TC_3,Pay,Place order,Confirmed
                """.getBytes(StandardCharsets.UTF_8);
        LibraryRevisionDiff.Result full = LibraryRevisionDiff.compareCsv(before, after);

        LibraryRevisionDiff.Result changed = full.filter("changed", null);
        Assert.assertTrue(changed.added().isEmpty());
        Assert.assertTrue(changed.removed().isEmpty());
        Assert.assertEquals(changed.changed().keySet(), java.util.Set.of("TC_1"));

        LibraryRevisionDiff.Result added = full.filter("added", null);
        Assert.assertEquals(added.added(), List.of("TC_3"));
        Assert.assertTrue(added.removed().isEmpty());
        Assert.assertTrue(added.changed().isEmpty());

        LibraryRevisionDiff.Result titleOnly = full.filter("changed", "Title");
        Assert.assertTrue(titleOnly.changed().isEmpty(), "TC_1 title did not change");

        LibraryRevisionDiff.Result expected = full.filter("changed", "ExpectedResult");
        Assert.assertEquals(expected.changed().get("TC_1").get("ExpectedResult.after"), "Welcome");
    }
}
