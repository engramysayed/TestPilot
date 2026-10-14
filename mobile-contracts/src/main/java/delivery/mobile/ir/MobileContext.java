package delivery.mobile.ir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileContext(
        String kind,
        String packageName,
        String webViewUrlMatcher,
        String webViewTitleMatcher
) {
    public MobileContext {
        if (kind == null || kind.isBlank()) {
            throw new MobileIrException("CONTEXT_KIND", "context.kind is required");
        }
        String k = kind.trim().toUpperCase();
        if (!k.equals("NATIVE") && !k.equals("WEBVIEW")) {
            throw new MobileIrException("CONTEXT_KIND", "context.kind must be NATIVE or WEBVIEW");
        }
        kind = k;
    }

    /** Jackson alias: JSON field "package" maps via @JsonProperty if needed — see codec. */
    public String pkg() {
        return packageName;
    }
}
