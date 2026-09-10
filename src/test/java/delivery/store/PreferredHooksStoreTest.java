package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PreferredHooksStoreTest {

    @Test
    public void parseKeepsValidAttributeNamesAndDropsJunk() {
        List<String> hooks = PreferredHooksStore.parse(
                "data-axis-test-id, javascript:alert(1), //button, data-qa, DATA-AXIS-TEST-ID");
        Assert.assertEquals(hooks, List.of("data-axis-test-id", "data-qa"));
    }

    @Test
    public void roundTripsOnTheDomainFolder() throws Exception {
        Path store = Files.createTempDirectory("pref-hooks");
        PreferredHooksStore.save(store, "https://opssit.axispay.app/login",
                "data-axis-test-id, data-qa");
        Path file = store.resolve("opssit-axispay-app").resolve(PreferredHooksStore.FILE_NAME);
        Assert.assertTrue(Files.isRegularFile(file), file.toString());
        Assert.assertEquals(
                PreferredHooksStore.load(store, "https://opssit.axispay.app"),
                List.of("data-axis-test-id", "data-qa"));
    }

    @Test
    public void activateScopesCurrentHooks() {
        Assert.assertEquals(PreferredHooksStore.current(), List.of());
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            Assert.assertEquals(PreferredHooksStore.current(), List.of("data-axis-test-id"));
        }
        Assert.assertEquals(PreferredHooksStore.current(), List.of());
    }
}
