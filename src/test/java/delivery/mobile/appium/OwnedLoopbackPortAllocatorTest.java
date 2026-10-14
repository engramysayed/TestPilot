package delivery.mobile.appium;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * M1-06: owned-port isolation — two owners never share loopback ports; bind is 127.0.0.1.
 */
public class OwnedLoopbackPortAllocatorTest {

    @Test
    public void twoOwnersGetDistinctPorts() {
        OwnedLoopbackPortAllocator alloc = new OwnedLoopbackPortAllocator(4700, 4710);
        OwnedLoopbackPortAllocator.PortBundle a = alloc.allocateBundle("lease_a@1");
        OwnedLoopbackPortAllocator.PortBundle b = alloc.allocateBundle("lease_b@1");
        Assert.assertEquals(a.server().getBindAddress(), OwnedPort.LOOPBACK);
        Assert.assertEquals(b.server().getBindAddress(), OwnedPort.LOOPBACK);
        Assert.assertNotEquals(a.server().getPort(), b.server().getPort());
        Assert.assertNotEquals(a.system().getPort(), b.system().getPort());
        Assert.assertNotEquals(a.chromedriver().getPort(), b.chromedriver().getPort());
        Assert.assertTrue(alloc.isOwnedBy(a.server().getPort(), "lease_a@1"));
        Assert.assertFalse(alloc.isOwnedBy(a.server().getPort(), "lease_b@1"));
    }

    @Test
    public void releaseAllFreesPortsForReuse() {
        OwnedLoopbackPortAllocator alloc = new OwnedLoopbackPortAllocator(4800, 4805);
        OwnedLoopbackPortAllocator.PortBundle a = alloc.allocateBundle("owner1");
        int serverPort = a.server().getPort();
        alloc.releaseAll("owner1");
        Assert.assertEquals(alloc.ownedCount("owner1"), 0);
        OwnedPort again = alloc.allocate("owner2", "server");
        Assert.assertEquals(again.getPort(), serverPort);
    }

    @Test
    public void exhaustedRangeFailsWithoutLeakingPartialBundle() {
        OwnedLoopbackPortAllocator alloc = new OwnedLoopbackPortAllocator(4900, 4901); // only 2 ports
        try {
            alloc.allocateBundle("big");
            Assert.fail("expected PORT_EXHAUSTED");
        } catch (AppiumLifecycleException e) {
            Assert.assertEquals(e.getCode(), AppiumErrorCode.PORT_EXHAUSTED);
        }
        Assert.assertEquals(alloc.ownedCount("big"), 0);
        Assert.assertTrue(alloc.snapshotOwners().isEmpty());
    }
}
