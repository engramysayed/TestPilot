package delivery.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ArtifactManifest {
    public record Entry(int version, String sha256, String jobId, String kind, boolean expired) {
    }

    public record Availability(boolean gone, String reason) {
        public static Availability ok() {
            return new Availability(false, "available");
        }
    }

    private final String projectId;
    private final int latestVersion;
    private final List<Entry> artifacts;

    public ArtifactManifest(String projectId, int latestVersion, List<Entry> artifacts) {
        this.projectId = projectId == null ? "" : projectId;
        this.latestVersion = latestVersion;
        this.artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public String projectId() {
        return projectId;
    }

    public int latestVersion() {
        return latestVersion;
    }

    public List<Entry> artifacts() {
        return artifacts;
    }

    public Optional<Entry> entry(int version) {
        return artifacts.stream().filter(e -> e.version() == version).findFirst();
    }

    public ArtifactManifest withPublished(Entry newest) {
        List<Entry> next = new ArrayList<>();
        for (Entry e : artifacts) {
            next.add(new Entry(e.version(), e.sha256(), e.jobId(), e.kind(), true));
        }
        next.add(newest);
        return new ArtifactManifest(projectId, newest.version(), List.copyOf(next));
    }

    public static Availability availability(ArtifactManifest manifest, int version) {
        if (manifest == null) {
            return new Availability(true, "missing manifest");
        }
        Optional<Entry> found = manifest.entry(version);
        if (found.isEmpty()) {
            return new Availability(true, "unknown artifact version");
        }
        if (found.get().expired()) {
            return new Availability(true, "artifact expired");
        }
        return Availability.ok();
    }
}
