package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class VisionMissJournalTest {

    @Test
    public void appendsGroundingJsonl() throws Exception {
        Path dir = Path.of("target/vision-miss-test-" + System.nanoTime());
        System.setProperty("delivery.vision.miss-log.enabled", "true");
        System.setProperty("delivery.vision.miss-log.dir", dir.toString());
        try {
            VisionMissJournal.recordGrounding(
                    "Click Login",
                    "miss",
                    new BoundingBox(1, 2, 30, 20),
                    0.85,
                    "Login button",
                    "none",
                    "{\"found\":false}");
            VisionMissJournal.recordGrounding(
                    "Click Login",
                    "grounded",
                    new BoundingBox(1, 2, 30, 20),
                    0.9,
                    "Login button",
                    "c1",
                    null);
            Assert.assertTrue(Files.isDirectory(dir));
            try (var stream = Files.list(dir)) {
                Path file = stream.filter(p -> p.getFileName().toString().startsWith("grounding-"))
                        .findFirst()
                        .orElseThrow();
                String body = Files.readString(file);
                Assert.assertTrue(body.contains("Click Login"));
                Assert.assertTrue(body.contains("miss"));
                Assert.assertFalse(body.contains("\"outcome\":\"grounded\""));
            }
        } finally {
            System.clearProperty("delivery.vision.miss-log.enabled");
            System.clearProperty("delivery.vision.miss-log.dir");
        }
    }
}
