package delivery.mobile.appium;

import delivery.mobile.MobileProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-job exclusive loopback port allocator (M1-06). Never exposes {@code 0.0.0.0}.
 * Isolation: two owners cannot share the same port; release only by owning key.
 */
public final class OwnedLoopbackPortAllocator {
    private final int rangeStart;
    private final int rangeEnd;
    /** port → ownerKey */
    private final ConcurrentHashMap<Integer, String> portOwners = new ConcurrentHashMap<>();
    /** ownerKey → ports held */
    private final ConcurrentHashMap<String, List<OwnedPort>> byOwner = new ConcurrentHashMap<>();

    public OwnedLoopbackPortAllocator(MobileProperties props) {
        MobileProperties p = props == null ? new MobileProperties() : props;
        this.rangeStart = Math.max(1024, p.getAppiumPortRangeStart());
        this.rangeEnd = Math.max(this.rangeStart, p.getAppiumPortRangeEnd());
    }

    public OwnedLoopbackPortAllocator(int rangeStart, int rangeEnd) {
        this.rangeStart = Math.max(1024, rangeStart);
        this.rangeEnd = Math.max(this.rangeStart, rangeEnd);
    }

    /**
     * Allocate one exclusive loopback port for {@code ownerKey} with the given role
     * (e.g. {@code server}, {@code system}, {@code chromedriver}).
     */
    public OwnedPort allocate(String ownerKey, String role) {
        String owner = requireOwner(ownerKey);
        synchronized (this) {
            for (int port = rangeStart; port <= rangeEnd; port++) {
                if (portOwners.putIfAbsent(port, owner) == null) {
                    OwnedPort owned = new OwnedPort(port, owner, role);
                    byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(owned);
                    return owned;
                }
            }
            throw new AppiumLifecycleException(AppiumErrorCode.PORT_EXHAUSTED,
                    "no free loopback ports in [" + rangeStart + "," + rangeEnd + "] for " + owner);
        }
    }

    /** Allocate the standard triple: Appium server + systemPort + chromedriverPort. */
    public PortBundle allocateBundle(String ownerKey) {
        OwnedPort server = allocate(ownerKey, "server");
        try {
            OwnedPort system = allocate(ownerKey, "system");
            OwnedPort chromedriver = allocate(ownerKey, "chromedriver");
            return new PortBundle(server, system, chromedriver);
        } catch (RuntimeException e) {
            releaseAll(ownerKey);
            throw e;
        }
    }

    public void releaseAll(String ownerKey) {
        String owner = requireOwner(ownerKey);
        synchronized (this) {
            List<OwnedPort> held = byOwner.remove(owner);
            if (held == null) {
                return;
            }
            for (OwnedPort p : held) {
                portOwners.remove(p.getPort(), owner);
            }
        }
    }

    public boolean isOwnedBy(int port, String ownerKey) {
        return Objects.equals(ownerKey, portOwners.get(port));
    }

    public int ownedCount(String ownerKey) {
        List<OwnedPort> held = byOwner.get(ownerKey);
        return held == null ? 0 : held.size();
    }

    public Map<Integer, String> snapshotOwners() {
        return Map.copyOf(portOwners);
    }

    private static String requireOwner(String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("ownerKey required");
        }
        return ownerKey;
    }

    public record PortBundle(OwnedPort server, OwnedPort system, OwnedPort chromedriver) {
        public PortBundle {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(system, "system");
            Objects.requireNonNull(chromedriver, "chromedriver");
        }
    }
}
