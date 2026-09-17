package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.ir.TcDraft;
import delivery.portal.model.JobRecord;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Actual providers invoked for a job, distinct from the frozen allowlist. */
public final class ProviderUsage {
    public record Snapshot(
            String allowed,
            String used,
            boolean fallbackUsed,
            String fallbackFrom,
            String fallbackTo,
            String fallbackReason
    ) {
    }

    private ProviderUsage() {
    }

    public static Snapshot from(JobRecord job, List<TcDraft> drafts) {
        String allowed = job == null ? "" : nz(job.getProviderAllowlistSnapshot());
        if (job != null && !nz(job.getProvidersUsed()).isBlank() && (drafts == null || drafts.isEmpty())) {
            boolean fallback = job.isFallbackUsed() || messageFallback(job);
            String reason = nz(job.getFallbackReason());
            if (reason.isBlank() && fallback) {
                reason = messageFallbackReason(job);
            }
            return new Snapshot(
                    allowed,
                    job.getProvidersUsed(),
                    fallback,
                    fallback ? "precision" : "",
                    fallback ? "keel" : "",
                    reason);
        }
        Set<String> used = new LinkedHashSet<>();
        boolean fallback = false;
        String reason = "";
        if (job != null) {
            AuthoringEngine engine = job.getAuthoringEngine();
            if (engine != null) {
                used.add(engine.wireValue());
            }
            if (job.getGenerateModel() != null && !job.getGenerateModel().isBlank()) {
                used.add("ollama");
            }
            if (messageFallback(job)) {
                fallback = true;
                reason = messageFallbackReason(job);
                used.add("precision");
                used.add("keel");
            }
        }
        if (drafts != null) {
            for (TcDraft draft : drafts) {
                if (draft == null) {
                    continue;
                }
                if (draft.jobAuthoringEngine() != null && !draft.jobAuthoringEngine().isBlank()) {
                    used.add(draft.jobAuthoringEngine().toLowerCase(Locale.ROOT));
                }
                if (draft.precisionFallback()) {
                    fallback = true;
                    if (reason.isBlank()) {
                        reason = nz(draft.precisionFallbackReason());
                    }
                    used.add("precision");
                    used.add("keel");
                }
                String heal = draft.healTier();
                if (heal != null && !heal.isBlank() && !"none".equalsIgnoreCase(heal)) {
                    used.add(heal.toLowerCase(Locale.ROOT));
                }
            }
        }
        return new Snapshot(
                allowed,
                String.join(",", used),
                fallback,
                fallback ? "precision" : "",
                fallback ? "keel" : "",
                reason);
    }

    public static void apply(JobRecord job, List<TcDraft> drafts) {
        if (job == null) {
            return;
        }
        Snapshot snap = from(job, drafts);
        job.setProvidersUsed(snap.used());
        job.setFallbackUsed(snap.fallbackUsed());
        job.setFallbackReason(snap.fallbackReason());
    }

    private static boolean messageFallback(JobRecord job) {
        return job.getMessage() != null && job.getMessage().contains("PRECISION_FALLBACK");
    }

    private static String messageFallbackReason(JobRecord job) {
        String message = job.getMessage() == null ? "" : job.getMessage();
        int open = message.lastIndexOf('(');
        int close = message.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return message.substring(open + 1, close).trim();
        }
        return "fallback";
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }
}
