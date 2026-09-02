package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TcGenerateServiceTimeoutTest {

    @Test
    public void defaultGenerateTimeout_allowsAsyncJsonBatches() {
        Assert.assertEquals(new DeliveryPortalProperties().getGenerateTimeoutSeconds(), 600);
    }

    @Test
    public void ollamaTimeoutMessage_startsWithCodeAndIncludesSeconds() {
        String msg = TcGenerateService.ollamaTimeoutMessage(120);
        Assert.assertTrue(msg.startsWith("OLLAMA_TIMEOUT"), msg);
        Assert.assertTrue(msg.contains("120"), msg);
    }
}
