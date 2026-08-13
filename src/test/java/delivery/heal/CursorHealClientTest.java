package delivery.heal;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CursorHealClientTest {

    @Test
    public void parseCandidateIdFromStdoutJson() {
        Assert.assertEquals(CursorHealClient.parseCandidateId("{\"candidateId\":\"c3\"}"), "c3");
        Assert.assertEquals(
                CursorHealClient.parseCandidateId("log line\n{\"candidateId\":\"c7\"}\n"),
                "c7");
        Assert.assertEquals(
                CursorHealClient.parseCandidateId(
                        "Thought: the visible control matches.\nAction: {\"candidateId\":\"c9\"}"),
                "c9");
        Assert.assertEquals(CursorHealClient.parseCandidateId("no json"), "");
    }

    @Test
    public void skippedWithoutApiKeyReturnsBlank() {
        CursorHealClient client = new CursorHealClient(true, "node -e \"process.exit(0)\"", 2);
        // No CURSOR_API_KEY in typical unit env → blank
        String id = client.pickCandidateId("click", "fail", "c1|css|#x|btn|X", "<html/>", null);
        Assert.assertEquals(id, "");
    }
}
