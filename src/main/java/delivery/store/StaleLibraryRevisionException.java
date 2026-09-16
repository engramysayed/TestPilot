package delivery.store;

public final class StaleLibraryRevisionException extends IllegalStateException {
    private final String baseRevisionId;
    private final String headRevisionId;

    public StaleLibraryRevisionException(String baseRevisionId, String headRevisionId) {
        super("library revision conflict: base=" + baseRevisionId + " head=" + headRevisionId);
        this.baseRevisionId = baseRevisionId == null ? "" : baseRevisionId;
        this.headRevisionId = headRevisionId == null ? "" : headRevisionId;
    }

    public String baseRevisionId() {
        return baseRevisionId;
    }

    public String headRevisionId() {
        return headRevisionId;
    }
}
