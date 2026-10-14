package delivery.hunt;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class HuntToastObservationTest {
    @Test public void vanishingToastDropped() {
        assertTrue(HuntToastObservation.stillPresent(List.of("Saved!"), List.of()).isEmpty());
    }

    @Test public void persistentAlertKept() {
        var kept = HuntToastObservation.stillPresent(List.of("Invalid password"), List.of("Invalid password"));
        assertEquals(kept.size(), 1);
    }
}
