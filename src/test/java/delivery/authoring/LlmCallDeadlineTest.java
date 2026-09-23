package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;
import java.time.Duration;

public class LlmCallDeadlineTest {
    @Test public void boundsNestedCallsAndRestoresScope() throws Exception {
        Duration normal = Duration.ofMinutes(5);
        try (var outer = LlmCallDeadline.within(Duration.ofSeconds(2))) {
            Assert.assertTrue(LlmCallDeadline.limit(normal).compareTo(Duration.ofSeconds(2)) <= 0);
            try (var inner = LlmCallDeadline.within(Duration.ZERO)) {
                Assert.expectThrows(java.io.IOException.class, () -> LlmCallDeadline.limit(normal));
            }
            Assert.assertTrue(LlmCallDeadline.limit(normal).isPositive());
        }
        Assert.assertEquals(LlmCallDeadline.limit(normal), normal);
    }
}
