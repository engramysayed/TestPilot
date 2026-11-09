package delivery.portal.cli;

import delivery.portal.tunnel.TunnelAgentExecutor;
import delivery.portal.tunnel.TunnelLocalProxy;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A2 portal CLI entry point: {@code java ... KeelPortalCli doctor|status|suites list|run|failure get}.
 * Auth: {@code KEEL_API_KEY} or {@code KEEL_USER}/{@code KEEL_PASSWORD}; base {@code KEEL_PORTAL_URL}.
 */
public final class KeelPortalCli {

    public static void main(String[] args) {
        try {
            int code = run(args);
            System.exit(code);
        } catch (Exception e) {
            System.err.println(new JSONObject()
                    .put("error", "CLI_ERROR")
                    .put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            System.exit(4);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return 2;
        }
        String base = env("KEEL_PORTAL_URL", "http://127.0.0.1:8080");
        String user = env("KEEL_USER", "admin@testpilot.local");
        String pass = env("KEEL_PASSWORD", "ChangeMeAdmin1!");
        String apiKey = env("KEEL_API_KEY", "");
        KeelPortalHttpClient client = new KeelPortalHttpClient(base, user, pass, apiKey);
        String cmd = args[0];
        if ("doctor".equals(cmd)) {
            var res = client.get("/api/health");
            JSONObject out = new JSONObject();
            out.put("portalUrl", base);
            out.put("status", res.status());
            out.put("ok", res.status() >= 200 && res.status() < 300);
            System.out.println(out);
            return out.getBoolean("ok") ? 0 : 1;
        }
        if ("status".equals(cmd) && args.length >= 2 && "auth".equals(args[1])) {
            var res = client.get("/api/projects");
            JSONObject out = new JSONObject();
            out.put("authenticated", res.status() == 200);
            out.put("httpStatus", res.status());
            System.out.println(out);
            return res.status() == 200 ? 0 : 1;
        }
        if ("suites".equals(cmd) && args.length >= 3 && "list".equals(args[1])) {
            String projectId = arg(args, "--project", 2);
            if (projectId == null) {
                throw new IllegalArgumentException("--project required");
            }
            var res = client.get("/api/projects/" + projectId + "/suites");
            System.out.println(new String(res.body(), java.nio.charset.StandardCharsets.UTF_8));
            return res.status() == 200 ? 0 : 1;
        }
        if ("run".equals(cmd)) {
            String projectId = flag(args, "--project");
            String suiteId = flag(args, "--suite");
            if (projectId == null || suiteId == null) {
                throw new IllegalArgumentException("--project and --suite required");
            }
            JSONObject body = new JSONObject().put("trigger", "manual");
            var res = client.post("/api/projects/" + projectId + "/suites/" + suiteId + "/runs", body.toString());
            System.out.println(res.json());
            if (res.status() == 402) return 12;
            if (res.status() == 202 || res.status() == 200) return 0;
            return 1;
        }
        if ("tunnel".equals(cmd) && args.length >= 2 && "enroll".equals(args[1])) {
            String projectId = flag(args, "--project");
            if (projectId == null) {
                throw new IllegalArgumentException("--project required");
            }
            var res = client.post("/api/projects/" + projectId + "/tunnel/enroll", "{}");
            System.out.println(res.json());
            return res.status() == 200 ? 0 : 1;
        }
        if ("tunnel".equals(cmd) && args.length >= 2 && "status".equals(args[1])) {
            String projectId = flag(args, "--project");
            if (projectId == null) {
                throw new IllegalArgumentException("--project required");
            }
            var res = client.get("/api/projects/" + projectId + "/tunnel/badge");
            System.out.println(res.json());
            return res.status() == 200 ? 0 : 1;
        }
        if ("tunnel".equals(cmd) && args.length >= 2 && "start".equals(args[1])) {
            String projectId = flag(args, "--project");
            String target = flag(args, "--target");
            if (projectId == null || target == null) {
                throw new IllegalArgumentException("--project and --target required");
            }
            int listen = parseIntFlag(args, "--listen", 4040);
            boolean reEnroll = hasFlag(args, "--re-enroll");
            Path credFile = TunnelCredentialCache.file(base, projectId);
            JSONObject enrollBody = new JSONObject();
            if (!reEnroll) {
                TunnelCredentialCache.load(credFile).ifPresent(c -> enrollBody
                        .put("reuseToken", c.optString("token", ""))
                        .put("agentSecret", c.optString("agentSecret", "")));
            }
            var enroll = client.post("/api/projects/" + projectId + "/tunnel/enroll", enrollBody.toString());
            if (enroll.status() != 200) {
                System.out.println(enroll.json());
                return 1;
            }
            String token = enroll.json().optString("token", "");
            String agentSecret = enroll.json().optString("agentSecret", "");
            if (token.isBlank() || agentSecret.isBlank()) {
                System.out.println(enroll.json());
                return 1;
            }
            boolean reused = enroll.json().optBoolean("reused", false);
            if (!reused) {
                TunnelCredentialCache.save(credFile, projectId, base, token, agentSecret);
            }
            try (TunnelLocalProxy proxy = new TunnelLocalProxy(listen, target)) {
                proxy.start();
                int port = proxy.port();
                String listenUrl = "http://127.0.0.1:" + port;
                System.out.println(new JSONObject()
                        .put("listenUrl", listenUrl)
                        .put("target", target)
                        .put("token", token)
                        .put("reusedEnrollment", reused)
                        .put("credentialsFile", credFile.toString())
                        .put("relay", "POST " + base + "/api/tunnel/agent/poll"));
                client.post("/api/projects/" + projectId + "/tunnel/heartbeat",
                        new JSONObject().put("token", token).put("listenUrl", listenUrl).toString());
                Thread heartbeat = new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            client.post("/api/projects/" + projectId + "/tunnel/heartbeat",
                                    new JSONObject().put("token", token).put("listenUrl", listenUrl).toString());
                            Thread.sleep(15_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception ignored) {
                        }
                    }
                }, "keel-tunnel-heartbeat");
                heartbeat.setDaemon(true);
                heartbeat.start();
                String agentAuth;
                if (!apiKey.isBlank()) {
                    agentAuth = "Bearer " + apiKey.trim();
                } else {
                    String pair = user + ":" + pass;
                    agentAuth = "Basic " + java.util.Base64.getEncoder()
                            .encodeToString(pair.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                new TunnelAgentExecutor(base, agentAuth).runLoop(projectId, token, agentSecret, listenUrl);
            }
            return 0;
        }
        if ("failure".equals(cmd) && args.length >= 2 && "get".equals(args[1])) {
            String jobId = arg(args, "--job", 2);
            String projectId = flag(args, "--project");
            String outDir = flag(args, "--out");
            if (jobId == null || projectId == null || outDir == null) {
                throw new IllegalArgumentException("--project --job and --out required");
            }
            var res = client.get("/api/projects/" + projectId + "/jobs/" + jobId + "/failure-pack");
            if (res.status() != 200) {
                System.out.println(res.json());
                return 1;
            }
            Path dir = Path.of(outDir);
            Files.createDirectories(dir);
            Path zip = dir.resolve("failure-pack-" + jobId + ".zip");
            Files.write(zip, res.body());
            System.out.println(new JSONObject()
                    .put("jobId", jobId)
                    .put("outputFile", zip.toAbsolutePath().toString())
                    .put("partial", false));
            return 0;
        }
        printHelp();
        return 2;
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String flag(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String name) {
        for (String a : args) {
            if (name.equals(a)) return true;
        }
        return false;
    }

    private static int parseIntFlag(String[] args, String name, int def) {
        String v = flag(args, name);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String arg(String[] args, String name, int from) {
        for (int i = from; i < args.length - 1; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }
        return null;
    }

    private static void printHelp() {
        System.out.println("""
                Keel portal CLI: doctor | status auth | suites list --project <id> \\
                | run --project <id> --suite <id> | failure get --project <id> --job <id> --out <dir> \\
                | tunnel enroll --project <id> | tunnel status --project <id> \\
                | tunnel start --project <id> --target <url> [--listen 4040] [--re-enroll]
                Env: KEEL_PORTAL_URL, KEEL_API_KEY or KEEL_USER/KEEL_PASSWORD, KEEL_HOME (default ~/.keel)
                tunnel start reuses the enrollment cached in KEEL_HOME/tunnel/ so the relay URL stays stable;
                pass --re-enroll to mint a new token (invalidates the old one).
                """);
    }
}
