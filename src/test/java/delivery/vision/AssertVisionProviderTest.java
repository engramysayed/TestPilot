package delivery.vision;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

public class AssertVisionProviderTest {
    private final Map<String, String> sys = new HashMap<>();
    private final Map<String, String> file = new HashMap<>();
    private final AtomicInteger probes = new AtomicInteger();

    private AssertVisionProvider resolve() {
        return AssertVisionProvider.resolve(sys::get, file::get);
    }

    private void probeReturns(OptionalDouble v) {
        AssertVisionProvider.resetProbeCache();
        AssertVisionProvider.vramProbe = () -> { probes.incrementAndGet(); return v; };
    }

    @BeforeMethod public void noGpu() {
        probeReturns(OptionalDouble.empty());
    }

    @AfterMethod public void clear() {
        sys.clear(); file.clear(); probes.set(0);
        for (String k : new String[]{AssertVisionProvider.KEY, AssertVisionProvider.ALIAS_KEY,
                AssertVisionProvider.VRAM_HINT_KEY, "delivery.vision.assert.model"}) System.clearProperty(k);
        AssertVisionProvider.vramProbe = AssertVisionProvider::nvidiaSmiVramGb;
        AssertVisionProvider.resetProbeCache();
    }

    @Test public void defaultIsUiTarsAndQwenIs7b() {
        assertEquals(AssertVisionProvider.KEY, "delivery.vision.assert.provider");
        assertEquals(resolve(), AssertVisionProvider.UITARS);
        assertEquals(AssertVisionProvider.UITARS.defaultModel, "ui-tars");
        assertEquals(AssertVisionProvider.QWEN.defaultModel, "qwen2.5vl:7b");
        assertEquals(QwenVisionProvider.DEFAULT_MODEL, "qwen2.5vl:7b");
        assertEquals(probes.get(), 0, "no probe unless auto");
    }

    @Test public void explicitValuesCaseAndSpaceTolerant() {
        file.put(AssertVisionProvider.KEY, " QWEN ");
        assertEquals(resolve(), AssertVisionProvider.QWEN);
        file.put(AssertVisionProvider.KEY, "uitars");
        assertEquals(resolve(), AssertVisionProvider.UITARS);
    }

    @Test public void autoHighVramPicksQwen() {
        file.put(AssertVisionProvider.KEY, "auto");
        probeReturns(OptionalDouble.of(24));
        assertEquals(resolve(), AssertVisionProvider.QWEN);
        probeReturns(OptionalDouble.of(12));
        assertEquals(resolve(), AssertVisionProvider.QWEN, "12 GB is enough");
    }

    @Test public void autoLowVramPicksUiTars() {
        file.put(AssertVisionProvider.KEY, "auto");
        probeReturns(OptionalDouble.of(11.9));
        assertEquals(resolve(), AssertVisionProvider.UITARS);
        probeReturns(OptionalDouble.of(4)); // RTX A2000-class
        assertEquals(resolve(), AssertVisionProvider.UITARS);
    }

    @Test public void autoUnknownVramFallsBackToUiTars() {
        file.put(AssertVisionProvider.KEY, "auto");
        assertEquals(resolve(), AssertVisionProvider.UITARS);
        AssertVisionProvider.resetProbeCache();
        AssertVisionProvider.vramProbe = () -> { throw new IllegalStateException("boom"); };
        assertEquals(resolve(), AssertVisionProvider.UITARS, "probe exception -> uitars");
    }

    @Test public void vramHintBeatsProbeAndProbeIsCached() {
        file.put(AssertVisionProvider.KEY, "auto");
        probeReturns(OptionalDouble.of(4));
        file.put(AssertVisionProvider.VRAM_HINT_KEY, "16");
        assertEquals(resolve(), AssertVisionProvider.QWEN);
        assertEquals(probes.get(), 0);
        sys.put(AssertVisionProvider.VRAM_HINT_KEY, "8");
        assertEquals(resolve(), AssertVisionProvider.UITARS, "-D hint beats file hint");
        sys.clear(); file.remove(AssertVisionProvider.VRAM_HINT_KEY);
        resolve(); resolve();
        assertEquals(probes.get(), 1, "probe runs once per JVM");
        file.put(AssertVisionProvider.VRAM_HINT_KEY, "lots");
        assertEquals(resolve(), AssertVisionProvider.UITARS, "bad hint ignored -> cached probe (4 GB)");
    }

    @Test public void deprecatedAliasesStillWork() {
        file.put(AssertVisionProvider.ALIAS_KEY, "qwen7b");
        assertEquals(resolve(), AssertVisionProvider.QWEN);
        file.put(AssertVisionProvider.ALIAS_KEY, "uitars");
        assertEquals(resolve(), AssertVisionProvider.UITARS);
        file.put(AssertVisionProvider.KEY, "qwen7b");
        assertEquals(resolve(), AssertVisionProvider.QWEN, "new key beats alias; qwen7b value alias");
        assertEquals(AssertVisionProvider.normalize("qwen7b", "k"), "qwen");
    }

    @Test public void precedenceSystemBeatsFileNewKeyBeatsAlias() {
        file.put(AssertVisionProvider.KEY, "qwen");
        sys.put(AssertVisionProvider.ALIAS_KEY, "uitars");
        assertEquals(resolve(), AssertVisionProvider.UITARS, "-D alias beats file new key");
        sys.put(AssertVisionProvider.KEY, "qwen");
        assertEquals(resolve(), AssertVisionProvider.QWEN, "-D new key beats -D alias");
        sys.clear();
        file.put(AssertVisionProvider.ALIAS_KEY, "uitars");
        assertEquals(resolve(), AssertVisionProvider.QWEN, "file new key beats file alias");
    }

    @Test public void unknownValuesAreErrors() {
        file.put(AssertVisionProvider.KEY, "gpt4v");
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, this::resolve);
        assertTrue(e.getMessage().contains("auto, uitars or qwen"), e.getMessage());
        file.clear();
        file.put(AssertVisionProvider.ALIAS_KEY, "nope");
        expectThrows(IllegalArgumentException.class, this::resolve);
        expectThrows(IllegalArgumentException.class, () -> AssertVisionProvider.withOverride("nope"));
    }

    @Test public void overrideBeatsEverythingAndIsScoped() throws Exception {
        sys.put(AssertVisionProvider.KEY, "qwen");
        try (var job = AssertVisionProvider.withOverride("uitars")) {
            assertEquals(resolve(), AssertVisionProvider.UITARS);
            try (var nested = AssertVisionProvider.withOverride("auto")) {
                sys.put(AssertVisionProvider.VRAM_HINT_KEY, "24");
                assertEquals(resolve(), AssertVisionProvider.QWEN);
            }
            try (var blank = AssertVisionProvider.withOverride("")) {
                assertEquals(resolve(), AssertVisionProvider.QWEN, "blank = no override");
            }
            AssertVisionProvider[] other = new AssertVisionProvider[1];
            Thread t = new Thread(() -> other[0] = resolve());
            t.start(); t.join();
            assertEquals(other[0], AssertVisionProvider.QWEN, "override is thread-local");
        }
        assertEquals(resolve(), AssertVisionProvider.QWEN);
    }

    @Test public void configUsesRealSourcesAndModelFollowsProvider() {
        // application.properties in this tree: delivery.vision.assert.provider=uitars, model ui-tars
        assertEquals(VisionGroundingConfig.assertVisionProvider(), AssertVisionProvider.UITARS);
        assertEquals(VisionGroundingConfig.assertModel(), "ui-tars");
        System.setProperty(AssertVisionProvider.ALIAS_KEY, "qwen7b");
        assertEquals(VisionGroundingConfig.assertProviderId(), "qwen", "-D alias beats file");
        assertEquals(VisionGroundingConfig.assertModel(), "qwen2.5vl:7b", "file's ui-tars tag not sent to qwen");
        System.setProperty("delivery.vision.assert.model", "qwen2.5vl:3b");
        assertEquals(VisionGroundingConfig.assertModel(), "qwen2.5vl:7b", "non-commercial 3b pin ignored");
        System.setProperty(AssertVisionProvider.KEY, "auto");
        System.setProperty(AssertVisionProvider.VRAM_HINT_KEY, "6");
        assertEquals(VisionGroundingConfig.assertProviderId(), "uitars");
        assertEquals(VisionGroundingConfig.assertModel(), "ui-tars", "qwen tag never sent to uitars");
    }

    @Test public void fileValuesCopiedIntoSystemPropsDoNotCountAsRuntime() {
        // utils.PropertyReader copies application.properties into System properties
        System.setProperty(AssertVisionProvider.KEY, "uitars"); // same as file → still "file"
        System.setProperty("delivery.vision.provider", "qwen"); // real -D legacy umbrella
        try {
            assertNull(AssertVisionProvider.runtimeProp(AssertVisionProvider.KEY));
            assertEquals(VisionGroundingConfig.assertVisionProvider(), AssertVisionProvider.QWEN, "-D (even legacy) beats file");
        } finally {
            System.clearProperty("delivery.vision.provider");
        }
    }

    @Test public void createsMatchingProviderClass() {
        System.setProperty("delivery.vision.assertions.enabled", "true");
        try {
            assertTrue(VisionGroundingConfig.createAssertionProvider() instanceof UiTarsVisionProvider);
            try (var s = AssertVisionProvider.withOverride("qwen")) {
                assertTrue(VisionGroundingConfig.createAssertionProvider() instanceof QwenVisionProvider);
            }
        } finally {
            System.clearProperty("delivery.vision.assertions.enabled");
        }
    }
}
