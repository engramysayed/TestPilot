package delivery.job;

import delivery.ir.TcDraft;

import java.util.List;

final class PrecisionFallbackMessage {
    private PrecisionFallbackMessage() {
    }

    static String appendIfNeeded(List<TcDraft> drafts, String message) {
        if (drafts == null || drafts.isEmpty()) {
            return message;
        }
        boolean fallback = drafts.stream().anyMatch(TcDraft::precisionFallback);
        if (!fallback) {
            return message;
        }
        String reason = drafts.stream()
                .filter(TcDraft::precisionFallback)
                .map(TcDraft::precisionFallbackReason)
                .filter(r -> r != null && !r.isBlank())
                .findFirst()
                .orElse("fallback");
        String notice = "PRECISION_FALLBACK: Precision engine fell back to Keel (" + reason + ")";
        if (message == null || message.isBlank()) {
            return notice;
        }
        if (message.contains("PRECISION_FALLBACK")) {
            return message;
        }
        return message + " — " + notice;
    }
}
