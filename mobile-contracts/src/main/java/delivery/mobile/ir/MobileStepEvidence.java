package delivery.mobile.ir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileStepEvidence(
        String snapshotId,
        List<String> artifactRefs
) {
    public MobileStepEvidence {
        if (artifactRefs == null) {
            artifactRefs = List.of();
        } else {
            artifactRefs = List.copyOf(artifactRefs);
        }
    }
}
