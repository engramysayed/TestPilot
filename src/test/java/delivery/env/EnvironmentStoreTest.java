package delivery.env;

import delivery.store.StaleLibraryRevisionException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class EnvironmentStoreTest {

    @Test
    public void originChangeCreatesANewRevisionWithoutRenamingStorageRoot() throws Exception {
        Path root = Files.createTempDirectory("env-store");
        EnvironmentStore store = new EnvironmentStore(root);
        EnvironmentProfile staging = new EnvironmentProfile(
                "staging", "https://staging.example", "qa", "ollama", 4, 2);
        EnvironmentStore.Revision first = store.commit(null, staging);
        EnvironmentProfile uat = new EnvironmentProfile(
                "staging", "https://uat.example", "qa", "ollama", 4, 2);
        EnvironmentStore.Revision second = store.commit(first.id(), uat);
        Assert.assertNotEquals(second.id(), first.id());
        Assert.assertEquals(store.read(first.id()).profile().origin(), "https://staging.example");
        Assert.assertEquals(store.head().orElseThrow().profile().origin(), "https://uat.example");
        Assert.assertTrue(Files.isDirectory(root), "environment revisions stay under the original store path");
        Assert.assertTrue(EnvironmentStore.compare(staging, uat).containsKey("origin.after"));
    }

    @Test
    public void staleBaseCannotRedirectQueuedEnvironment() throws Exception {
        Path root = Files.createTempDirectory("env-stale");
        EnvironmentStore store = new EnvironmentStore(root);
        EnvironmentStore.Revision first = store.commit(null, new EnvironmentProfile(
                "prod", "https://a.example", "ci", "ollama", 1, 1));
        store.commit(first.id(), new EnvironmentProfile(
                "prod", "https://b.example", "ci", "ollama", 1, 1));
        Assert.assertThrows(StaleLibraryRevisionException.class, () ->
                store.commit(first.id(), new EnvironmentProfile(
                        "prod", "https://c.example", "ci", "cursor", 1, 1)));
        Assert.assertEquals(store.read(first.id()).profile().origin(), "https://a.example");
    }
}
