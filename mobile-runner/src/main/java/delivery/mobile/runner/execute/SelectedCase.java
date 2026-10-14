package delivery.mobile.runner.execute;

public record SelectedCase(String tcId, boolean simulated) {
    public SelectedCase {
        if (tcId == null || tcId.isBlank()) throw new IllegalArgumentException("tcId required");
    }
}
