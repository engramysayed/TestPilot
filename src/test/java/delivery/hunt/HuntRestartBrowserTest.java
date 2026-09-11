package delivery.hunt;

import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** restart_browser allowlist, aliases, and restart cap. */
public class HuntRestartBrowserTest {

    @Test
    public void normalizerMapsRestartAliases() {
        for (String alias : List.of("fresh_session", "reset_browser", "restart", "restart_browser")) {
            Map<String, Object> n = HuntActionNormalizer.normalize(Map.of("type", alias));
            Assert.assertEquals(n.get("type"), "restart_browser", alias);
        }
    }

    @Test
    public void sessionRejectsWhenRestartCapExhausted() {
        HuntBrowserSession session = HuntBrowserSession.forCapTest(HuntBrowserSession.MAX_RESTARTS);
        Map<String, Object> result = session.restart(false);
        Assert.assertEquals(result.get("ok"), false);
        Assert.assertTrue(String.valueOf(result.get("reason")).contains("cap"),
                String.valueOf(result.get("reason")));
    }

    @Test
    public void executorInvokesSessionRestartOnceAndDefaultsLoginFalse() {
        WebDriver driver = mock(WebDriver.class);
        HuntBrowserControls session = mock(HuntBrowserControls.class);
        when(session.driver()).thenReturn(driver);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);
        ok.put("url", "https://example.test/");
        when(session.restart(false)).thenReturn(ok);

        HuntActionExecutor ex = HuntActionExecutor.forSession(session);
        Map<String, Object> row = ex.executeOne(Map.of("type", "restart_browser"));
        Assert.assertEquals(row.get("status"), "ok");
        Assert.assertEquals(row.get("login"), false);
        Assert.assertEquals(row.get("url"), "https://example.test/");
        verify(session, times(1)).restart(false);
    }

    @Test
    public void executorPassesLoginTrueAndRejectsOverCapFromSession() {
        WebDriver driver = mock(WebDriver.class);
        HuntBrowserControls session = mock(HuntBrowserControls.class);
        when(session.driver()).thenReturn(driver);
        when(session.restart(true)).thenReturn(Map.of(
                "ok", false,
                "reason", "restart_browser cap=2 already used"));

        HuntActionExecutor ex = HuntActionExecutor.forSession(session);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "fresh_session");
        action.put("login", true);
        Map<String, Object> row = ex.executeOne(action);
        Assert.assertEquals(row.get("status"), "rejected");
        Assert.assertTrue(String.valueOf(row.get("reason")).contains("cap"));
        verify(session, times(1)).restart(true);
    }

    @Test
    public void restartWithoutSessionIsRejected() {
        HuntActionExecutor ex = new HuntActionExecutor(mock(WebDriver.class));
        Map<String, Object> row = ex.executeOne(Map.of("type", "restart_browser"));
        Assert.assertEquals(row.get("status"), "rejected");
        Assert.assertTrue(String.valueOf(row.get("reason")).contains("unavailable"));
    }

    @Test
    public void oracleAndJournalTreatRestartLikeNavigate() {
        Assert.assertTrue(HuntOracle.lastActionNavigateOrClick(List.of(
                Map.of("type", "restart_browser", "status", "ok"))));
        String sys = OllamaHuntPlanner.systemPrompt();
        Assert.assertTrue(sys.contains("restart_browser"), sys);
        Assert.assertTrue(sys.contains("fresh_session") || sys.toLowerCase().contains("session"), sys);
    }
}
