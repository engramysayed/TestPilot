package delivery.portal;

import delivery.portal.service.AdminPromptsService;
import delivery.prompts.PromptCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Boot-time inventory of every classpath prompt id: log source + short sha256,
 * and fail fast when an override dir is configured but incomplete.
 * Runs after {@link PromptsDirBinder} (constructor inject ensures PostConstruct bind first).
 */
@Component
public class PromptsBootInventoryRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PromptsBootInventoryRunner.class);

    private final AdminPromptsService adminPromptsService;
    @SuppressWarnings("unused")
    private final PromptsDirBinder promptsDirBinder;

    public PromptsBootInventoryRunner(AdminPromptsService adminPromptsService, PromptsDirBinder promptsDirBinder) {
        this.adminPromptsService = adminPromptsService;
        this.promptsDirBinder = promptsDirBinder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        inventoryAndValidate();
    }

    /**
     * Scan classpath prompt ids, log inventory lines, and fail fast when override is set
     * but any known id lacks an override {@code .md}/{@code .txt} file.
     *
     * @return inventory rows (ordered by id) for tests / callers
     */
    public List<InventoryEntry> inventoryAndValidate() throws IOException {
        TreeSet<String> ids = adminPromptsService.classpathIds();
        boolean overrideConfigured = PromptCatalog.overrideDir().isPresent();
        String version = PromptCatalog.version();
        log.info(
                "prompts boot inventory: promptsVersion={} overrideConfigured={} classpathIdCount={}",
                version,
                overrideConfigured,
                ids.size());

        List<InventoryEntry> entries = new ArrayList<>(ids.size());
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            Optional<String> overrideBody = PromptCatalog.tryOverrideText(id);
            Optional<String> classpathBody = PromptCatalog.tryClasspathText(id);
            String source;
            String body;
            if (overrideBody.isPresent()) {
                source = AdminPromptsService.SOURCE_OVERRIDE;
                body = overrideBody.get();
            } else {
                source = AdminPromptsService.SOURCE_CLASSPATH;
                body = classpathBody.orElse("");
                if (overrideConfigured) {
                    missing.add(id);
                }
            }
            String sha = shortSha256(body);
            entries.add(new InventoryEntry(id, source, sha));
            log.info("prompts boot inventory: id={} source={} sha256={}", id, source, sha);
        }

        if (overrideConfigured && !missing.isEmpty()) {
            String msg = "Prompt override directory is incomplete; missing override file(s) for known classpath id(s): "
                    + String.join(", ", missing);
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        return Collections.unmodifiableList(entries);
    }

    static String shortSha256(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record InventoryEntry(String id, String source, String sha256) {
    }
}
