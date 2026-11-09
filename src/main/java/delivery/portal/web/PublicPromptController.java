package delivery.portal.web;

import delivery.prompts.PromptCatalog;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The copy-able generation prompts used to be static files under {@code static/prompts/}. They now live in the
 * prompt catalog ({@code prompts/generate/*.md}: Admin → Prompts, versioned, overridable). This keeps the old public
 * URLs working (Generate page "copy prompt", Guide "AI rewrite → CSV") and serves the owner's override when set.
 */
@RestController
public class PublicPromptController {

    static final Map<String, String> LEGACY = Map.of(
            "keel-tc-generate-from-stories-to-json", "generate/stories-to-json",
            "keel-tc-generate-from-stories-to-csv", "generate/stories-to-csv",
            "keel-capability-manifest", "generate/capability-manifest",
            "keel-testing-techniques", "generate/testing-techniques",
            "keel-tc-rewrite-to-csv", "generate/rewrite-to-csv");

    @GetMapping("/prompts/{name:[a-z0-9-]+}.txt")
    public ResponseEntity<byte[]> prompt(@PathVariable String name) {
        String id = LEGACY.get(name);
        if (id == null) {
            return ResponseEntity.notFound().build();
        }
        String body = PromptCatalog.get(id);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Keel-Prompts-Version", PromptCatalog.version())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
