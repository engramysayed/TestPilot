package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class RequirementTraceTest {

    @Test
    public void jsonRequirementRefBecomesTags() {
        String json = "{\"testCases\":[{\"tcId\":\"TC_01\",\"title\":\"t\",\"steps\":\"1. Click the \\\"Save\\\" button\","
                + "\"expectedResult\":\"1. Saved\",\"tags\":\"negative\",\"requirementRef\":\"US-1, AC-2\"}],"
                + "\"coverageNotes\":\"\"}";
        ManualTestCase tc = GeneratedTcJsonParser.parse(json).cases().get(0);
        Assert.assertEquals(tc.tags(), "negative, req:US-1, req:AC-2");
        Assert.assertEquals(RequirementTrace.refsOf(tc.tags()), List.of("US-1", "AC-2"));
    }

    @Test
    public void matrixGroupsCasesByStoryAndListsUntraced() {
        ManualTestCase a = new ManualTestCase("TC_01", "", "", "", "", "", "req:US-1");
        ManualTestCase b = new ManualTestCase("TC_02", "", "", "", "", "", "negative, req:US-1, req:US-2");
        ManualTestCase c = new ManualTestCase("TC_03", "", "", "", "", "", "smoke");
        Map<String, List<String>> m = RequirementTrace.matrix(List.of(a, b, c));
        Assert.assertEquals(m.get("US-1"), List.of("TC_01", "TC_02"));
        Assert.assertEquals(m.get("US-2"), List.of("TC_02"));
        Assert.assertEquals(m.get(""), List.of("TC_03"));
    }

    @Test
    public void noDuplicateTags() {
        Assert.assertEquals(RequirementTrace.withRequirementTags("req:US-1", "US-1"), "req:US-1");
    }
}
