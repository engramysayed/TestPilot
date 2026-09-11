package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.portal.model.JobRecord;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Durable per-job authoring options (automate/execute request.json). */
public final class AuthoringJobRequestFiles {
    private AuthoringJobRequestFiles() {
    }

    public static Path requestPath(Path projectRoot, JobRecord.JobKind kind, String jobId) {
        String subdir = kind == JobRecord.JobKind.EXECUTE ? "execute-runs" : "automate-runs";
        return projectRoot.resolve(subdir).resolve(jobId).resolve("request.json");
    }

    public static void write(Path requestPath, AuthoringEngine engine) throws IOException {
        Files.createDirectories(requestPath.getParent());
        JSONObject body = new JSONObject();
        body.put("authoringEngine", engine.wireValue());
        Files.writeString(requestPath, body.toString(2));
    }

    public static AuthoringEngine read(Path requestPath) {
        if (requestPath == null || !Files.isRegularFile(requestPath)) {
            return AuthoringEngine.KEEL;
        }
        try {
            String raw = Files.readString(requestPath);
            if (raw == null || raw.isBlank()) {
                return AuthoringEngine.KEEL;
            }
            JSONObject body = new JSONObject(raw);
            return AuthoringEngine.parse(body.optString("authoringEngine", "keel"));
        } catch (Exception ignored) {
            return AuthoringEngine.KEEL;
        }
    }
}
