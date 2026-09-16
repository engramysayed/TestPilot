package delivery.ir;

import org.testng.Assert;
import org.testng.annotations.Test;

/** F05 / P1-05: one TC ID contract and collision-resistant storage keys. */
public class TcIdentityTest {

    @Test
    public void acceptsQualityGateIds() {
        TcIdentity.requireValid("TC_1");
        TcIdentity.requireValid("TC_01");
        TcIdentity.requireValid("TC_CART");
        TcIdentity.requireValid("TC_DQ_01");
    }

    @Test
    public void rejectsSlashAndBareTokensWithTheSameMessage() {
        String slash = TcIdentity.invalidMessage("TC/1");
        Assert.assertTrue(slash.contains("TC/1"), slash);
        Assert.assertTrue(slash.contains("TC_<"), slash);
        try {
            TcIdentity.requireValid("TC/1");
            Assert.fail("expected invalid TC/1");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), slash);
        }
        try {
            TcIdentity.requireValid("TC1");
            Assert.fail("expected invalid TC1");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), TcIdentity.invalidMessage("TC1"));
        }
    }

    @Test
    public void storageKeysDoNotCollideAfterFilenameNormalization() {
        Assert.assertNotEquals(TcIdentity.storageKey("TC/1"), TcIdentity.storageKey("TC_1"));
        Assert.assertNotEquals(TcIdentity.storageKey("TC/1"), TcDraftStore.safeFileName("TC/1"));
        Assert.assertFalse(TcIdentity.storageKey("TC/1").contains("/"));
        Assert.assertFalse(TcIdentity.storageKey("TC/1").contains("\\"));
        Assert.assertEquals(TcIdentity.displayId("TC_CART"), "TC_CART");
    }

    @Test
    public void occurrenceFoldersStayDistinctForTheSameLogicalId() {
        String a = delivery.job.OccurrenceIdentity.folder("TC_CART", 1);
        String b = delivery.job.OccurrenceIdentity.folder("TC_CART", 2);
        Assert.assertNotEquals(a, b);
        Assert.assertTrue(a.contains(TcIdentity.storageKey("TC_CART")), a);
    }
}
