package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SuiteTestCaseLibraryServiceTest {

    @Test
    public void filter_matchesIdOrTitle() {
        List<Map<String, Object>> items = List.of(
                row("TC_LOGIN", "Login flow"),
                row("TC_02", "Checkout"));
        List<Map<String, Object>> byId = SuiteTestCaseLibraryService.filter("login", items);
        Assert.assertEquals(byId.size(), 1);
        Assert.assertEquals(byId.get(0).get("id"), "TC_LOGIN");

        List<Map<String, Object>> byTitle = SuiteTestCaseLibraryService.filter("check", items);
        Assert.assertEquals(byTitle.size(), 1);
        Assert.assertEquals(byTitle.get(0).get("id"), "TC_02");

        Assert.assertEquals(SuiteTestCaseLibraryService.filter("  ", items).size(), 2);
    }

    @Test
    public void parseOffset_and_limit_bounds() {
        Assert.assertEquals(SuiteTestCaseLibraryService.parseOffset(null), 0);
        Assert.assertEquals(SuiteTestCaseLibraryService.parseOffset("bad"), 0);
        Assert.assertEquals(SuiteTestCaseLibraryService.parseOffset("12"), 12);
        Assert.assertEquals(SuiteTestCaseLibraryService.normalizeLimit(null), SuiteTestCaseLibraryService.DEFAULT_LIMIT);
        Assert.assertEquals(SuiteTestCaseLibraryService.normalizeLimit(0), SuiteTestCaseLibraryService.DEFAULT_LIMIT);
        Assert.assertEquals(SuiteTestCaseLibraryService.normalizeLimit(9999), SuiteTestCaseLibraryService.MAX_LIMIT);
    }

    private static Map<String, Object> row(String id, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        return m;
    }
}
