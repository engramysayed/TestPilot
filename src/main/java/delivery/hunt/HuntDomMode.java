package delivery.hunt;

import java.util.Locale;

public enum HuntDomMode {
    MAP,
    SLIM,
    AUTO;

    public static HuntDomMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "map" -> MAP;
            case "slim" -> SLIM;
            case "auto" -> AUTO;
            default -> AUTO;
        };
    }

    public static boolean shouldIncludeSlim(HuntDomMode mode, HuntPageMap map) {
        return mode == SLIM
                || (mode == AUTO && (map == null || map.isThin()));
    }
}
