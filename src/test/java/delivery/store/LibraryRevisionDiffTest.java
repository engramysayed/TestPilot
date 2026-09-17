package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class LibraryRevisionDiffTest {

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
}
