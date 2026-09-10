package delivery.hunt;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Navigation + JS allowlist for Bug Hunter planner actions.
 */
public class HuntActionNavJsTest {

    @Test
    public void backForwardRefreshCallDriverNavigation() {
        WebDriver driver = mock(WebDriver.class);
        WebDriver.Navigation nav = mock(WebDriver.Navigation.class);
        when(driver.navigate()).thenReturn(nav);

        HuntActionExecutor ex = new HuntActionExecutor(driver);
        Assert.assertEquals(ex.executeOne(Map.of("type", "back")).get("status"), "ok");
        Assert.assertEquals(ex.executeOne(Map.of("type", "forward")).get("status"), "ok");
        Assert.assertEquals(ex.executeOne(Map.of("type", "refresh")).get("status"), "ok");
        verify(nav).back();
        verify(nav).forward();
        verify(nav).refresh();
    }

    @Test
    public void navigateBackAndReloadAliasesWork() {
        WebDriver driver = mock(WebDriver.class);
        WebDriver.Navigation nav = mock(WebDriver.Navigation.class);
        when(driver.navigate()).thenReturn(nav);

        HuntActionExecutor ex = new HuntActionExecutor(driver);
        Assert.assertEquals(ex.executeOne(Map.of("type", "navigate_back")).get("status"), "ok");
        Assert.assertEquals(ex.executeOne(Map.of("type", "reload")).get("status"), "ok");
        verify(nav).back();
        verify(nav).refresh();
    }

    @Test
    public void executeJsRunsScriptAndCapturesResult() {
        WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        when(((JavascriptExecutor) driver).executeScript(anyString())).thenReturn("ok-result");

        HuntActionExecutor ex = new HuntActionExecutor(driver);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "execute_js");
        action.put("script", "return document.title;");
        Map<String, Object> row = ex.executeOne(action);
        Assert.assertEquals(row.get("status"), "ok");
        Assert.assertEquals(String.valueOf(row.get("result")), "ok-result");
        verify((JavascriptExecutor) driver).executeScript("return document.title;");
    }

    @Test
    public void executeJsRejectsBlankAndOversize() {
        WebDriver driver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        HuntActionExecutor ex = new HuntActionExecutor(driver);

        Assert.assertEquals(ex.executeOne(Map.of("type", "execute_js")).get("status"), "rejected");

        Map<String, Object> huge = new LinkedHashMap<>();
        huge.put("type", "js");
        huge.put("code", "x".repeat(4001));
        Assert.assertEquals(ex.executeOne(huge).get("status"), "rejected");
    }

    @Test
    public void oracleTreatsHistoryNavAsNavigateLike() {
        Assert.assertTrue(HuntOracle.lastActionNavigateOrClick(List.of(
                Map.of("type", "refresh", "status", "ok"))));
        Assert.assertTrue(HuntOracle.lastActionNavigateOrClick(List.of(
                Map.of("type", "back", "status", "ok"))));
        Assert.assertTrue(HuntOracle.lastActionNavigateOrClick(List.of(
                Map.of("type", "execute_js", "status", "ok"))));
    }

    @Test
    public void plannerPromptDocumentsNavAndJsActions() {
        String sys = OllamaHuntPlanner.systemPrompt();
        Assert.assertTrue(sys.contains("back"), sys);
        Assert.assertTrue(sys.contains("refresh"), sys);
        Assert.assertTrue(sys.contains("execute_js"), sys);
        Assert.assertTrue(sys.contains("## URL") || sys.toLowerCase().contains("current url")
                || sys.contains("page map"), sys);
    }
}
