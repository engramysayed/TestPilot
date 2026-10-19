package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class ReviewBatchPolicyTest {

    @Test
    public void smallSuite_oneChunkEvenWhenNIsSmaller() {
        List<String> ids = List.of("TC_1", "TC_2", "TC_3", "TC_4", "TC_5");
        List<List<String>> chunks = ReviewBatchPolicy.chunks(ids, 2);
        Assert.assertEquals(chunks.size(), 1);
        Assert.assertEquals(chunks.get(0), ids);
        Assert.assertEquals(ReviewBatchPolicy.capForPostedCount(5, 2), 5);
        Assert.assertFalse(ReviewBatchPolicy.exceedsCap(5, 2));
    }

    @Test
    public void twentyWithN7_threeChunks() {
        List<String> ids = numbered(20);
        List<List<String>> chunks = ReviewBatchPolicy.chunks(ids, 7);
        Assert.assertEquals(chunks.size(), 3);
        Assert.assertEquals(chunks.get(0).size(), 7);
        Assert.assertEquals(chunks.get(1).size(), 7);
        Assert.assertEquals(chunks.get(2).size(), 6);
        Assert.assertEquals(ReviewBatchPolicy.capForPostedCount(20, 7), 7);
        Assert.assertTrue(ReviewBatchPolicy.exceedsCap(20, 7));
        Assert.assertFalse(ReviewBatchPolicy.exceedsCap(7, 7));
    }

    @Test
    public void clampN_defaultAndMax() {
        Assert.assertEquals(ReviewBatchPolicy.clampN(0), 50);
        Assert.assertEquals(ReviewBatchPolicy.clampN(-3), 50);
        Assert.assertEquals(ReviewBatchPolicy.clampN(99), 50);
        Assert.assertEquals(ReviewBatchPolicy.clampN(7), 7);
        Assert.assertEquals(ReviewBatchPolicy.clampN(1), 1);
        Assert.assertEquals(ReviewBatchPolicy.clampN(50), 50);
    }

    private static List<String> numbered(int n) {
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ids.add("TC_" + i);
        }
        return ids;
    }
}
