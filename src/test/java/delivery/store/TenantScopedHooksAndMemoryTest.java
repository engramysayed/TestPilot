package delivery.store;

import delivery.identity.TenantId;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TenantScopedHooksAndMemoryTest {

    @Test
    public void preferredHooksOnTheSameHostStayPerTenant() throws Exception {
        Path store = Files.createTempDirectory("hooks-tenant");
        String host = "https://same.example.com/login";
        TenantId alice = TenantId.mint();
        TenantId bob = TenantId.mint();
        PreferredHooksStore.save(store, alice, host, "data-tenant-a");
        PreferredHooksStore.save(store, bob, host, "data-tenant-b");
        Assert.assertEquals(
                PreferredHooksStore.load(store, alice, host),
                List.of("data-tenant-a"));
        Assert.assertEquals(
                PreferredHooksStore.load(store, bob, host),
                List.of("data-tenant-b"));
        Path domainShared = store.resolve("same-example-com").resolve(PreferredHooksStore.FILE_NAME);
        Assert.assertFalse(Files.exists(domainShared),
                "tenant-scoped hooks must not write the legacy domain-shared file: " + domainShared);
    }

    @Test
    public void locatorMemoryOnTheSameHostStaysPerTenant() throws Exception {
        Path store = Files.createTempDirectory("mem-tenant");
        String host = "https://same.example.com";
        Path alice = DomainLocatorMemory.sharedFile(store, TenantId.mint(), host);
        Path bob = DomainLocatorMemory.sharedFile(store, TenantId.mint(), host);
        Assert.assertNotEquals(alice, bob);
        Files.createDirectories(alice.getParent());
        Files.writeString(alice, "{\"slots\":[]}");
        Assert.assertFalse(Files.exists(bob));
    }
}
