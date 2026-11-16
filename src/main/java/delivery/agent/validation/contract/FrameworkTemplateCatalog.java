package delivery.agent.validation.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Read-only catalog of reusable template utilities for duplicate/missing-helper heuristics. */
public final class FrameworkTemplateCatalog {
    private FrameworkTemplateCatalog() {
    }

    public static Catalog load(Path templateRoot) {
        if (templateRoot == null || !Files.isDirectory(templateRoot)) {
            return new Catalog(List.of());
        }
        Path utils = templateRoot.resolve("src/main/java/project/utils");
        List<UtilityRef> refs = new ArrayList<>();
        if (Files.isDirectory(utils)) {
            try (Stream<Path> walk = Files.walk(utils)) {
                walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
                        .forEach(p -> refs.add(toRef(utils, p)));
            } catch (IOException ignored) {
                // deterministic empty catalog on IO failure
            }
        }
        return new Catalog(List.copyOf(refs));
    }

    private static UtilityRef toRef(Path utilsRoot, Path file) {
        String rel = utilsRoot.relativize(file).toString().replace('\\', '/');
        String fqcn = "project.utils." + rel.replace(".java", "").replace('/', '.');
        String name = file.getFileName().toString().replace(".java", "");
        String body = "";
        try {
            body = Files.readString(file);
        } catch (IOException ignored) {
        }
        return new UtilityRef(fqcn, name, body);
    }

    public record Catalog(List<UtilityRef> utilities) {
        public boolean mentionsUtility(String simpleName) {
            if (simpleName == null || simpleName.isBlank()) {
                return false;
            }
            for (UtilityRef ref : utilities) {
                if (simpleName.equals(ref.simpleName())) {
                    return true;
                }
            }
            return false;
        }

        public Map<String, String> bySimpleName() {
            Map<String, String> map = new LinkedHashMap<>();
            for (UtilityRef u : utilities) {
                map.putIfAbsent(u.simpleName().toLowerCase(Locale.ROOT), u.fqcn());
            }
            return map;
        }
    }

    public record UtilityRef(String fqcn, String simpleName, String sourceBody) {
        public UtilityRef {
            simpleName = simpleName == null ? "" : simpleName;
        }

        public boolean declaresPublicApi() {
            return sourceBody != null && sourceBody.contains("public ");
        }
    }
}