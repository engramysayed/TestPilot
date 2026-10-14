package delivery.mobile.ir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileTarget(
        String strategy,
        String value,
        String origin,
        Integer expectedMatches
) {
    public MobileTarget {
        if (strategy == null || strategy.isBlank()) {
            throw new MobileIrException("TARGET_STRATEGY", "target.strategy is required");
        }
        if (value == null) {
            throw new MobileIrException("TARGET_VALUE", "target.value is required");
        }
        if (origin == null || origin.isBlank()) {
            throw new MobileIrException("TARGET_ORIGIN", "target.origin is required");
        }
        String o = origin.trim().toUpperCase();
        if (!(o.equals("USER") || o.equals("HIERARCHY") || o.equals("PRECISION") || o.equals("MEMORY"))) {
            throw new MobileIrException("TARGET_ORIGIN", "target.origin unsupported: " + origin);
        }
        origin = o;
        if (expectedMatches != null && expectedMatches < 1) {
            throw new MobileIrException("TARGET_MATCHES", "expectedMatches must be >= 1");
        }
    }
}
