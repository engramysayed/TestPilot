package delivery.mobile.automate;

import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class MobileDeviceChoiceTest {

    @Test
    public void choiceSurvivesANewProcess() throws Exception {
        Path root = Files.createTempDirectory("device-choice");
        MobileDeviceChoice.save(root, "job_abc", "pixel-1");
        assertEquals(MobileDeviceChoice.load(root, "job_abc"), "pixel-1");
        MobileDeviceChoice.save(root, "job_abc", "");
        assertEquals(MobileDeviceChoice.load(root, "job_abc"), "");
        assertNull(MobileDeviceChoice.load(root, "../secret"));
    }
}
