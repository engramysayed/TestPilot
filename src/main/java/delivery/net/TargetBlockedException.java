package delivery.net;

public final class TargetBlockedException extends IllegalStateException {
    private final String blockedUrl;

    public TargetBlockedException(String reason, String blockedUrl) {
        super(reason == null ? "blocked destination" : reason);
        this.blockedUrl = blockedUrl == null ? "" : blockedUrl;
    }

    public String blockedUrl() {
        return blockedUrl;
    }
}
