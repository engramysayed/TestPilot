package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;
import delivery.excel.CallBefore;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import delivery.ir.TcIdentity;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * UPDATE reuse is allowed only for complete prior live proof.
 * Unchanged TODO/PARTIAL/empty IR must be re-proven and must not count as PASS.
 *
 * <p>Environment fingerprint includes base URL, authoring engine, IR schema,
 * an opaque credential revision (never a password hash), credentials-bound flag,
 * Precision budget, and local LLM URL/model. Codegen Ollama naming is emit-only
 * and does not participate. Credential MAC material stays on the server store.
 */
public final class ReuseEligibility {
    public static final int IR_SCHEMA = 1;
    public static final String CONTEXT_FILE = "prove-context.json";
    public static final String PROVENANCE = "reused prior PASSED proof; not a fresh browser run";

    public record Context(
            String baseUrl,
            String authoringEngine,
            int irSchema,
            String credentialRevision,
            boolean credentialsBound,
            boolean precisionEnabled,
            int precisionMaxCalls,
            String authoringConfigFingerprint
    ) {
        public Context {
            baseUrl = normalizeBaseUrl(baseUrl);
            authoringEngine = authoringEngine == null || authoringEngine.isBlank()
                    ? AuthoringEngine.KEEL.wireValue()
                    : authoringEngine.trim().toLowerCase(Locale.ROOT);
            credentialRevision = credentialRevision == null ? "" : credentialRevision;
            authoringConfigFingerprint = authoringConfigFingerprint == null
                    ? "" : authoringConfigFingerprint;
        }

        /** Test helper: URL + engine + schema with default execution settings. */
        public Context(String baseUrl, String authoringEngine, int irSchema) {
            this(baseUrl, authoringEngine, irSchema, "", false, true, 50, "");
        }
    }

    private ReuseEligibility() {
    }

    public static boolean canReuse(TcDraft prior) {
        if (prior == null || prior.status() == null) {
            return false;
        }
        if (prior.status() != TcDraftStatus.PASSED && prior.status() != TcDraftStatus.REUSED) {
            return false;
        }
        return prior.provenSteps() != null && !prior.provenSteps().isEmpty();
    }

    public static Context current(ConversionJobRequest request) {
        AuthoringEngine engine = request == null || request.authoringEngine() == null
                ? AuthoringEngine.KEEL
                : request.authoringEngine();
        String baseUrl = request == null ? "" : request.baseUrl();
        String username = request == null ? "" : request.username();
        String password = request == null ? "" : request.password();
        boolean bound = (username != null && !username.isBlank())
                || (password != null && !password.isBlank());
        PrecisionJobConfig precision = request == null || request.precisionConfig() == null
                ? PrecisionJobConfig.DEFAULTS
                : request.precisionConfig();
        String llmUrl = request == null ? "" : request.localLlmBaseUrl();
        String llmModel = request == null ? "" : request.localLlmModel();
        return new Context(
                baseUrl,
                engine.wireValue(),
                IR_SCHEMA,
                CredentialRevision.resolve(request),
                bound,
                precision.enabled(),
                precision.maxCallsPerJob(),
                authoringConfigFingerprint(llmUrl, llmModel));
    }

    public static boolean environmentMatches(Context stored, Context current) {
        if (stored == null || current == null) {
            return false;
        }
        if (stored.irSchema() != current.irSchema()) {
            return false;
        }
        return stored.baseUrl().equals(current.baseUrl())
                && stored.authoringEngine().equals(current.authoringEngine())
                && stored.credentialRevision().equals(current.credentialRevision())
                && stored.credentialsBound() == current.credentialsBound()
                && stored.precisionEnabled() == current.precisionEnabled()
                && stored.precisionMaxCalls() == current.precisionMaxCalls()
                && stored.authoringConfigFingerprint().equals(current.authoringConfigFingerprint());
    }

    public static Set<String> authorIds(
            List<ManualTestCase> incoming,
            Map<String, String> storedHashes,
            Map<String, TcDraft> storedDrafts,
            Context storedContext,
            Context currentContext
    ) {
        return authorIds(incoming, storedHashes, storedDrafts, storedContext, currentContext, null);
    }

    public static Set<String> authorIds(
            List<ManualTestCase> incoming,
            Map<String, String> storedHashes,
            Map<String, TcDraft> storedDrafts,
            Context storedContext,
            Context currentContext,
            Path projectRoot
    ) {
        List<ManualTestCase> cases = incoming == null ? List.of() : incoming;
        Map<String, String> hashes = storedHashes == null ? Map.of() : storedHashes;
        Map<String, TcDraft> drafts = storedDrafts == null ? Map.of() : storedDrafts;
        Set<String> author = new HashSet<>();
        if (!environmentMatches(storedContext, currentContext)) {
            for (ManualTestCase tc : cases) {
                author.add(tc.tcId());
            }
            return author;
        }
        for (ManualTestCase tc : cases) {
            String prev = hashes.get(tc.tcId());
            TcDraft stored = drafts.get(tc.tcId());
            if (prev == null || !prev.equals(tc.contentHash())
                    || !canReuse(stored)
                    || !evidenceAvailable(stored, projectRoot)) {
                author.add(tc.tcId());
            }
        }
        boolean changed;
        do {
            changed = false;
            for (ManualTestCase tc : cases) {
                if (author.contains(tc.tcId())) {
                    continue;
                }
                for (String ref : CallBefore.parse(tc.callBefore())) {
                    if (author.contains(ref) || !canReuse(drafts.get(ref))) {
                        author.add(tc.tcId());
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        return author;
    }

    public static TcDraft copyForReuse(TcDraft prior, ManualTestCase incoming) {
        return copyForReuse(prior, incoming, null);
    }

    public static TcDraft copyForReuse(TcDraft prior, ManualTestCase incoming, Path projectRoot) {
        if (prior == null) {
            throw new IllegalArgumentException("prior draft is required");
        }
        String title = incoming == null || incoming.title() == null || incoming.title().isBlank()
                ? prior.title()
                : incoming.title();
        String evidence = projectRoot == null
                ? prior.evidenceDir()
                : durableEvidenceDir(projectRoot, prior.tcId()).toString();
        return new TcDraft(
                prior.tcId(),
                title,
                prior.stepsText(),
                prior.expectedResult(),
                TcDraftStatus.REUSED,
                prior.provenSteps(),
                prior.loginSteps(),
                prior.needsLoginBeforeMethod(),
                prior.blockerStepIndex(),
                prior.blockerIntent(),
                PROVENANCE,
                evidence,
                prior.retryCountOnBlocker(),
                prior.lastPageUrl(),
                prior.healTier(),
                prior.healSkipReason(),
                prior.loginFormUrl(),
                prior.jobAuthoringEngine(),
                prior.precisionCallsUsed(),
                prior.precisionFallback(),
                prior.precisionFallbackReason());
    }

    public static Path durableEvidenceDir(Path projectRoot, String tcId) {
        Path root = projectRoot == null ? Path.of("evidence") : projectRoot.resolve("evidence");
        return root.resolve(TcIdentity.storageKey(tcId == null ? "tc" : tcId));
    }

    public static boolean evidenceAvailable(TcDraft prior, Path projectRoot) {
        if (prior == null) {
            return false;
        }
        if (prior.evidenceDir() == null || prior.evidenceDir().isBlank()) {
            return true;
        }
        if (projectRoot == null) {
            return true;
        }
        if (hasFiles(durableEvidenceDir(projectRoot, prior.tcId()))) {
            return true;
        }
        Path legacy = projectRoot.resolve("evidence")
                .resolve(TcDraftStore.safeFileName(prior.tcId() == null ? "tc" : prior.tcId()));
        if (hasFiles(legacy)) {
            return true;
        }
        if (latestOccurrenceDir(projectRoot.resolve("evidence"), prior.tcId()) != null) {
            return true;
        }
        try {
            return hasFiles(Path.of(prior.evidenceDir()));
        } catch (Exception e) {
            return false;
        }
    }

    public static void preserveEvidence(TcDraft prior, Path projectRoot, Path workDir) throws Exception {
        if (prior == null || projectRoot == null) {
            return;
        }
        Path source = null;
        Path claimed = (prior.evidenceDir() == null || prior.evidenceDir().isBlank())
                ? null : Path.of(prior.evidenceDir());
        Path durable = durableEvidenceDir(projectRoot, prior.tcId());
        Path legacyDurable = projectRoot.resolve("evidence")
                .resolve(TcDraftStore.safeFileName(prior.tcId() == null ? "tc" : prior.tcId()));
        if (claimed != null && hasFiles(claimed)) {
            source = claimed;
        } else if (hasFiles(durable)) {
            source = durable;
        } else if (hasFiles(legacyDurable)) {
            source = legacyDurable;
        } else {
            source = latestOccurrenceDir(projectRoot.resolve("evidence"), prior.tcId());
        }
        if (source == null) {
            return;
        }
        copyTree(source, durable);
        if (workDir != null) {
            copyTree(source, workDir.resolve("evidence").resolve(TcIdentity.storageKey(prior.tcId())));
        }
    }

    public static Map<String, TcDraft> loadStoredDrafts(Path projectRoot) {
        if (projectRoot == null) {
            return Map.of();
        }
        try {
            Map<String, TcDraft> out = new HashMap<>();
            for (TcDraft draft : new TcDraftStore(projectRoot).readAll()) {
                if (draft != null && draft.tcId() != null && !draft.tcId().isBlank()) {
                    out.put(draft.tcId(), draft);
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static Context read(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        Path file = projectRoot.resolve(CONTEXT_FILE);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            return new Context(
                    json.optString("baseUrl", ""),
                    json.optString("authoringEngine", AuthoringEngine.KEEL.wireValue()),
                    json.optInt("irSchema", 0),
                    json.optString("credentialRevision", ""),
                    json.optBoolean("credentialsBound", false),
                    json.optBoolean("precisionEnabled", true),
                    json.optInt("precisionMaxCalls", PrecisionJobConfig.DEFAULTS.maxCallsPerJob()),
                    json.optString("authoringConfigFingerprint", ""));
        } catch (Exception e) {
            return new Context("", "", -1);
        }
    }

    public static void write(Path projectRoot, Context context) throws Exception {
        if (projectRoot == null || context == null) {
            return;
        }
        Files.createDirectories(projectRoot);
        JSONObject json = new JSONObject();
        json.put("baseUrl", context.baseUrl());
        json.put("authoringEngine", context.authoringEngine());
        json.put("irSchema", context.irSchema());
        json.put("credentialRevision", context.credentialRevision());
        json.put("credentialsBound", context.credentialsBound());
        json.put("precisionEnabled", context.precisionEnabled());
        json.put("precisionMaxCalls", context.precisionMaxCalls());
        json.put("authoringConfigFingerprint", context.authoringConfigFingerprint());
        Files.writeString(projectRoot.resolve(CONTEXT_FILE), json.toString(2), StandardCharsets.UTF_8);
    }

    static String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    static String authoringConfigFingerprint(String localLlmBaseUrl, String localLlmModel) {
        String url = localLlmBaseUrl == null ? "" : normalizeBaseUrl(localLlmBaseUrl);
        String model = localLlmModel == null ? "" : localLlmModel.trim().toLowerCase(Locale.ROOT);
        if (url.isBlank() && model.isBlank()) {
            return "";
        }
        return sha256Hex(url + "\0" + model);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    static Path latestOccurrenceDir(Path evidenceRoot, String tcId) {
        if (evidenceRoot == null || !Files.isDirectory(evidenceRoot)) {
            return null;
        }
        String encodedPrefix = TcIdentity.storageKey(tcId == null ? "tc" : tcId) + "__occ_";
        String legacyPrefix = TcDraftStore.safeFileName(tcId == null ? "tc" : tcId) + "__occ_";
        Path best = null;
        int bestN = -1;
        try (var stream = Files.list(evidenceRoot)) {
            for (Path dir : stream.toList()) {
                if (!Files.isDirectory(dir) || !hasFiles(dir)) {
                    continue;
                }
                String name = dir.getFileName().toString();
                if (!name.startsWith(encodedPrefix) && !name.startsWith(legacyPrefix)) {
                    continue;
                }
                String digits = name.startsWith(encodedPrefix)
                        ? name.substring(encodedPrefix.length())
                        : name.substring(legacyPrefix.length());
                try {
                    int n = Integer.parseInt(digits);
                    if (n >= bestN) {
                        bestN = n;
                        best = dir;
                    }
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        } catch (Exception e) {
            return null;
        }
        return best;
    }

    private static boolean hasFiles(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(Files::isRegularFile);
        } catch (Exception e) {
            return false;
        }
    }

    private static void copyTree(Path src, Path dest) throws Exception {
        if (src == null || dest == null || !Files.exists(src)) {
            return;
        }
        Files.createDirectories(dest);
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws java.io.IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
