package delivery.job;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SuiteCaseFilterTest {

    @AfterMethod
    public void clear() {
        SuiteRunContext.clear();
    }

    @Test
    public void emptyFilterReturnsAll() {
        List<ManualTestCase> cases = List.of(tc("TC_1", "smoke"), tc("TC_2", "regression"));
        Assert.assertEquals(SuiteCaseFilter.filter(cases, null, null, false).size(), 2);
        Assert.assertEquals(SuiteCaseFilter.apply(cases, null).size(), 2);
    }

    @Test
    public void tcIdsAllowList() {
        List<ManualTestCase> cases = List.of(tc("TC_1", "smoke"), tc("TC_2", "regression"), tc("TC_3", "nightly"));
        List<ManualTestCase> out = SuiteCaseFilter.filter(cases, List.of("TC_2", "TC_3"), null, false);
        Assert.assertEquals(out.size(), 2);
        Assert.assertEquals(out.get(0).tcId(), "TC_2");
        Assert.assertEquals(out.get(1).tcId(), "TC_3");
    }

    @Test
    public void caseTagFallbackWhenNoSuiteTcIds() {
        List<ManualTestCase> cases = List.of(tc("TC_1", "smoke,login"), tc("TC_2", "regression"), tc("TC_3", "SMOKE"));
        List<ManualTestCase> out = SuiteCaseFilter.filter(cases, List.of(), "smoke", true);
        Assert.assertEquals(out.size(), 2);
        Assert.assertEquals(out.get(0).tcId(), "TC_1");
        Assert.assertEquals(out.get(1).tcId(), "TC_3");
    }

    @Test
    public void suiteStoreTagResolvesTcIdsViaContext() throws Exception {
        Path root = Files.createTempDirectory("suite-filter-");
        Files.writeString(root.resolve(SuiteStore.FILE), """
                [
                  {"suiteId":"s-smoke","name":"Smoke","tags":["smoke"],"tcIds":["TC_1","TC_9"],"builtIn":true},
                  {"suiteId":"s-reg","name":"Reg","tags":["regression"],"tcIds":["TC_2"],"builtIn":true}
                ]
                """, StandardCharsets.UTF_8);
        SuiteRunContext.activate(new SuiteRunContext("p1", null, "smoke", List.of(), "manual", null, null));
        List<ManualTestCase> cases = List.of(tc("TC_1", ""), tc("TC_2", ""), tc("TC_9", ""), tc("TC_X", ""));
        List<ManualTestCase> out = SuiteCaseFilter.apply(cases, root);
        Assert.assertEquals(out.size(), 2);
        Assert.assertEquals(out.get(0).tcId(), "TC_1");
        Assert.assertEquals(out.get(1).tcId(), "TC_9");
    }

    @Test
    public void suiteIdFilter() throws Exception {
        Path root = Files.createTempDirectory("suite-id-");
        Files.writeString(root.resolve(SuiteStore.FILE), """
                [{"suiteId":"abc","name":"Pack","tags":["nightly"],"tcIds":["TC_7"],"builtIn":false}]
                """, StandardCharsets.UTF_8);
        SuiteRunContext.activate(new SuiteRunContext("p1", "abc", null, List.of(), "schedule", "slack", null));
        List<ManualTestCase> out = SuiteCaseFilter.apply(List.of(tc("TC_7", ""), tc("TC_8", "")), root);
        Assert.assertEquals(out.size(), 1);
        Assert.assertEquals(out.get(0).tcId(), "TC_7");
    }

    private static ManualTestCase tc(String id, String tags) {
        return new ManualTestCase(id, "t", "", "step", "ok", "P1", tags);
    }
}
