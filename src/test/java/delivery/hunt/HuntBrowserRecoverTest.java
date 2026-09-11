package delivery.hunt;

import delivery.job.DeadBrowserSession;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HuntBrowserRecoverTest {

    @Test
    public void deadSessionDetectorRecognizesInvalidSession() {
        Assert.assertTrue(DeadBrowserSession.isDead("invalid session id"));
        Assert.assertTrue(DeadBrowserSession.isDead("disconnected: not connected to DevTools"));
        Assert.assertFalse(DeadBrowserSession.isDead("element not found"));
    }

    @Test
    public void ensureAliveRestartsWhenDriverThrowsDeadSession() throws Exception {
        WebDriver dead = mock(WebDriver.class);
        WebDriver fresh = mock(WebDriver.class);
        when(dead.getCurrentUrl()).thenThrow(new RuntimeException("invalid session id"));
        when(fresh.getCurrentUrl()).thenReturn("https://example.test/");

        HuntBrowserControls session = mock(HuntBrowserControls.class);
        when(session.driver()).thenReturn(dead, fresh);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);
        ok.put("url", "https://example.test/");
        when(session.restart(false)).thenReturn(ok);

        Assert.assertTrue(LiveHuntService.ensureAliveOrRestart(session, null, 7, false));
        verify(session, times(1)).restart(false);
    }
}
