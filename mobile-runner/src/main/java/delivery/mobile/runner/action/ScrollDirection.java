package delivery.mobile.runner.action;

public enum ScrollDirection {
    UP, DOWN, LEFT, RIGHT;

    public static ScrollDirection parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DOWN;
        }
        return ScrollDirection.valueOf(raw.trim().toUpperCase());
    }
}
