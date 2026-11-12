package delivery.agent.harness;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SdkTouchMarkerTest {

    @Test
    public void missingMarker_isAmbiguous() {
        Assert.assertEquals(new SdkTouchMarker().readState(Path.of(".")), SdkTouchMarker.State.AMBIGUOUS);
    }

    @Test
    public void falseSidecarStarted_isSafe() throws Exception {
        Path dir = Files.createTempDirectory("sdk-marker-");
        Files.writeString(SdkTouchMarker.markerPath(dir),
                "{\"sdkTouched\":false,\"phase\":\"sidecar_started\"}", StandardCharsets.UTF_8);
        Assert.assertEquals(new SdkTouchMarker().readState(dir), SdkTouchMarker.State.DEFINITELY_SAFE_TO_CREATE);
    }

    @Test
    public void trueMarker_neverSafe() throws Exception {
        Path dir = Files.createTempDirectory("sdk-marker-t-");
        Files.writeString(SdkTouchMarker.markerPath(dir),
                "{\"sdkTouched\":true,\"phase\":\"create_requested\"}", StandardCharsets.UTF_8);
        Assert.assertEquals(new SdkTouchMarker().readState(dir), SdkTouchMarker.State.SDK_TOUCHED);
    }
}
