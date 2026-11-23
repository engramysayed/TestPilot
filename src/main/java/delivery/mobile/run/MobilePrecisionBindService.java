package delivery.mobile.run;

import delivery.authoring.PrecisionJobConfig;
import delivery.heal.CursorHealClient;
import delivery.mobile.runner.precision.MobilePrecisionGate;
import delivery.mobile.runner.precision.PrecisionCandidate;
import delivery.mobile.runner.precision.PrecisionInput;
import delivery.mobile.runner.precision.PrecisionOutcome;
import delivery.portal.service.PortalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 7a: portal-side caller for mobile Precision binds. The mobile runner POSTs a bind
 * request (intent + candidates + stepContext), this service invokes Cursor through the same
 * {@link CursorHealClient} the web path uses, feeds the response through
 * {@link MobilePrecisionGate}, and returns the gate outcome.
 *
 * <p>Design is in {@code docs/MOBILE_FRAMEWORK_RULES.md} §5. The sidecar invocation stays on
 * portal so runners don't need Node / sidecar jars / Cursor API keys.
 */
@Service
public class MobilePrecisionBindService {

    private static final Logger log = LoggerFactory.getLogger(MobilePrecisionBindService.class);

    private final RunResolver resolver;
    private final JobConfigResolver configResolver;
    private final CursorHealClientFactory cursorFactory;

    public MobilePrecisionBindService(MobileRunOrchestrator runs, PortalStore portalStore) {
        this(runs::get, portalStore::precisionConfigForProject, CursorHealClient::new);
    }

    /** Package-visible for tests: swap the run lookup, the job config, and the sidecar call. */
    MobilePrecisionBindService(RunResolver resolver, JobConfigResolver configResolver,
                               CursorHealClientFactory cursorFactory) {
        this.resolver = resolver;
        this.configResolver = configResolver;
        this.cursorFactory = cursorFactory;
    }

    /**
     * Resolve + call + validate. {@code tenantId} comes from the runner's bearer token; a run
     * that lives in another tenant → NOT_FOUND. Blank candidate list → IllegalArgumentException.
     */
    public PrecisionOutcome bind(String runId, String tenantId, BindRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("bind request required");
        }
        if (req.candidates() == null || req.candidates().isEmpty()) {
            throw new IllegalArgumentException("at least one candidate required");
        }
        if (req.stepContext() == null || req.stepContext().isBlank()) {
            throw new IllegalArgumentException("stepContext required");
        }
        MobileRun run = resolver.find(runId)
                .filter(r -> tenantId == null || tenantId.equals(r.tenantId))
                .orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));

        PrecisionJobConfig cfg = configResolver.find(run.projectId);
        CursorHealClient cursor = cursorFactory.create(cfg.cursorModel());
        boolean providerAvailable = cursor.isRuntimeReady();

        String chosenId = "";
        String responseText = "";
        if (providerAvailable) {
            try {
                String table = formatCandidatesTable(req.candidates());
                String excerpt = req.snapshotExcerpt() == null ? "" : req.snapshotExcerpt();
                chosenId = cursor.pickCandidateId(
                        req.intent(), "AMBIGUOUS",
                        table,
                        excerpt.length() > 8000 ? excerpt.substring(0, 8000) : excerpt,
                        null);
                responseText = chosenId;
            } catch (RuntimeException e) {
                log.warn("mobile precision cursor call failed for run {}: {}", runId, e.getMessage());
                providerAvailable = false;
            }
        }

        List<PrecisionCandidate> gateCandidates = new ArrayList<>(req.candidates().size());
        for (Candidate c : req.candidates()) {
            gateCandidates.add(new PrecisionCandidate(c.id(), c.contextKind(), c.userOverride()));
        }
        PrecisionInput input = new PrecisionInput(
                gateCandidates,
                chosenId,
                req.stepContext(),
                0, 1,
                providerAvailable,
                cfg.enabled(),
                "cursor",
                responseText,
                false);
        return MobilePrecisionGate.rank(input);
    }

    private static String formatCandidatesTable(List<Candidate> candidates) {
        StringBuilder sb = new StringBuilder(64 + candidates.size() * 64);
        sb.append("id\tlabel\tcontext\tuserOverride\n");
        for (Candidate c : candidates) {
            sb.append(c.id()).append('\t')
                    .append(c.label() == null ? "" : c.label().replace('\t', ' ')).append('\t')
                    .append(c.contextKind()).append('\t')
                    .append(c.userOverride() ? "yes" : "no").append('\n');
        }
        return sb.toString();
    }

    /** One candidate the gate will choose between. Mirror of {@link PrecisionCandidate} plus a label. */
    public record Candidate(String id, String label, String contextKind, boolean userOverride) {
        public Candidate {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("candidate id required");
            if (contextKind == null || contextKind.isBlank()) {
                throw new IllegalArgumentException("contextKind required");
            }
        }
    }

    /** Request envelope from the runner. */
    public record BindRequest(String intent, List<Candidate> candidates,
                              String stepContext, String snapshotExcerpt) {
    }

    /** Package-visible factory so tests can replace the sidecar call. */
    @FunctionalInterface
    interface CursorHealClientFactory {
        CursorHealClient create(String cursorModel);
    }

    /** Package-visible resolver: production wires {@code MobileRunOrchestrator::get}. */
    @FunctionalInterface
    interface RunResolver {
        java.util.Optional<MobileRun> find(String runId);
    }

    /** Package-visible resolver: production wires {@code PortalStore::precisionConfigForProject}. */
    @FunctionalInterface
    interface JobConfigResolver {
        PrecisionJobConfig find(String projectId);
    }
}
