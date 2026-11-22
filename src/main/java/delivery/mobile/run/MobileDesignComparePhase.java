package delivery.mobile.run;

import delivery.authoring.LocalLlmClient;
import delivery.portal.service.DesignReferenceService;
import delivery.vision.DesignCompareEvidence;
import delivery.vision.DesignCompareGate;
import delivery.vision.DesignCompareResult;
import delivery.vision.VisionGroundingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Mobile Figma Validate comparator. Mirrors {@link delivery.job.DesignComparePhase} for mobile runs:
 * for each completed TC it compares the whole-TC Figma against the final step screenshot, and each
 * uploaded per-step Figma against that step's screenshot. Reads the already-shipped per-TC and
 * per-step storage (<projectRoot>/design-references/...), compares via {@link DesignCompareGate}
 * (same vision model as web — no Cursor), and writes evidence via {@link DesignCompareEvidence}.
 *
 * <p>Depends on mobile Phase 4 (runner writes step-NNN.png into
 * {@code <storeRoot>/mobile-runs/<runId>/evidence/<tcId>/}). Skips cleanly when no PNGs are there.
 */
final class MobileDesignComparePhase {

    private static final Logger log = LoggerFactory.getLogger(MobileDesignComparePhase.class);

    private final DesignReferenceService references = new DesignReferenceService();
    private final Path projectRoot;
    private final Path evidenceDir;
    private final LocalLlmClient visionClient;
    private final String provider;
    private final String model;

    MobileDesignComparePhase(Path projectRoot, Path evidenceDir, LocalLlmClient visionClient) {
        this.projectRoot = projectRoot;
        this.evidenceDir = evidenceDir;
        this.visionClient = visionClient;
        String p;
        String m;
        try {
            p = VisionGroundingConfig.assertProviderId();
            m = VisionGroundingConfig.assertModel();
        } catch (RuntimeException e) {
            // Vision config may not be wired in a dry-run; evidence still writes with blanks.
            p = "";
            m = "";
        }
        this.provider = p;
        this.model = m;
    }

    /** Compare uploaded Figmas against this run's step screenshots. Never throws. */
    void run(List<String> tcIds) {
        if (projectRoot == null || evidenceDir == null || tcIds == null || tcIds.isEmpty()) {
            return;
        }
        for (String tcId : tcIds) {
            if (tcId == null || tcId.isBlank()) continue;
            Path tcEvidence = evidenceDir.resolve(tcId);
            Optional<Path> tcRef = references.resolve(projectRoot, tcId);
            if (tcRef.isPresent()) {
                compareTcLevel(tcRef.get(), tcEvidence, tcId);
            }
            try {
                for (int stepIndex : references.listSteps(projectRoot, tcId)) {
                    compareStep(tcEvidence, tcId, stepIndex);
                }
            } catch (Exception e) {
                log.warn("Mobile design compare {} step listing failed: {}", tcId, e.getMessage());
            }
        }
    }

    private void compareTcLevel(Path reference, Path tcEvidence, String tcId) {
        try {
            Optional<Path> actual = DesignReferenceService.pickActualScreenshot(tcEvidence);
            if (actual.isEmpty()) {
                DesignCompareEvidence.write(tcEvidence, tcId,
                        DesignCompareResult.uncertain("no actual screenshot"),
                        null, null, provider, model);
                return;
            }
            byte[] refBytes = Files.readAllBytes(reference);
            byte[] actBytes = Files.readAllBytes(actual.get());
            DesignCompareResult result = DesignCompareGate.compare(refBytes, actBytes, visionClient);
            DesignCompareEvidence.write(tcEvidence, tcId, result, actual.get(), actBytes, provider, model);
        } catch (Exception e) {
            log.warn("Mobile design compare {}: {}", tcId, e.getMessage());
            DesignCompareEvidence.write(tcEvidence, tcId,
                    DesignCompareResult.uncertain(e.getMessage()),
                    null, null, provider, model);
        }
    }

    private void compareStep(Path tcEvidence, String tcId, int stepIndex) {
        Optional<Path> reference = references.resolveStep(projectRoot, tcId, stepIndex);
        if (reference.isEmpty()) return;
        try {
            Path actual = tcEvidence.resolve(String.format(java.util.Locale.ROOT, "step-%03d.png", stepIndex));
            if (!Files.isRegularFile(actual)) {
                DesignCompareEvidence.writeStep(tcEvidence, tcId, stepIndex,
                        DesignCompareResult.uncertain("no screenshot for step " + stepIndex),
                        null, null, provider, model);
                return;
            }
            byte[] refBytes = Files.readAllBytes(reference.get());
            byte[] actBytes = Files.readAllBytes(actual);
            DesignCompareResult result = DesignCompareGate.compare(refBytes, actBytes, visionClient);
            DesignCompareEvidence.writeStep(tcEvidence, tcId, stepIndex, result, actual, actBytes, provider, model);
        } catch (Exception e) {
            log.warn("Mobile design compare {} step {}: {}", tcId, stepIndex, e.getMessage());
            DesignCompareEvidence.writeStep(tcEvidence, tcId, stepIndex,
                    DesignCompareResult.uncertain(e.getMessage()),
                    null, null, provider, model);
        }
    }

}
