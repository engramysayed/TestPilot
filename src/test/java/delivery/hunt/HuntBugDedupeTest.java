package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HuntBugDedupeTest {
    @Test
    public void dedupesSameTitle() {
        List<Map<String, Object>> all = new ArrayList<>();
        Assert.assertTrue(HuntBugDedupe.addUnique(all, Map.of("title", "Copy mismatch TC_04", "severity", "medium")));
        Assert.assertFalse(HuntBugDedupe.addUnique(all, Map.of("title", "Copy mismatch TC_04", "severity", "medium")));
        Assert.assertEquals(all.size(), 1);
        Assert.assertEquals(all.get(0).get("dupCount"), 2);
    }
}
