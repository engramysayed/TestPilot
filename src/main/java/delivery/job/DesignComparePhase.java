package delivery.job;

import delivery.authoring.LocalLlmClient;
import delivery.excel.ManualTestCase;
import delivery.portal.service.DesignReferenceService;
import delivery.store.ProjectStore;
import delivery.vision.DesignCompareEvidence;
import delivery.vision.DesignCompareGate;
import delivery.vision.DesignCompareResult;
import delivery.vision.VisionGroundingConfig;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * After prove, compare design references to actual screenshots per TC (execute runs only).
 */
public final class DesignComparePhase {

    private final DesignReferenceService references = new DesignReferenceService();

    public void run(ConversionJobRequest request, Path workDir, List<ManualTestCase> cases) {
        if (request == null || workDir == null || cases == null || cases.isEmpty()) {
            return;
        }
        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl());
        Path projectRoot = store.projectRoot(request.projectId());
        Path evidenceRoot = workDir.resolve("evidence");
        LocalLlmClient client = createClient(request);
        String provider = VisionGroundingConfig.assertProviderId();
        String model = resolveModel(request);

        for (ManualTestCase tc : cases) {
            Optional<Path> reference = references.resolve(projectRoot, tc.tcId());
            if (reference.isEmpty()) {
                continue;
            }
            Path evidenceDir = evidenceRoot.resolve(tc.tcId());
            try {
                Optional<Path> actual = DesignReferenceService.pickActualScreenshot(evidenceDir);
                if (actual.isEmpty()) {
                    DesignCompareEvidence.write(
                            evidenceDir,
                            tc.tcId(),
                            DesignCompareResult.uncertain("no actual screenshot"),
                            null,
                            null,
                            provider,
                            model);
                    continue;
                }
                byte[] refBytes = Files.readAllBytes(reference.get());
                byte[] actBytes = Files.readAllBytes(actual.get());
                DesignCompareResult result = DesignCompareGate.compare(refBytes, actBytes, client);
                DesignCompareEvidence.write(
                        evidenceDir,
                        tc.tcId(),
                        result,
                        actual.get(),
                        actBytes,
                        provider,
                        model);
            } catch (Exception e) {
                LogsManager.error("DESIGN_COMPARE: " + tc.tcId() + ": " + e.getMessage());
                DesignCompareEvidence.write(
                        evidenceDir,
                        tc.tcId(),
                        DesignCompareResult.uncertain(e.getMessage()),
                        null,
                        null,
                        provider,
                        model);
            }
        }
    }

    private static LocalLlmClient createClient(ConversionJobRequest request) {
        String baseUrl = request.localLlmBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = firstProp("delivery.llm-base-url");
        }
        String model = resolveModel(request);
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            return new LocalLlmClient(baseUrl, model);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String resolveModel(ConversionJobRequest request) {
        if (request.localLlmModel() != null && !request.localLlmModel().isBlank()) {
            return request.localLlmModel().trim();
        }
        return VisionGroundingConfig.assertModel();
    }

    private static String firstProp(String key) {
        String p = System.getProperty(key);
        if (p == null || p.isBlank()) {
            p = utils.PropertyReader.getProperty(key);
        }
        return p;
    }
}
