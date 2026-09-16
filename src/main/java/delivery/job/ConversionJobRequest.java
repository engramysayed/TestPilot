package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;

import java.nio.file.Path;

public record ConversionJobRequest(
        String projectId,
        Path excel,
        String baseUrl,
        String username,
        String password,
        Path workDir,
        Path storeRoot,
        Path templateRoot,
        String mode,
        String localLlmBaseUrl,
        String localLlmModel,
        /** Portal "Client delivery — final revise" checkbox. */
        boolean finalRevise,
        /** Optional Ollama polish for generated test method names only. */
        boolean codegenOllamaNaming,
        /** Prove/heal engine for this job; defaults to Keel when unset. */
        AuthoringEngine authoringEngine,
        /** Server + job Precision settings (cap, feature flag). */
        PrecisionJobConfig precisionConfig,
        /** Immutable workspace; null is allowed only for {@link TenantScope#LEGACY_EXPLICIT}. */
        delivery.identity.TenantId tenantId,
        /** Immutable job id used for exclusive work directories. */
        String jobId,
        /** Hosted jobs fail closed; legacy directories require an explicit opt-in. */
        TenantScope tenantScope
) {
    public ConversionJobRequest {
        if (tenantScope == null) {
            tenantScope = TenantScope.LEGACY_EXPLICIT;
        }
    }

    /** Hosted portal request: missing tenant/job must fail closed. */
    public ConversionJobRequest(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String localLlmBaseUrl,
            String localLlmModel,
            boolean finalRevise,
            boolean codegenOllamaNaming,
            AuthoringEngine authoringEngine,
            PrecisionJobConfig precisionConfig,
            delivery.identity.TenantId tenantId,
            String jobId
    ) {
        this(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot,
                mode, localLlmBaseUrl, localLlmModel, finalRevise, codegenOllamaNaming,
                authoringEngine, precisionConfig, tenantId, jobId, TenantScope.LEGACY_EXPLICIT);
    }

    /** Back-compat for callers without tenant/job identity. */
    public ConversionJobRequest(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String localLlmBaseUrl,
            String localLlmModel,
            boolean finalRevise,
            boolean codegenOllamaNaming,
            AuthoringEngine authoringEngine,
            PrecisionJobConfig precisionConfig
    ) {
        this(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot,
                mode, localLlmBaseUrl, localLlmModel, finalRevise, codegenOllamaNaming,
                authoringEngine, precisionConfig, null, null, TenantScope.LEGACY_EXPLICIT);
    }
    /** Back-compat for callers without final-revise flag. */
    public ConversionJobRequest(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String localLlmBaseUrl,
            String localLlmModel
    ) {
        this(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot,
                mode, localLlmBaseUrl, localLlmModel, false, false, AuthoringEngine.KEEL,
                PrecisionJobConfig.DEFAULTS);
    }

    /** Back-compat for callers without codegen Ollama naming flag. */
    public ConversionJobRequest(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String localLlmBaseUrl,
            String localLlmModel,
            boolean finalRevise
    ) {
        this(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot,
                mode, localLlmBaseUrl, localLlmModel, finalRevise, false, AuthoringEngine.KEEL,
                PrecisionJobConfig.DEFAULTS);
    }

    /** Back-compat for callers without precision config. */
    public ConversionJobRequest(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String localLlmBaseUrl,
            String localLlmModel,
            boolean finalRevise,
            boolean codegenOllamaNaming,
            AuthoringEngine authoringEngine
    ) {
        this(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot,
                mode, localLlmBaseUrl, localLlmModel, finalRevise, codegenOllamaNaming,
                authoringEngine, PrecisionJobConfig.DEFAULTS);
    }

    /** Back-compat for callers without authoring engine. */
    public ConversionJobRequest(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String localLlmBaseUrl,
            String localLlmModel,
            boolean finalRevise,
            boolean codegenOllamaNaming
    ) {
        this(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot,
                mode, localLlmBaseUrl, localLlmModel, finalRevise, codegenOllamaNaming,
                AuthoringEngine.KEEL, PrecisionJobConfig.DEFAULTS);
    }
}
