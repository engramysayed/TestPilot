package delivery.mobile.runner.evidence;

import delivery.mobile.result.FailureEvidenceManifest;
import delivery.mobile.runner.device.CrashDetectionResult;
import delivery.mobile.runner.device.MobileCrashDetector;
import delivery.mobile.runner.snapshot.MobileSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Collects failure evidence under a 5s budget taken from the case deadline (not teardown).
 * Order: redacted XML → masked screenshot → top-5 nearest candidates → crash classification.
 */
public final class MobileFailureEvidenceCollector {
    public static final long DEFAULT_BUDGET_MS = 5_000L;

    private final long budgetMs;
    private final LongSupplier monoMs;
    private final MobileCrashDetector crashDetector;

    public MobileFailureEvidenceCollector(long budgetMs, LongSupplier monoMs, MobileCrashDetector crashDetector) {
        this.budgetMs = budgetMs > 0 ? budgetMs : DEFAULT_BUDGET_MS;
        this.monoMs = monoMs == null ? System::currentTimeMillis : monoMs;
        this.crashDetector = Objects.requireNonNull(crashDetector);
    }

    public MobileFailureEvidenceCollector(MobileCrashDetector crashDetector) {
        this(DEFAULT_BUDGET_MS, System::currentTimeMillis, crashDetector);
    }

    public FailureEvidenceManifest collect(EvidenceRequest req) {
        long start = monoMs.getAsLong();
        long deadline = start + budgetMs;
        boolean partial = false;

        String xmlRef = "";
        String shotRef = "";
        List<FailureEvidenceManifest.NearestCandidateRef> nearest = List.of();
        String logRef = "";
        boolean crashed = false;
        boolean healSkip = false;
        boolean visionSkip = false;
        String failureCode = req.failureCode();

        try {
            if (monoMs.getAsLong() < deadline && req.snapshot() != null) {
                String xml = XmlRedactor.toRedactedXml(req.snapshot());
                if (xml.contains(XmlRedactor.SECRET_CANARY)) {
                    xml = xml.replace(XmlRedactor.SECRET_CANARY, XmlRedactor.REDACTED);
                }
                xmlRef = "mem:redacted-xml:" + xml.getBytes(StandardCharsets.UTF_8).length;
                req.onRedactedXml().accept(xml);
            } else if (req.snapshot() != null) {
                partial = true;
            }

            if (monoMs.getAsLong() < deadline && req.screenshotRgba() != null && req.snapshot() != null) {
                byte[] masked = ScreenshotMasker.mask(
                        req.screenshotRgba(), req.screenshotWidth(), req.screenshotHeight(), req.snapshot());
                shotRef = "mem:masked-shot:" + masked.length;
                req.onMaskedScreenshot().accept(masked);
            } else if (req.screenshotRgba() != null) {
                partial = true;
            }

            if (monoMs.getAsLong() < deadline && req.snapshot() != null) {
                nearest = NearestCandidateRanker.topK(req.snapshot(), req.intendedTarget(), 5);
            } else {
                partial = true;
            }

            if (monoMs.getAsLong() < deadline) {
                CrashDetectionResult crash = crashDetector.detect(
                        req.appPackage(), req.stepStartedAtEpochMs(), req.logcatCrashBuffer());
                if (crash.appCrashed()) {
                    crashed = true;
                    failureCode = "APP_CRASHED";
                    healSkip = true;
                    visionSkip = true;
                    logRef = crash.excerptRef();
                    req.onLogcatExcerpt().accept(crash.redactedExcerpt());
                }
            } else {
                partial = true;
            }
        } catch (RuntimeException e) {
            partial = true;
        }

        long elapsed = Math.max(0, monoMs.getAsLong() - start);
        if (elapsed >= budgetMs) partial = true;

        return new FailureEvidenceManifest(
                req.stepId(),
                failureCode,
                partial,
                budgetMs,
                elapsed,
                xmlRef,
                shotRef,
                nearest,
                logRef,
                crashed,
                healSkip,
                visionSkip
        );
    }

    /** Input bundle for evidence collection. */
    public record EvidenceRequest(
            String stepId,
            String failureCode,
            String intendedTarget,
            String appPackage,
            long stepStartedAtEpochMs,
            MobileSnapshot snapshot,
            byte[] screenshotRgba,
            int screenshotWidth,
            int screenshotHeight,
            String logcatCrashBuffer,
            java.util.function.Consumer<String> onRedactedXml,
            java.util.function.Consumer<byte[]> onMaskedScreenshot,
            java.util.function.Consumer<String> onLogcatExcerpt
    ) {
        public EvidenceRequest {
            onRedactedXml = onRedactedXml == null ? s -> {} : onRedactedXml;
            onMaskedScreenshot = onMaskedScreenshot == null ? b -> {} : onMaskedScreenshot;
            onLogcatExcerpt = onLogcatExcerpt == null ? s -> {} : onLogcatExcerpt;
            logcatCrashBuffer = logcatCrashBuffer == null ? "" : logcatCrashBuffer;
            intendedTarget = intendedTarget == null ? "" : intendedTarget;
            appPackage = appPackage == null ? "" : appPackage;
        }
    }
}
