package delivery.revise;

import delivery.ir.TcDraft;

import java.util.List;

/**
 * Outcome of Mode-B final revise (before codegen).
 */
public record FinalReviseResult(
        List<TcDraft> drafts,
        JobVerdict jobVerdict,
        String reportMarkdown,
        String summaryMessage,
        boolean ran
) {
    public enum JobVerdict {
        SKIPPED,
        SHIP,
        SHIP_WITH_REVIEW,
        BLOCK
    }

    public static FinalReviseResult skipped(List<TcDraft> drafts) {
        return new FinalReviseResult(drafts, JobVerdict.SKIPPED, "", "final revise skipped", false);
    }

    public boolean softBlocked() {
        return jobVerdict == JobVerdict.BLOCK;
    }

    public String portalJobStatus() {
        return softBlocked() ? "COMPLETED_WITH_BLOCK" : "COMPLETED";
    }
}
