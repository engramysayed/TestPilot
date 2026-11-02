package delivery.portal;

import delivery.prompts.PromptCatalog;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Binds Spring property {@code keel.prompts.dir} (also via env {@code KEEL_PROMPTS_DIR}
 * through Spring relaxed binding) into {@link PromptCatalog} at startup.
 * Prefer {@link PromptCatalog#setOverrideDir(Path)}; also sets system property as supplement.
 * Does not invent a second catalog API.
 */
@Component
public class PromptsDirBinder {

    @Value("${keel.prompts.dir:}")
    private String promptsDir;

    @PostConstruct
    public void bindOverrideDir() {
        if (promptsDir == null || promptsDir.isBlank()) {
            return;
        }
        Path absolute = Path.of(promptsDir.trim()).toAbsolutePath().normalize();
        PromptCatalog.setOverrideDir(absolute);
        System.setProperty(PromptCatalog.PROP_DIR, absolute.toString());
    }
}
