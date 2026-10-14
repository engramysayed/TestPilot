package delivery.mobile.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory immutable auth profile revision store (M3-01).
 * Active revision is pinned by id; later edits add revisions without mutating prior ones.
 */
public final class AuthProfileStore {
    private final Map<String, AuthProfileRevision> byRevision = new LinkedHashMap<>();
    private final Map<String, String> activeByProfile = new LinkedHashMap<>();
    private final Map<String, List<String>> historyByProfile = new LinkedHashMap<>();

    public synchronized AuthProfileRevision publish(AuthProfileRevision revision) {
        Objects.requireNonNull(revision, "revision");
        if (byRevision.containsKey(revision.revisionId())) {
            throw new AuthContractException("REVISION_EXISTS", "revision already published: " + revision.revisionId());
        }
        byRevision.put(revision.revisionId(), revision);
        historyByProfile.computeIfAbsent(revision.profileId(), k -> new ArrayList<>()).add(revision.revisionId());
        activeByProfile.putIfAbsent(revision.profileId(), revision.revisionId());
        return revision;
    }

    /**
     * Publish an edit as a new revision and advance active pointer.
     * {@code expectedActiveRevisionId} must match current active or a {@code REVISION_CONFLICT} is raised.
     */
    public synchronized AuthProfileRevision publishEdit(
            String expectedActiveRevisionId, AuthProfileRevision newRevision) {
        Objects.requireNonNull(newRevision, "newRevision");
        String active = activeByProfile.get(newRevision.profileId());
        if (active == null) {
            throw new AuthContractException("PROFILE_MISSING", "unknown profile: " + newRevision.profileId());
        }
        if (!active.equals(expectedActiveRevisionId)) {
            throw new AuthContractException(
                    "REVISION_CONFLICT",
                    "expected active " + expectedActiveRevisionId + " but was " + active);
        }
        if (byRevision.containsKey(newRevision.revisionId())) {
            throw new AuthContractException("REVISION_EXISTS", "revision already published: " + newRevision.revisionId());
        }
        if (!newRevision.profileId().equals(byRevision.get(active).profileId())) {
            throw new AuthContractException("PROFILE_MISMATCH", "profile id mismatch on edit");
        }
        byRevision.put(newRevision.revisionId(), newRevision);
        historyByProfile.get(newRevision.profileId()).add(newRevision.revisionId());
        activeByProfile.put(newRevision.profileId(), newRevision.revisionId());
        return newRevision;
    }

    public synchronized Optional<AuthProfileRevision> getRevision(String revisionId) {
        return Optional.ofNullable(byRevision.get(revisionId));
    }

    public synchronized Optional<AuthProfileRevision> getActive(String profileId) {
        String rev = activeByProfile.get(profileId);
        return rev == null ? Optional.empty() : Optional.ofNullable(byRevision.get(rev));
    }

    public synchronized String activeRevisionId(String profileId) {
        return activeByProfile.get(profileId);
    }

    public synchronized List<String> history(String profileId) {
        List<String> h = historyByProfile.get(profileId);
        return h == null ? List.of() : List.copyOf(h);
    }
}
