package delivery.heal;

import delivery.codegen.ProvenStep;
import delivery.evidence.Phase2EvidenceConfig;
import delivery.evidence.Phase2Session;
import delivery.prompts.PromptCatalog;
import org.json.JSONObject;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * AE emit-only heal proposals under {@code {projectRoot}/heal-proposals/{proposalId}.json}.
 * Never rewrites generated Java. Skips when pageClass missing (do not invent).
 */
public final class HealProposalWriter {
    private HealProposalWriter() {
    }

    public static Path emitPending(
            Path projectRoot,
            String projectId,
            String jobId,
            String tcId,
            int stepIndex,
            String pageClass,
            HealProposal.LocatorRef before,
            HealProposal.LocatorRef after,
            String tier,
            String reason,
            String evidenceRel
    ) {
        if (!Phase2EvidenceConfig.healProposeOnly()) {
            return null;
        }
        if (projectRoot == null) {
            projectRoot = Phase2Session.projectRoot();
        }
        if (projectRoot == null) {
            LogsManager.warn("PHASE2_HEAL_PROPOSAL: no projectRoot");
            return null;
        }
        if (pageClass == null || pageClass.isBlank()) {
            LogsManager.warn("PHASE2_HEAL_PROPOSAL: skip — missing pageClass (never invent)");
            return null;
        }
        if (after == null || after.value() == null || after.value().isBlank()) {
            LogsManager.warn("PHASE2_HEAL_PROPOSAL: skip — empty locatorAfter");
            return null;
        }
        String id = UUID.randomUUID().toString();
        HealProposal proposal = new HealProposal(
                id,
                projectId != null ? projectId : Phase2Session.projectId(),
                jobId != null ? jobId : Phase2Session.jobId(),
                tcId,
                Math.max(0, stepIndex),
                pageClass.trim(),
                before,
                after,
                "locator",
                tier,
                reason,
                "pending",
                null,
                null,
                evidenceRel
        );
        try {
            Path dir = projectRoot.resolve("heal-proposals");
            Files.createDirectories(dir);
            Path file = dir.resolve(id + ".json");
            delivery.store.Atomic.writeString(file, toJson(proposal).toString(2), java.nio.charset.StandardCharsets.UTF_8);
            LogsManager.info("PHASE2_HEAL_PROPOSAL: wrote " + file.getFileName());
            return file;
        } catch (Exception e) {
            LogsManager.warn("PHASE2_HEAL_PROPOSAL: " + e.getMessage());
            return null;
        }
    }

    /**
     * Prefer first healed step that carries a locator + pageName.
     * Returns null if no existing page class can be named.
     */
    public static Path emitFromHealResult(
            Path projectRoot,
            String projectId,
            String jobId,
            String tcId,
            int stepIndex,
            List<FailedLocator> failed,
            HealResult healed,
            String evidenceRel
    ) {
        if (healed == null || !healed.ok() || healed.steps() == null || healed.steps().isEmpty()) {
            return null;
        }
        ProvenStep pick = null;
        for (ProvenStep s : healed.steps()) {
            if (s == null) {
                continue;
            }
            if (s.locatorValue() != null && !s.locatorValue().isBlank()
                    && s.pageName() != null && !s.pageName().isBlank()) {
                pick = s;
                break;
            }
        }
        if (pick == null) {
            LogsManager.warn("PHASE2_HEAL_PROPOSAL: skip — no pageClass/locator on healed steps");
            return null;
        }
        HealProposal.LocatorRef before = new HealProposal.LocatorRef("", "");
        if (failed != null && !failed.isEmpty() && failed.get(0) != null) {
            FailedLocator f = failed.get(0);
            before = new HealProposal.LocatorRef(f.strategy(), f.value());
        }
        HealProposal.LocatorRef after = new HealProposal.LocatorRef(pick.locatorStrategy(), pick.locatorValue());
        return emitPending(
                projectRoot, projectId, jobId, tcId, stepIndex,
                pick.pageName(), before, after, healed.tierUsed(),
                healed.reason() == null || healed.reason().isBlank()
                        ? "heal tier " + healed.tierUsed()
                        : healed.reason(),
                evidenceRel);
    }

    static JSONObject toJson(HealProposal p) {
        JSONObject o = new JSONObject();
        o.put("proposalId", p.proposalId());
        o.put("projectId", p.projectId());
        o.put("jobId", p.jobId());
        o.put("tcId", p.tcId());
        o.put("stepIndex", p.stepIndex());
        o.put("pageClass", p.pageClass());
        o.put("locatorBefore", loc(p.locatorBefore()));
        o.put("locatorAfter", loc(p.locatorAfter()));
        o.put("patchKind", p.patchKind());
        o.put("tier", p.tier());
        o.put("reason", p.reason());
        o.put("status", p.status());
        o.put("createdAt", p.createdAt());
        if (p.decidedAt() == null) {
            o.put("decidedAt", JSONObject.NULL);
        } else {
            o.put("decidedAt", p.decidedAt());
        }
        if (p.evidenceRel() == null) {
            o.put("evidenceRel", JSONObject.NULL);
        } else {
            o.put("evidenceRel", p.evidenceRel());
        }
        o.put("promptsVersion", PromptCatalog.version());
        return o;
    }

    private static JSONObject loc(HealProposal.LocatorRef ref) {
        JSONObject o = new JSONObject();
        o.put("strategy", ref == null ? "" : ref.strategy());
        o.put("value", ref == null ? "" : ref.value());
        return o;
    }
}
