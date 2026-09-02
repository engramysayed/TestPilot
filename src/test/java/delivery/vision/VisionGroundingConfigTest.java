package delivery.vision;

import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VisionGroundingConfigTest {

    @Test
    public void disabledByDefault() {
        System.clearProperty("delivery.vision.grounding.enabled");
        Assert.assertFalse(VisionGroundingConfig.enabled());
    }

    @Test
    public void enabledWhenTrue() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            Assert.assertTrue(VisionGroundingConfig.enabled());
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }

    @Test
    public void fakeReturnsEmpty() {
        VisionAnalysisResult r = new FakeVisionGroundingProvider()
                .analyze(new byte[] {1}, new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Click Login"));
        Assert.assertFalse(r.found());
        Assert.assertTrue(r.candidates().isEmpty());
    }

    @Test
    public void createProviderReturnsQwenFromGroundingKeysWhenEnabled() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.grounding.provider", "qwen");
        System.setProperty("delivery.vision.grounding.model", "qwen2.5vl:3b");
        System.clearProperty("delivery.vision.provider");
        try {
            VisionGroundingProvider provider = VisionGroundingConfig.createProvider();
            Assert.assertTrue(provider instanceof QwenVisionProvider);
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.grounding.provider");
            System.clearProperty("delivery.vision.grounding.model");
            System.clearProperty("delivery.vision.provider");
        }
    }

    @Test
    public void createProviderReturnsUiTarsWhenGroundingProviderSet() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.grounding.provider", "uitars");
        System.setProperty("delivery.vision.grounding.model", "ui-tars");
        System.clearProperty("delivery.vision.provider");
        try {
            VisionGroundingProvider provider = VisionGroundingConfig.createProvider();
            Assert.assertTrue(provider instanceof UiTarsVisionProvider);
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.grounding.provider");
            System.clearProperty("delivery.vision.grounding.model");
            System.clearProperty("delivery.vision.provider");
        }
    }

    @Test
    public void createProviderReturnsQwenWhenProviderSet() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.provider", "qwen");
        try {
            VisionGroundingProvider provider = VisionGroundingConfig.createProvider();
            Assert.assertTrue(provider instanceof QwenVisionProvider);
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.provider");
        }
    }

    @Test
    public void assertionsDisabledByDefault() {
        System.clearProperty("delivery.vision.assertions.enabled");
        Assert.assertFalse(VisionGroundingConfig.assertionsEnabled());
    }

    @Test
    public void healHintsEnabledByDefault() {
        System.clearProperty("delivery.vision.heal-hints.enabled");
        Assert.assertTrue(VisionGroundingConfig.healHintsEnabled());
    }

    @Test
    public void createAssertionProviderIgnoresGroundingFlag() {
        System.setProperty("delivery.vision.grounding.enabled", "false");
        System.setProperty("delivery.vision.assertions.enabled", "true");
        System.setProperty("delivery.vision.provider", "qwen");
        try {
            Assert.assertTrue(VisionGroundingConfig.createAssertionProvider() instanceof QwenVisionProvider);
            Assert.assertTrue(VisionGroundingConfig.createProvider() instanceof FakeVisionGroundingProvider);
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.assertions.enabled");
            System.clearProperty("delivery.vision.provider");
        }
    }

    @Test
    public void splitKeysQwenBothForPromptTesting() {
        System.setProperty("delivery.vision.grounding.provider", "qwen");
        System.setProperty("delivery.vision.grounding.model", "qwen2.5vl:3b");
        System.setProperty("delivery.vision.assert.provider", "qwen");
        System.setProperty("delivery.vision.assert.model", "qwen2.5vl:3b");
        System.clearProperty("delivery.vision.provider");
        System.clearProperty("delivery.vision.model");
        try {
            Assert.assertEquals(VisionGroundingConfig.groundingProviderId(), "qwen");
            Assert.assertEquals(VisionGroundingConfig.assertProviderId(), "qwen");
            Assert.assertEquals(VisionGroundingConfig.groundingModel(), "qwen2.5vl:3b");
            Assert.assertEquals(VisionGroundingConfig.assertModel(), "qwen2.5vl:3b");
        } finally {
            System.clearProperty("delivery.vision.grounding.provider");
            System.clearProperty("delivery.vision.grounding.model");
            System.clearProperty("delivery.vision.assert.provider");
            System.clearProperty("delivery.vision.assert.model");
        }
    }

    @Test
    public void legacyProviderForcesBothWhenSplitUnset() {
        System.setProperty("delivery.vision.provider", "qwen");
        System.setProperty("delivery.vision.model", "qwen2.5vl:7b");
        System.clearProperty("delivery.vision.grounding.provider");
        System.clearProperty("delivery.vision.assert.provider");
        System.clearProperty("delivery.vision.grounding.model");
        System.clearProperty("delivery.vision.assert.model");
        try {
            Assert.assertEquals(VisionGroundingConfig.groundingProviderId(), "qwen");
            Assert.assertEquals(VisionGroundingConfig.assertProviderId(), "qwen");
            Assert.assertEquals(VisionGroundingConfig.groundingModel(), "qwen2.5vl:7b");
            Assert.assertEquals(VisionGroundingConfig.assertModel(), "qwen2.5vl:7b");
        } finally {
            System.clearProperty("delivery.vision.provider");
            System.clearProperty("delivery.vision.model");
        }
    }

    @Test
    public void splitKeysOverrideLegacy() {
        System.setProperty("delivery.vision.provider", "qwen");
        System.setProperty("delivery.vision.model", "qwen2.5vl:7b");
        System.setProperty("delivery.vision.grounding.provider", "uitars");
        System.setProperty("delivery.vision.grounding.model", "ui-tars");
        System.setProperty("delivery.vision.assert.provider", "qwen");
        System.setProperty("delivery.vision.assert.model", "qwen2.5vl:3b");
        System.setProperty("delivery.vision.grounding.enabled", "true");
        System.setProperty("delivery.vision.assertions.enabled", "true");
        try {
            Assert.assertTrue(VisionGroundingConfig.createProvider() instanceof UiTarsVisionProvider);
            Assert.assertTrue(VisionGroundingConfig.createAssertionProvider() instanceof QwenVisionProvider);
            Assert.assertEquals(VisionGroundingConfig.assertModel(), "qwen2.5vl:3b");
        } finally {
            System.clearProperty("delivery.vision.provider");
            System.clearProperty("delivery.vision.model");
            System.clearProperty("delivery.vision.grounding.provider");
            System.clearProperty("delivery.vision.grounding.model");
            System.clearProperty("delivery.vision.assert.provider");
            System.clearProperty("delivery.vision.assert.model");
            System.clearProperty("delivery.vision.grounding.enabled");
            System.clearProperty("delivery.vision.assertions.enabled");
        }
    }
}
