package delivery.authoring;

public record LocatorCandidate(
        String strategy,
        String value,
        String pageName,
        String rationale
) {
}
