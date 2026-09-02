package delivery.portal.service;

import delivery.portal.DeliveryPortalProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class GenerateModelServiceTest {

    @Test
    public void resolve_usesDefaultWhenBlank() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setGenerateModel("gemma4:e2b");
        props.setGenerateModels(List.of("gemma4:e2b", "qwen2.5:latest"));
        GenerateModelService service = new GenerateModelService(props);

        Assert.assertEquals(service.resolve(null), "gemma4:e2b");
        Assert.assertEquals(service.resolve("  "), "gemma4:e2b");
    }

    @Test
    public void resolve_acceptsConfiguredModel() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setGenerateModel("gemma4:e2b");
        props.setGenerateModels(List.of("gemma4:e2b", "qwen2.5:latest"));
        GenerateModelService service = new GenerateModelService(props);

        Assert.assertEquals(service.resolve("qwen2.5:latest"), "qwen2.5:latest");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void resolve_rejectsUnknownModel() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setGenerateModel("gemma4:e2b");
        props.setGenerateModels(List.of("gemma4:e2b", "qwen2.5:latest"));
        new GenerateModelService(props).resolve("llama3:latest");
    }

    @Test
    public void configPayload_listsModelsAndDefault() {
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setGenerateModel("gemma4:e2b");
        props.setGenerateModels(List.of("gemma4:e2b", "qwen2.5:latest"));
        Map<String, Object> payload = new GenerateModelService(props).configPayload();

        Assert.assertEquals(payload.get("defaultModel"), "gemma4:e2b");
        Assert.assertEquals(payload.get("models"), List.of("gemma4:e2b", "qwen2.5:latest"));
    }
}
