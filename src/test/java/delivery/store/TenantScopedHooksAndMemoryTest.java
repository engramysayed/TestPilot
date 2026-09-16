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
        PreferredHooksStore.save(store, TenantId.personal(1L), host, "data-tenant-a");
        PreferredHooksStore.save(store, TenantId.personal(2L), host, "data-tenant-b");
        Assert.assertEquals(
                PreferredHooksStore.load(store, TenantId.personal(1L), host),
                List.of("data-tenant-a"));
        Assert.assertEquals(
                PreferredHooksStore.load(store, TenantId.personal(2L), host),
                List.of("data-tenant-b"));
        Path domainShared = store.resolve("same-example-com").resolve(PreferredHooksStore.FILE_NAME);
        Assert.assertFalse(Files.exists(domainShared),
                "tenant-scoped hooks must not write the legacy domain-shared file: " + domainShared);
    }

    @Test
    public void locatorMemoryOnTheSameHostStaysPerTenant() throws Exception {
        Path store = Files.createTempDirectory("mem-tenant");
        String host = "https://same.example.com";
        Path alice = DomainLocatorMemory.sharedFile(store, TenantId.personal(1L), host);
        Path bob = DomainLocatorMemory.sharedFile(store, TenantId.personal(2L), host);
        Assert.assertNotEquals(alice, bob);
        Files.createDirectories(alice.getParent());
        Files.writeString(alice, "{\"slots\":[]}");
        Assert.assertFalse(Files.exists(bob));
    }
}
