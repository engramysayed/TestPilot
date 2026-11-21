package delivery.portal.engine;

import delivery.job.ProvePhase;
import delivery.job.port.SiteMemoryPort;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.DesignReferenceService;
import delivery.portal.service.GeneratedWorkbookService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** B05: in-process {@link SiteMemoryPort}, registered via META-INF/services. Same behaviour as before the seam. */
public final class InProcessEnginePorts implements SiteMemoryPort {
    private final DesignReferenceService references = new DesignReferenceService();

    @Override
    public Optional<Path> designReference(Path projectRoot, String tcId) {
        return references.resolve(projectRoot, tcId);
    }

    @Override
    public Optional<Path> pickActualScreenshot(Path evidenceDir) throws IOException {
        return DesignReferenceService.pickActualScreenshot(evidenceDir);
    }

    @Override
    public List<Integer> designReferenceSteps(Path projectRoot, String tcId) throws IOException {
        return references.listSteps(projectRoot, tcId);
    }

    @Override
    public Optional<Path> designReferenceStep(Path projectRoot, String tcId, int stepIndex) {
        return references.resolveStep(projectRoot, tcId, stepIndex);
    }

    @Override
    public Optional<Path> stepScreenshot(Path evidenceDir, int stepIndex) {
        if (evidenceDir == null || stepIndex < 1) {
            return Optional.empty();
        }
        Path file = evidenceDir.resolve(String.format(Locale.ROOT, "step-%03d.png", stepIndex));
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    @Override
    public ProvePhase.HealWorkbookApplier healWorkbookApplier(Path storeRoot) {
        if (storeRoot == null) {
            return null;
        }
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(storeRoot.toString());
        GeneratedWorkbookService workbooks = new GeneratedWorkbookService(props);
        return (projectId, tcId, steps, notes, baseUrl) -> {
            GeneratedWorkbookService.HealPatchApplyResult result =
                    workbooks.applyHealRecoveryPatch(projectId, tcId, steps, notes, baseUrl);
            if (result.excelSaved()) {
                utils.LogsManager.info("HEAL_WORKBOOK_PATCH: saved for " + tcId);
            } else if (result.skipped() && result.reason() != null && !result.reason().isBlank()) {
                utils.LogsManager.warn(result.reason());
            }
        };
    }
}
