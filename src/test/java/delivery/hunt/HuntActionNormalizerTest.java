package delivery.hunt;

import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HuntActionNormalizerTest {

    @Test
    public void fillBecomesTypeAndBareAttrBecomesCssSelector() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("type", "fill");
        raw.put("locator", "css:data-qa='username_Input'");
        raw.put("value", "invalid_user");
        Map<String, Object> n = HuntActionNormalizer.normalize(raw);
        Assert.assertEquals(n.get("type"), "type");
        Assert.assertEquals(n.get("locator"), "[data-qa='username_Input']");
        Assert.assertEquals(n.get("locatorStrategy"), "css");
    }

    @Test
    public void shorthandClickAndWaitNormalize() {
        Map<String, Object> click = HuntActionNormalizer.normalize(Map.of(
                "click", "css:data-qa='sign_In_Button'"));
        Assert.assertEquals(click.get("type"), "click");
        Assert.assertEquals(click.get("locator"), "[data-qa='sign_In_Button']");

        Map<String, Object> wait = HuntActionNormalizer.normalize(Map.of("wait", 5000));
        Assert.assertEquals(wait.get("type"), "wait");
        Assert.assertEquals(String.valueOf(wait.get("ms")), "5000");
    }

    @Test
    public void bareHookTokenUsesActivePreferredHook() {
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            Map<String, Object> n = HuntActionNormalizer.normalize(Map.of(
                    "type", "click",
                    "locator", "sign_In_Button"));
            Assert.assertEquals(n.get("locator"), "[data-axis-test-id='sign_In_Button']");
            Assert.assertEquals(n.get("locatorStrategy"), "css");
        }
    }

    @Test
    public void bareHookTokenLeftAloneWhenNoPreferredHooks() {
        try (PreferredHooksStore.Scope ignored = PreferredHooksStore.activate(List.of())) {
            Map<String, Object> n = HuntActionNormalizer.normalize(Map.of(
                    "type", "click",
                    "locator", "sign_In_Button"));
            Assert.assertEquals(n.get("locator"), "sign_In_Button");
        }
    }

    @Test
    public void guardAcceptsGemmaStyleLocatorAgainstPageMap() {
        String slim = "<body><input data-axis-test-id='username_Input' id='basic_login'/></body>";
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/login", "Login", slim);
        HuntActionGuard g = new HuntActionGuard(map, slim);
        Map<String, Object> action = HuntActionNormalizer.normalize(Map.of(
                "type", "type",
                "locator", "css:data-axis-test-id='username_Input'",
                "value", "x"));
        Assert.assertTrue(g.rejectReason(action).isEmpty(), String.valueOf(g.rejectReason(action)));
    }
}
