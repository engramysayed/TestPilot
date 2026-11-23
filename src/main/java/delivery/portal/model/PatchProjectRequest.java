package delivery.portal.model;

public record PatchProjectRequest(
        String name,
        String baseUrl,
        Boolean archived,
        String preferredHooks,
        String authoringEngine,
        Integer precisionMaxCallsPerJob,
        Boolean precisionReuseLocatorMemory,
        String testDataEnv,
        /** {@code stable-first} (default) or {@code user-facing-first}; see delivery.locator.LocatorMode. */
        String locatorMode,
        /** Cursor model id; "auto" or blank clears the per-project pin and uses the workspace default. */
        String precisionCursorModel
) {
    public PatchProjectRequest(String name, String baseUrl, Boolean archived) {
        this(name, baseUrl, archived, null, null, null, null, null, null, null);
    }

    public PatchProjectRequest(String name, String baseUrl, Boolean archived, String preferredHooks) {
        this(name, baseUrl, archived, preferredHooks, null, null, null, null, null, null);
    }
}
