package delivery.portal.jobs;

import delivery.portal.model.JobRecord;
import delivery.runner.EngineAgent;
import keel.contract.runner.WireJobKind;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

/** B08: keel.jobs.dispatch.* parsing, fail-closed validation, and parity with what the engine claims. */
public class JobDispatchPolicyTest {
    private static JobDispatchPolicy policy(Map<String, String> p) {
        return new JobDispatchPolicy(p::get);
    }

    @Test
    public void defaultIsInprocessForEveryKind() {
        JobDispatchPolicy d = policy(Map.of());
        for (JobRecord.JobKind k : JobRecord.JobKind.values()) {
            assertFalse(d.remote(k), k.name());
        }
    }

    @Test
    public void perKindOverridesAndDefaultRemoteOnlyHitsCapableKinds() {
        JobDispatchPolicy d = policy(Map.of("keel.jobs.dispatch.execute", "remote"));
        assertTrue(d.remote(JobRecord.JobKind.EXECUTE));
        assertFalse(d.remote(JobRecord.JobKind.CONVERT));
        JobDispatchPolicy all = policy(Map.of("keel.jobs.dispatch.default", "remote", "keel.jobs.dispatch.convert", "inprocess"));
        assertTrue(all.remote(JobRecord.JobKind.EXECUTE));
        assertFalse(all.remote(JobRecord.JobKind.CONVERT));
        assertFalse(all.remote(JobRecord.JobKind.HUNT), "default=remote never sends an unsupported kind to the engine");
    }

    @Test
    public void explicitRemoteForUnsupportedKindOrBadValueRefusesStartup() {
        var e = expectThrows(IllegalStateException.class, () -> policy(Map.of("keel.jobs.dispatch.hunt", "remote")));
        assertTrue(e.getMessage().startsWith("DISPATCH_UNSUPPORTED"));
        expectThrows(IllegalStateException.class, () -> policy(Map.of("keel.jobs.dispatch.execute", "cloud")));
    }

    @Test
    public void remoteCapableKindsMatchWhatTheEngineClaims() {
        assertEquals(JobDispatchPolicy.REMOTE_CAPABLE.stream().map(Enum::name).collect(Collectors.toSet()),
                EngineAgent.REMOTE_KINDS.stream().map(WireJobKind::name).collect(Collectors.toSet()));
    }

    @Test
    public void portalDoesNotClaimHuntOrGenerateAsRemote() {
        // D-QA-08: 0a EngineAgent REMOTE_KINDS is EXECUTE+CONVERT only
        assertFalse(EngineAgent.REMOTE_KINDS.contains(WireJobKind.HUNT));
        assertFalse(EngineAgent.REMOTE_KINDS.contains(WireJobKind.GENERATE_BATCH));
        assertFalse(EngineAgent.REMOTE_KINDS.contains(WireJobKind.GENERATE_COMPARE));
        JobDispatchPolicy all = policy(Map.of("keel.jobs.dispatch.default", "remote"));
        assertFalse(all.remote(JobRecord.JobKind.HUNT));
        assertFalse(all.remote(JobRecord.JobKind.GENERATE_BATCH));
        assertFalse(all.remote(JobRecord.JobKind.GENERATE_COMPARE));
        expectThrows(IllegalStateException.class, () -> policy(Map.of("keel.jobs.dispatch.generate-batch", "remote")));
        expectThrows(IllegalStateException.class, () -> policy(Map.of("keel.jobs.dispatch.generate-compare", "remote")));
        expectThrows(IllegalStateException.class, () -> policy(Map.of("keel.jobs.dispatch.hunt", "remote")));
    }
}
