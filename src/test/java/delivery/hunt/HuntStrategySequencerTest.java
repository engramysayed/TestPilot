package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class HuntStrategySequencerTest {

    @Test
    public void advancesUntilLast() {
        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        Assert.assertEquals(seq.current().mode(), "happy");
        seq.advance();
        Assert.assertEquals(seq.current().mode(), "empty");
        Assert.assertTrue(seq.forPrompt().contains("completedModes"));
    }

    @Test
    public void isLastOnlyOnInvent() {
        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        Assert.assertFalse(seq.isLast());
        for (int i = 0; i < MODES_BEFORE_LAST; i++) {
            seq.advance();
        }
        Assert.assertEquals(seq.current().mode(), "invent");
        Assert.assertTrue(seq.isLast());
    }

    @Test
    public void forPromptIncludesGoal() {
        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        String prompt = seq.forPrompt();
        Assert.assertTrue(prompt.contains("mode=happy"));
        Assert.assertTrue(prompt.contains("goal=Exercise the primary happy path"));
    }

    @Test
    public void sessionSkippedWhenNoLoginUsername() throws Exception {
        Path root = Files.createTempDirectory("hunt-seq-session-skip");
        HuntCoverageMap coverage = new HuntCoverageMap(root);
        HuntStrategySequencer seq = new HuntStrategySequencer(true);
        for (int i = 0; i < 4; i++) {
            seq.advance();
        }
        Assert.assertEquals(seq.current().mode(), "session");

        LiveHuntService.skipSessionIfNoLogin(seq, coverage, false);

        Assert.assertEquals(seq.current().mode(), "invent");
        Assert.assertTrue(coverage.forPrompt().contains("session"));
    }

    private static final int MODES_BEFORE_LAST = 5;
}
