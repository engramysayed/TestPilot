package delivery.portal.model;

public record PatchProjectRequest(String name, String baseUrl, Boolean archived, String preferredHooks) {
    public PatchProjectRequest(String name, String baseUrl, Boolean archived) {
        this(name, baseUrl, archived, null);
    }
}
