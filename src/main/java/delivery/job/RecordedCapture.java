package delivery.job;

/** One action remembered during recording, with the locator taken from that Chrome page. */
public record RecordedCapture(
        String kind,
        String label,
        String value,
        boolean secret,
        String strategy,
        String locator,
        String pageUrl
) {
    public RecordedCapture {
        kind = kind == null ? "" : kind;
        label = label == null ? "" : label;
        value = value == null ? "" : value;
        strategy = strategy == null ? "" : strategy;
        locator = locator == null ? "" : locator;
        pageUrl = pageUrl == null ? "" : pageUrl;
    }
}
