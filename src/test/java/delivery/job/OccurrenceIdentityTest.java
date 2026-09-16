package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

/** F13 / P1-04: repeated Call-before executions keep distinct evidence folders. */
public class OccurrenceIdentityTest {

    @Test
    public void repeatedOccurrencesAreDistinctAndKeepLogicalId() {
        String first = OccurrenceIdentity.folder("TC_CART", 1);
        String second = OccurrenceIdentity.folder("TC_CART", 2);
        Assert.assertNotEquals(first, second);
        Assert.assertTrue(first.startsWith("TC_CART"), first);
        Assert.assertTrue(second.startsWith("TC_CART"), second);
        Assert.assertTrue(first.contains("occ"), first);
        Assert.assertTrue(second.contains("occ"), second);
    }

    @Test
    public void sanitizesUnsafeIdsTheSameWayAsIrFiles() {
        String folder = OccurrenceIdentity.folder("TC/1", 1);
        Assert.assertFalse(folder.contains("/"), folder);
        Assert.assertTrue(folder.contains("occ"), folder);
    }
}
