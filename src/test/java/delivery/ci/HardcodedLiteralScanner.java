package delivery.ci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * B10 (OPS §12.5 Part A item 1): finds hard-coded hosts and paths in production code. Only string literals count
 * (comments and Javadoc are prose, not values). Scanned: Java under {@code src/main/java} and {@code keel-* /src/main/java},
 * and engine-side JS ({@code tools/** /*.mjs|js}). Not scanned: tests, profile files ({@code *.properties}, {@code *.yml},
 * launch scripts), which is where host-specific values belong.
 */
public final class HardcodedLiteralScanner {
    public record Hit(String file, int line, String rule, String literal) {
        public String key() {
            return file + "|" + literal;
        }
    }

    public record Rule(String id, Pattern pattern) {
    }

    public static final List<Rule> RULES = List.of(
            new Rule("localhost", Pattern.compile("(?i)\\blocalhost\\b")),
            new Rule("loopback-ip", Pattern.compile("\\b127\\.0\\.0\\.1\\b")),
            new Rule("windows-drive", Pattern.compile("(?i)(?<![A-Za-z])[CDE]:(\\\\|/)")),
            new Rule("delivery-store", Pattern.compile("(?i)delivery-store")),
            new Rule("portal-internal", Pattern.compile("(?i)portal\\.internal")));

    private HardcodedLiteralScanner() {
    }

    public static List<Path> productionSources(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        List<Path> javaRoots = new ArrayList<>();
        javaRoots.add(root.resolve("src/main/java"));
        try (Stream<Path> mods = Files.list(root)) {
            mods.filter(p -> p.getFileName().toString().startsWith("keel-"))
                    .map(p -> p.resolve("src/main/java")).forEach(javaRoots::add);
        }
        for (Path r : javaRoots) {
            walk(r, p -> p.toString().endsWith(".java"), out);
        }
        walk(root.resolve("tools"), p -> {
            String n = p.toString();
            return (n.endsWith(".mjs") || n.endsWith(".js")) && !n.contains("node_modules");
        }, out);
        out.sort(null);
        return out;
    }

    private static void walk(Path dir, java.util.function.Predicate<Path> keep, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile).filter(keep).forEach(out::add);
        }
    }

    public static List<Hit> scan(Path root, List<Path> files) throws IOException {
        List<Hit> hits = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            boolean js = rel.endsWith(".mjs") || rel.endsWith(".js");
            for (Literal lit : literals(Files.readString(f, StandardCharsets.UTF_8), js)) {
                for (Rule r : RULES) {
                    if (r.pattern().matcher(lit.text()).find()) {
                        hits.add(new Hit(rel, lit.line(), r.id(), lit.text()));
                    }
                }
            }
        }
        return hits;
    }

    record Literal(int line, String text) {
    }

    /** Tiny lexer: string literals (Java "..." and text blocks; JS also '...' and `...`), comments skipped. */
    static List<Literal> literals(String src, boolean js) {
        List<Literal> out = new ArrayList<>();
        int line = 1;
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') line++;
                    i++;
                }
                i += 2;
            } else if (c == '"' && src.startsWith("\"\"\"", i) && !js) {
                int start = line;
                int end = src.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end;
                String body = src.substring(i + 3, end);
                out.add(new Literal(start, body));
                line += (int) body.chars().filter(ch -> ch == '\n').count();
                i = Math.min(n, end + 3);
            } else if (c == '"' || (js && (c == '\'' || c == '`'))) {
                int start = line;
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && src.charAt(i) != c) {
                    char d = src.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        sb.append(d).append(src.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (d == '\n') {
                        if (c != '`') break;
                        line++;
                    }
                    sb.append(d);
                    i++;
                }
                i++;
                out.add(new Literal(start, sb.toString()));
            } else if (c == '\'' && !js) {
                i++; // Java char literal
                while (i < n && src.charAt(i) != '\'' && src.charAt(i) != '\n') {
                    i += src.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
            } else {
                i++;
            }
        }
        return out;
    }

    /** Allowlist line: {@code file|literal|reason}. Blank lines and # comments ignored. */
    public record Allowed(String file, String literal, String reason) {
        public String key() {
            return file + "|" + literal;
        }
    }

    public static List<Allowed> allowlist(Path file) throws IOException {
        List<Allowed> out = new ArrayList<>();
        for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = l.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] p = t.split("\\|", 3);
            if (p.length != 3 || p[2].isBlank()) {
                throw new IllegalArgumentException("allowlist line needs file|literal|reason: " + l);
            }
            out.add(new Allowed(p[0].strip(), p[1], p[2].strip()));
        }
        return out;
    }

    public static String describe(List<Hit> hits) {
        StringBuilder sb = new StringBuilder();
        for (Hit h : hits) {
            sb.append("\n  ").append(h.file()).append(':').append(h.line()).append(" [").append(h.rule()).append("] \"")
                    .append(h.literal().length() > 120 ? h.literal().substring(0, 120) + "..." : h.literal()).append('"');
        }
        return sb.toString();
    }
}
