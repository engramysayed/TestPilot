package delivery.heal;

import java.time.Instant;

/**
 * Phase 2 heal proposal disk DTO (AE emit only; portal Accept/Reject applies Java).
 */
public record HealProposal(
        String proposalId,
        String projectId,
        String jobId,
        String tcId,
        int stepIndex,
        String pageClass,
        LocatorRef locatorBefore,
        LocatorRef locatorAfter,
        String patchKind,
        String tier,
        String reason,
        String status,
        String createdAt,
        String decidedAt,
        String evidenceRel
) {
    public record LocatorRef(String strategy, String value) {
        public LocatorRef {
            strategy = strategy == null ? "" : strategy;
            value = value == null ? "" : value;
        }
    }

    public HealProposal {
        proposalId = proposalId == null ? "" : proposalId;
        projectId = projectId == null ? "" : projectId;
        jobId = jobId == null ? "" : jobId;
        tcId = tcId == null ? "" : tcId;
        pageClass = pageClass == null ? "" : pageClass;
        locatorBefore = locatorBefore == null ? new LocatorRef("", "") : locatorBefore;
        locatorAfter = locatorAfter == null ? new LocatorRef("", "") : locatorAfter;
        patchKind = (patchKind == null || patchKind.isBlank()) ? "locator" : patchKind;
        tier = tier == null ? "" : tier;
        reason = reason == null ? "" : reason;
        status = (status == null || status.isBlank()) ? "pending" : status;
        createdAt = (createdAt == null || createdAt.isBlank()) ? Instant.now().toString() : createdAt;
        decidedAt = decidedAt;
        evidenceRel = evidenceRel;
    }
}
