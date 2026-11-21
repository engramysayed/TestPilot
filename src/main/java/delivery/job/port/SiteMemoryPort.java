package delivery.job.port;

import delivery.job.ProvePhase;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Project/site memory a worker reads or patches: design references and generated-workbook heal patches.
 * Pure file logic lives portal-side today, so workers reach it only through this port.
 */
public interface SiteMemoryPort {
    Optional<Path> designReference(Path projectRoot, String tcId);

    Optional<Path> pickActualScreenshot(Path evidenceDir) throws java.io.IOException;

    /** Step indices (1..999) a user attached a Figma to for this TC. Empty when none. */
    default List<Integer> designReferenceSteps(Path projectRoot, String tcId) throws java.io.IOException {
        return List.of();
    }

    /** Per-step Figma reference. Empty when the user attached nothing to this step. */
    default Optional<Path> designReferenceStep(Path projectRoot, String tcId, int stepIndex) {
        return Optional.empty();
    }

    /** The specific {@code step-NNN.png} inside an evidence directory. Empty when the step did not screenshot. */
    default Optional<Path> stepScreenshot(Path evidenceDir, int stepIndex) {
        return Optional.empty();
    }

    /** @return applier or {@code null} when heal patches cannot be written back from here. */
    ProvePhase.HealWorkbookApplier healWorkbookApplier(Path storeRoot);
}
