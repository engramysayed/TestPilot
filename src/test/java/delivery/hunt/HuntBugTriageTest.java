package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class HuntBugTriageTest {
    @Test
    public void applyDropsExplicitTitles() {
        List<Map<String, Object>> bugs = List.of(
                Map.of("title", "Real defect", "severity", "high"),
                Map.of("title", "Unexpected redirect to login/error URL", "severity", "major"));
        HuntBugTriage.Decision d = HuntBugTriage.parse("""
                {"keep":[{"title":"Real defect","reason":"product"}],
                 "drop":[{"title":"Unexpected redirect to login/error URL","reason":"oracle_false"}],
                 "merge":[]}
                """);
        List<Map<String, Object>> kept = HuntBugTriage.apply(bugs, d);
        Assert.assertEquals(kept.size(), 1);
        Assert.assertEquals(kept.get(0).get("title"), "Real defect");
    }
}
