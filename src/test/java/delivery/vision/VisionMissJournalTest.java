package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class VisionMissJournalTest {
    @Test
    public void diagnosticsStayScopedAndMaskSecrets() throws Exception {
        Path root = Files.createTempDirectory("vision-scopes-");
        Assert.assertNull(VisionMissJournal.logDir());
        try (var alice = VisionMissJournal.activate(root.resolve("alice"), "private-value")) {
            VisionMissJournal.recordGrounding("Fill private-value", "miss", null, 0.5,
                    "password=private-value", "none", "raw-private-value");
            try (var bob = VisionMissJournal.activate(root.resolve("bob"))) {
                VisionMissJournal.recordGrounding("Bob", "miss", null, 0.5, "", "none", null);
            }
            Assert.assertEquals(VisionMissJournal.logDir(), root.resolve("alice/vision-diagnostics"));
        }
        Assert.assertNull(VisionMissJournal.logDir());
        try (var files = Files.walk(root.resolve("alice"))) {
            String data = Files.readString(files.filter(Files::isRegularFile).findFirst().orElseThrow());
            Assert.assertFalse(data.contains("private-value"));
            Assert.assertFalse(data.contains("Bob"));
            Assert.assertFalse(data.contains("rawClip"));
        }
    }

    @Test
    public void appendsGroundingJsonl() throws Exception {
        Path dir = Path.of("target/vision-miss-test-" + System.nanoTime());
        System.setProperty("delivery.vision.miss-log.enabled", "true");
        System.setProperty("delivery.vision.miss-log.dir", dir.toString());
        try (var scope = VisionMissJournal.activate(dir.getParent().resolve(dir.getFileName()))) {
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
            Assert.assertTrue(Files.isDirectory(dir.resolve("vision-diagnostics")));
            try (var stream = Files.list(dir.resolve("vision-diagnostics"))) {
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
