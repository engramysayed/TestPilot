package delivery.authoring;

/**
 * A locator that exists on the live page. LLM may only pick among these.
 */
public record DomCandidate(
        String id,
        String strategy,
        String value,
        String tag,
        String label
) {
    public String key() {
        return strategy + ":" + value;
    }
}
