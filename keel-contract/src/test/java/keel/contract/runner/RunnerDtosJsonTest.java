package keel.contract.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class RunnerDtosJsonTest {
    private final ObjectMapper json = new ObjectMapper();

    @DataProvider
    public Object[][] dtos() {
        return new Object[][]{
                {new RunnerDtos.ClaimRequest(List.of(RunnerCapability.BROWSER_CHROME), List.of(WireJobKind.EXECUTE))},
                {new RunnerDtos.ClaimResponse("j1", "a1", 3, 7, "p1", WireJobKind.HUNT, "lib", "env", "h", "allow", "2026-10-13T00:00:00Z")},
                {new RunnerDtos.LeaseResponse("j1", true, 2, "2026-10-13T00:00:00Z")},
                {new RunnerDtos.StageRequest(WireStage.EMIT)},
                {new RunnerDtos.ProgressRequest("tc 3/9", 3, 9)},
                {new RunnerDtos.ArtifactChunkResponse("j1", "art", 0, true, false)},
                {new RunnerDtos.ResultRequest("COMPLETED", 5, 1, "ok", "h", "allow")},
                {new RunnerDtos.ErrorBody("LEASE_LOST", "stale fencing token")},
        };
    }

    @Test(dataProvider = "dtos")
    public void roundTrips(Object dto) throws Exception {
        String wire = json.writeValueAsString(dto);
        assertEquals(json.readValue(wire, dto.getClass()), dto, wire);
    }

    @Test
    public void unknownFieldsIgnoredForForwardCompatibility() throws Exception {
        var lease = json.readValue("{\"jobId\":\"j\",\"cancelRequested\":false,\"cancelGeneration\":1,\"future\":42}",
                RunnerDtos.LeaseResponse.class);
        assertEquals(lease.jobId(), "j");
    }

    @Test
    public void claimResponseOmitsNulls() throws Exception {
        String wire = json.writeValueAsString(new RunnerDtos.ClaimResponse("j", "a", 0, 1, null, WireJobKind.EXECUTE,
                null, null, null, null, null));
        assertFalse(wire.contains("null"), wire);
    }
}
