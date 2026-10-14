package delivery.job.port;

import delivery.job.ProvePhase;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Project/site memory a worker reads or patches: design references and generated-workbook heal patches.
 * Pure file logic lives portal-side today, so workers reach it only through this port.
 */
public interface SiteMemoryPort {
    Optional<Path> designReference(Path projectRoot, String tcId);

    Optional<Path> pickActualScreenshot(Path evidenceDir) throws java.io.IOException;

    /** @return applier or {@code null} when heal patches cannot be written back from here. */
    ProvePhase.HealWorkbookApplier healWorkbookApplier(Path storeRoot);
}
