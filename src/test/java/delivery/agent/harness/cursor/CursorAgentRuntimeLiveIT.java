package delivery.agent.harness.cursor;

import org.testng.annotations.Test;

/**
 * Opt-in live Cursor SDK test — requires {@code CURSOR_API_KEY} and
 * {@code -Dkeel.agent.cursor.live=true}. Excluded from normal CI by default.
 */
public class CursorAgentRuntimeLiveIT {

    @Test(enabled = false)
    public void liveCursorExecute_placeholder() {
        // Implemented when operators run: mvn test -Dkeel.agent.cursor.live=true -Dtest=CursorAgentRuntimeLiveIT
    }
}
