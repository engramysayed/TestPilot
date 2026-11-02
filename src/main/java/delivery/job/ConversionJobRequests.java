package delivery.job;

/**
 * Small withers for {@link ConversionJobRequest} without exploding call sites.
 */
public final class ConversionJobRequests {
    private ConversionJobRequests() {
    }

    public static ConversionJobRequest withBaseUrl(ConversionJobRequest request, String baseUrl) {
        if (request == null) {
            return null;
        }
        if (baseUrl == null || baseUrl.equals(request.baseUrl())) {
            return request;
        }
        return new ConversionJobRequest(
                request.projectId(),
                request.excel(),
                baseUrl,
                request.username(),
                request.password(),
                request.workDir(),
                request.storeRoot(),
                request.templateRoot(),
                request.mode(),
                request.localLlmBaseUrl(),
                request.localLlmModel(),
                request.finalRevise(),
                request.codegenOllamaNaming(),
                request.authoringEngine(),
                request.precisionConfig(),
                request.tenantId(),
                request.jobId(),
                request.tenantScope()
        );
    }
}
