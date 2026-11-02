package delivery.job;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

public class MonitorTargetResolverTest {

    @AfterMethod
    public void clear() {
        SuiteRunContext.clear();
        System.clearProperty("keel.monitor.prod.base-url");
        System.clearProperty("keel.monitor.staging.base-url");
    }

    @Test
    public void usesMappedTarget() {
        Assert.assertEquals(
                MonitorTargetResolver.resolveBaseUrl("staging", "http://default",
                        Map.of("staging", "https://stg.example")),
                "https://stg.example");
    }

    @Test
    public void fallsBackToDefault() {
        Assert.assertEquals(
                MonitorTargetResolver.resolveBaseUrl("prod", "http://default", Map.of()),
                "http://default");
    }

    @Test
    public void activeMonitorContextOverrides() {
        System.setProperty("keel.monitor.prod.base-url", "https://prod.example");
        SuiteRunContext.activate(new SuiteRunContext("p", "s", null, null, "monitor", null, "prod"));
        Assert.assertEquals(
                MonitorTargetResolver.resolveForActiveContext("http://default", null),
                "https://prod.example");
    }
}
