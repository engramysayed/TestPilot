package delivery.job;

import delivery.net.TargetNetworkPolicy;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WebhookDestinationStoreTest {

    @Test
    public void sharedPolicyRejectsMetadataAndLoopbackDestinations() throws Exception {
        Path file = Files.createTempDirectory("webhook-policy").resolve("webhook.json");
        WebhookDestinationStore store = new WebhookDestinationStore(file);
        TargetNetworkPolicy shared = TargetNetworkPolicy.shared("https://shop.example.com/app");
        Assert.assertThrows(IllegalArgumentException.class, () ->
                store.put("http://169.254.169.254/latest/meta-data/", "whsec", shared));
        Assert.assertThrows(IllegalArgumentException.class, () ->
                store.put("http://127.0.0.1:9/hook", "whsec", shared));
        Assert.assertThrows(IllegalArgumentException.class, () ->
                store.put("http://10.0.0.8/hook", "whsec", shared));
        Assert.assertTrue(store.get().isEmpty());
    }

    @Test
    public void dedicatedLoopbackCidrAllowsControlledReceiver() throws Exception {
        Path file = Files.createTempDirectory("webhook-dedicated").resolve("webhook.json");
        WebhookDestinationStore store = new WebhookDestinationStore(file);
        TargetNetworkPolicy dedicated = TargetNetworkPolicy.dedicated(
                "http://127.0.0.1:8080/app", List.of("127.0.0.0/8"));
        WebhookDestinationStore.Config saved = store.put("http://127.0.0.1:4079/hook", "whsec", dedicated);
        Assert.assertEquals(saved.url(), "http://127.0.0.1:4079/hook");
        Assert.assertTrue(store.get().isPresent());
    }

    @Test
    public void publicCiHostsRemainAllowedOnSharedInstalls() throws Exception {
        Path file = Files.createTempDirectory("webhook-ci").resolve("webhook.json");
        WebhookDestinationStore store = new WebhookDestinationStore(file);
        WebhookDestinationStore.Config saved = store.put(
                "https://ci.example/hooks/keel",
                "whsec",
                TargetNetworkPolicy.shared("https://shop.example.com/app"));
        Assert.assertEquals(saved.url(), "https://ci.example/hooks/keel");
    }
}
