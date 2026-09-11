package delivery.heal;

import delivery.authoring.GroundRankResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CursorHealClientGroundRankTest {

    @Test
    public void groundRankResultParsesSidecarJson() {
        GroundRankResult result = GroundRankResult.parse(
                "{\"candidateId\":\"c2\",\"confidence\":\"medium\",\"rationale\":\"matches\"}");
        Assert.assertEquals("c2", result.candidateId());
        Assert.assertTrue(result.isAcceptable());
    }

    @Test
    public void disabledClientReturnsEmptyGroundRank() {
        CursorHealClient client = new CursorHealClient(false, "node noop", 1);
        Assert.assertFalse(client.isEnabled());
        GroundRankResult result = client.groundRankResult(
                "CLICK Sign In", "| c1 | css | .x | button | Sign In |",
                "<button>Sign In</button>", null, List.of());
        Assert.assertFalse(result.isAcceptable());
    }
}
