package delivery.privacy;

public final class ProviderDisallowedException extends IllegalStateException {
    public ProviderDisallowedException(String reason) {
        super(reason == null ? "provider disallowed" : reason);
    }
}
