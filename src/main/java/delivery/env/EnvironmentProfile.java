package delivery.env;

import java.util.Objects;

/**
 * Named execution environment. Identity is the immutable revision id, not the project folder.
 */
public record EnvironmentProfile(
        String name,
        String origin,
        String credentialProfileRef,
        String providerAllowlist,
        int maxQueued,
        int maxRunning
) {
    public EnvironmentProfile {
        name = name == null ? "" : name.trim();
        origin = origin == null ? "" : origin.trim();
        credentialProfileRef = credentialProfileRef == null ? "" : credentialProfileRef.trim();
        providerAllowlist = providerAllowlist == null ? "" : providerAllowlist.trim();
        maxQueued = Math.max(0, maxQueued);
        maxRunning = Math.max(0, maxRunning);
    }

    public org.json.JSONObject toJson() {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("name", name);
        o.put("origin", origin);
        o.put("credentialProfileRef", credentialProfileRef);
        o.put("providerAllowlist", providerAllowlist);
        o.put("maxQueued", maxQueued);
        o.put("maxRunning", maxRunning);
        return o;
    }

    public static EnvironmentProfile fromJson(org.json.JSONObject o) {
        if (o == null) {
            return new EnvironmentProfile("", "", "", "", 0, 0);
        }
        return new EnvironmentProfile(
                o.optString("name"),
                o.optString("origin"),
                o.optString("credentialProfileRef"),
                o.optString("providerAllowlist"),
                o.optInt("maxQueued"),
                o.optInt("maxRunning"));
    }

    public boolean sameStorageIdentity(EnvironmentProfile other) {
        return other != null && Objects.equals(name, other.name);
    }
}
