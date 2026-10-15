package delivery.portal.record;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Page hook that writes clicks and typing onto {@code window.__keelRec}. */
public final class RecordingScript {
    public static final String READ = """
            return (function () {
              var rec = window.__keelRec;
              if (!rec || !rec.steps) return '';
              try {
                return JSON.stringify({ gen: rec.gen || '', steps: rec.steps });
              } catch (e) {
                return '';
              }
            })();
            """;

    private static volatile String install;

    private RecordingScript() {
    }

    public static String install() {
        String cached = install;
        if (cached != null) {
            return cached;
        }
        synchronized (RecordingScript.class) {
            if (install == null) {
                install = read("/keel/record-hook.js");
            }
            return install;
        }
    }

    private static String read(String path) {
        try (InputStream in = RecordingScript.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Recording script is missing.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Recording script is missing.");
        }
    }
}
