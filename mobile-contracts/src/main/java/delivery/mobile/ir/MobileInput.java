package delivery.mobile.ir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileInput(
        String kind,
        String key,
        String value,
        Boolean sensitive
) {
    public MobileInput {
        if (kind == null || kind.isBlank()) {
            throw new MobileIrException("INPUT_KIND", "input.kind is required");
        }
        String k = kind.trim().toUpperCase();
        if (!(k.equals("LITERAL") || k.equals("DATA_REF") || k.equals("SECRET_REF"))) {
            throw new MobileIrException("INPUT_KIND", "input.kind unsupported: " + kind);
        }
        kind = k;
        if (k.equals("LITERAL")) {
            if (Boolean.TRUE.equals(sensitive)) {
                throw new MobileIrException("INPUT_LITERAL_SENSITIVE",
                        "LITERAL forbidden for sensitive fields; use SECRET_REF");
            }
            if (value == null) {
                throw new MobileIrException("INPUT_VALUE", "LITERAL input.value is required");
            }
        } else if (key == null || key.isBlank()) {
            throw new MobileIrException("INPUT_KEY", kind + " input.key is required");
        }
    }
}
