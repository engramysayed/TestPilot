package delivery.mobile.ir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileCaseIr(
        String schemaVersion,
        MobilePlatform platform,
        String tcId,
        String libraryRevisionId,
        String appRevisionId,
        String caseSettingsRevisionId,
        String namingRevisionId,
        MobileProof proof,
        List<String> setupRefs,
        List<MobileStep> steps
) {
    public static final String SCHEMA_V1 = "mobile-ir/1";

    public MobileCaseIr {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new MobileIrException("SCHEMA_REQUIRED", "schemaVersion is required");
        }
        if (!SCHEMA_V1.equals(schemaVersion)) {
            throw new MobileIrException("SCHEMA_UNSUPPORTED",
                    "unsupported schemaVersion: " + schemaVersion + " (reader is " + SCHEMA_V1 + ")");
        }
        if (platform == null) {
            throw new MobileIrException("PLATFORM_REQUIRED", "platform is required");
        }
        if (platform != MobilePlatform.ANDROID) {
            throw new MobileIrException("PLATFORM_UNSUPPORTED", "only ANDROID supported in mobile IR");
        }
        require(tcId, "tcId");
        require(libraryRevisionId, "libraryRevisionId");
        require(appRevisionId, "appRevisionId");
        require(caseSettingsRevisionId, "caseSettingsRevisionId");
        require(namingRevisionId, "namingRevisionId");
        if (proof == null) {
            throw new MobileIrException("PROOF_REQUIRED", "proof is required");
        }
        if (setupRefs == null) {
            setupRefs = List.of();
        } else {
            setupRefs = List.copyOf(setupRefs);
        }
        if (steps == null) {
            throw new MobileIrException("STEPS_REQUIRED", "steps is required");
        }
        steps = List.copyOf(steps);
    }

    private static void require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new MobileIrException("FIELD_REQUIRED", name + " is required");
        }
    }
}
