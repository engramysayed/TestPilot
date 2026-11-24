package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.portal.persistence.ProjectRepository;
import delivery.portal.service.credits.CreditService;
import delivery.portal.service.credits.InsufficientCreditsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Per-workspace opt-in gate for the unified agent harness + validation (Slice 1 of the Nov-17 plan).
 * Stored at {@code {projectRoot}/ai-policy.json}. When no file exists, the default follows
 * {@code delivery.agent-harness.workspace-default-on}. A saved file always wins.
 *
 * <p>Fixes blocker B1 from the Phase 6.16 production readiness audit (per-tenant/project scoping).
 */
@Service
public class WorkspaceAiPolicyService implements delivery.job.port.WorkspaceAiPolicy {
    public static final String FILE_NAME = "ai-policy.json";

    private final PortalStore store;
    private final ProjectRepository projects;
    private final CreditService credits;
    private final boolean defaultOn;
    private final boolean forceOn;
    private final ObjectMapper mapper = new ObjectMapper();
    static final String FORCED_ON_AT = "forcedOnAt";

    public WorkspaceAiPolicyService(PortalStore store, ProjectRepository projects, CreditService credits,
                                    @Value("${delivery.agent-harness.workspace-default-on:false}") boolean defaultOn,
                                    @Value("${delivery.agent-harness.workspace-force-on:false}") boolean forceOn) {
        this.store = store;
        this.projects = projects;
        this.credits = credits;
        this.defaultOn = defaultOn;
        this.forceOn = forceOn;
    }

    /**
     * Debit the project owner for one AI validation task after it completes successfully.
     * No-op when the AI_VALIDATION price is 0, when credits are disabled, when the owner cannot
     * be resolved, or when the taskId is blank. Idempotent per taskId (CreditService.tryDebitForAddon).
     *
     * @throws InsufficientCreditsException when the owner's balance cannot cover the charge
     */
    public void chargeForAiTask(String projectId, String taskId) {
        if (credits == null || projectId == null || projectId.isBlank() || taskId == null || taskId.isBlank()) {
            return;
        }
        Long ownerId = projects.findByProjectId(projectId).map(e -> e.getOwnerUserId()).orElse(null);
        if (ownerId == null) return;
        credits.tryDebitForAddon(ownerId, "AI_VALIDATION", taskId);
    }

    /** All toggles, default off. */
    public record Policy(
            boolean aiValidationEnabled,
            boolean tcValidationEnabled,
            boolean frameworkRepairEnabled,
            boolean failureInvestigationEnabled,
            boolean huntValidationEnabled
    ) {
        public static Policy disabled() {
            return new Policy(false, false, false, false, false);
        }

        public static Policy enabled() {
            return new Policy(true, true, true, true, true);
        }

        public Policy withAiValidation(boolean v) {
            return new Policy(v, tcValidationEnabled, frameworkRepairEnabled,
                    failureInvestigationEnabled, huntValidationEnabled);
        }
    }

    public Policy load(String projectId) {
        if (projectId == null || projectId.isBlank()) return missing();
        Path file = fileFor(projectId).orElse(null);
        if (file == null || !Files.isRegularFile(file)) return missing();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = mapper.readValue(file.toFile(), Map.class);
            if (forceOn && !doc.containsKey(FORCED_ON_AT)) {
                Policy on = Policy.enabled();
                try {
                    save(projectId, on);
                } catch (Exception ignored) {
                    // The gates still see every toggle on. The next read tries the stamp again.
                }
                return on;
            }
            return new Policy(
                    bool(doc, "aiValidationEnabled"),
                    bool(doc, "tcValidationEnabled"),
                    bool(doc, "frameworkRepairEnabled"),
                    bool(doc, "failureInvestigationEnabled"),
                    bool(doc, "huntValidationEnabled"));
        } catch (Exception e) {
            return missing();
        }
    }

    private Policy missing() {
        return defaultOn ? Policy.enabled() : Policy.disabled();
    }

    public void save(String projectId, Policy policy) throws IOException {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId required");
        }
        Path file = fileFor(projectId).orElseThrow(
                () -> new IllegalStateException("No project root for " + projectId));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("aiValidationEnabled", policy.aiValidationEnabled());
        doc.put("tcValidationEnabled", policy.tcValidationEnabled());
        doc.put("frameworkRepairEnabled", policy.frameworkRepairEnabled());
        doc.put("failureInvestigationEnabled", policy.failureInvestigationEnabled());
        doc.put("huntValidationEnabled", policy.huntValidationEnabled());
        if (forceOn) {
            doc.put(FORCED_ON_AT, "2026-11-24");
        }
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), "ai-policy-", ".tmp");
        try {
            Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc),
                    StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException _unused) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Convenience predicates callers use at bridge entry. */
    public boolean isAiValidationEnabled(String projectId) {
        return load(projectId).aiValidationEnabled();
    }

    public boolean isTcValidationEnabled(String projectId) {
        Policy p = load(projectId);
        return p.aiValidationEnabled() && p.tcValidationEnabled();
    }

    public boolean isFrameworkRepairEnabled(String projectId) {
        Policy p = load(projectId);
        return p.aiValidationEnabled() && p.frameworkRepairEnabled();
    }

    public boolean isFailureInvestigationEnabled(String projectId) {
        Policy p = load(projectId);
        return p.aiValidationEnabled() && p.failureInvestigationEnabled();
    }

    public boolean isHuntValidationEnabled(String projectId) {
        Policy p = load(projectId);
        return p.aiValidationEnabled() && p.huntValidationEnabled();
    }

    private Optional<Path> fileFor(String projectId) {
        try {
            Path root = store.projectDiskRoot(projectId);
            return Optional.ofNullable(root).map(r -> r.resolve(FILE_NAME));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static boolean bool(Map<String, Object> doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }
}
