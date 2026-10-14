package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class UserStoryBulkParserTest {

    private static final String SAMPLE = """
            US_ID,Title,Story
            US_001,Login,As a user I want to log in so that I can access my account.
            US_002,Cart,As a shopper I want to add items to my cart.
            """;

    @Test
    public void parse_extractsStories() {
        List<UserStoryBulkParser.UserStoryEntry> entries = UserStoryBulkParser.parse(SAMPLE);
        Assert.assertEquals(entries.size(), 2);
        Assert.assertEquals(entries.get(0).usId(), "US_001");
        Assert.assertEquals(entries.get(0).title(), "Login");
        Assert.assertTrue(entries.get(0).toPromptBlock().contains("log in"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void parse_rejectsMissingHeader() {
        UserStoryBulkParser.parse("Title,Story\nx,y");
    }

    @Test
    public void parse_optionalStructuredColumns() {
        String csv = """
                US_ID,Title,Story,AcceptanceCriteria,Role,Priority,Tags
                US_010,Login,As a user I want to log in,Show dashboard,user,P1,smoke
                """;
        var entries = UserStoryBulkParser.parse(csv);
        Assert.assertEquals(entries.size(), 1);
        Assert.assertTrue(entries.get(0).toPromptBlock().contains("Acceptance criteria:"));
        Assert.assertTrue(entries.get(0).toPromptBlock().contains("Role: user"));
    }
}
