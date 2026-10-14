package delivery.portal.preflight;

/** Reachability probe for a project's website. Replaceable in tests. */
public interface PreflightProbe {

    record Result(Boolean reachable, Integer httpStatus, Long latencyMs, String message) {
    }

    Result probe(String baseUrl);
}
