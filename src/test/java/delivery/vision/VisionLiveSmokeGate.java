package delivery.vision;

import org.testng.SkipException;

/**
 * AE: vision LiveSmoke is opt-in so default {@code mvn test} does not hang (~3–4 min) when Ollama
 * answers {@code /api/tags} but inference is slow/unavailable. Production vision timeouts unchanged.
 * <p>Run overnight / operator: {@code KEEL_LIVESMOKE=1} or {@code -Dkeel.vision.liveSmoke=true}.
 */
final class VisionLiveSmokeGate {
    private VisionLiveSmokeGate() {
    }

    static void assumeOptIn() {
        if (!optIn()) {
            throw new SkipException(
                    "Vision live smoke skipped (set KEEL_LIVESMOKE=1 or -Dkeel.vision.liveSmoke=true)");
        }
    }

    static boolean optIn() {
        String env = System.getenv("KEEL_LIVESMOKE");
        if ("1".equals(env) || (env != null && "true".equalsIgnoreCase(env.trim()))) {
            return true;
        }
        String prop = System.getProperty("keel.vision.liveSmoke", "").trim();
        return "1".equals(prop) || "true".equalsIgnoreCase(prop);
    }
}
