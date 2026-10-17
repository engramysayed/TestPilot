package delivery.authoring;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TIE pick card for Ollama and Cursor: scored shortlist + whyTied + neighborhood.
 * Pick candidateId from the shortlist only — never invent on this call.
 * screenshotPath is optional (empty-ok); only set when evidence already has a file.
 */
public record HealPickPayload(
        String quotedName,
        String whyTied,
        String scoredTable,
        String neighborhood,
        String screenshotPath
) {
    public static final int NEIGHBORHOOD_MAX = 1500;

    public HealPickPayload(
            String quotedName,
            String whyTied,
            String scoredTable,
            String neighborhood
    ) {
        this(quotedName, whyTied, scoredTable, neighborhood, "");
    }

    public HealPickPayload withScreenshotPath(String path) {
        String p = path == null ? "" : path.trim();
        return new HealPickPayload(quotedName, whyTied, scoredTable, neighborhood, p);
    }

    public static HealPickPayload from(
            StepIntentBinder.IntentLine intent,
            BindDecision decision,
            String html) {
        String quoted = intent == null ? null : StepIntentBinder.quotedVisibleName(intent.text());
        if (decision == null || decision.kind() == BindDecision.Kind.EMPTY
                || decision.shortlist() == null || decision.shortlist().isEmpty()) {
            return new HealPickPayload(quoted == null ? "" : quoted, "", "", "", "");
        }
        List<CandidateScore> scores = decision.scores() == null ? List.of() : decision.scores();
        List<DomCandidate> shortlist = decision.shortlist();
        String why = "";
        if (decision.kind() == BindDecision.Kind.TIE && scores.size() >= 2) {
            CandidateScore best = scores.get(0);
            CandidateScore runner = null;
            for (int i = 1; i < scores.size(); i++) {
                CandidateScore s = scores.get(i);
                if (!CandidateScorer.sameControl(best.candidate(), s.candidate(),
                        scores.stream().map(CandidateScore::candidate).toList())) {
                    runner = s;
                    break;
                }
            }
            String label = best.candidate().label() == null ? "" : best.candidate().label();
            int margin = runner == null ? 0 : best.total() - runner.total();
            why = "TIE margin=" + margin
                    + " best=" + best.candidate().id() + "=" + best.total()
                    + (runner == null ? "" : " runner=" + runner.candidate().id() + "=" + runner.total())
                    + (label.isBlank() ? "" : " same-label=" + label);
        } else if (decision.kind() == BindDecision.Kind.CLEAR) {
            why = "CLEAR";
        }
        StringBuilder table = new StringBuilder();
        int rows = 0;
        for (DomCandidate c : shortlist) {
            if (rows >= CandidateScorer.SHORTLIST_MAX) break;
            CandidateScore score = scores.stream()
                    .filter(s -> s.candidate().id().equals(c.id()))
                    .findFirst()
                    .orElse(null);
            String role = c.tag() == null ? "" : c.tag();
            String label = c.label() == null ? "" : c.label();
            if (score == null) {
                table.append(c.id()).append(" | ").append(c.strategy()).append(" | ")
                        .append(c.value()).append(" | ").append(role).append(" | ")
                        .append(label).append(" | total=0\n");
            } else {
                table.append(c.id()).append(" | ").append(c.strategy()).append(" | ")
                        .append(c.value()).append(" | ").append(role).append(" | ")
                        .append(label)
                        .append(" | total=").append(score.total())
                        .append(" | stability=").append(score.stability())
                        .append(" | uniqueness=").append(score.uniqueness())
                        .append(" | intentMatch=").append(score.intentMatch())
                        .append(" | roleFit=").append(score.roleFit())
                        .append(" | labelDistance=").append(score.labelDistance())
                        .append('\n');
            }
            rows++;
        }
        String neighborhood = neighborhood(html, shortlist);
        return new HealPickPayload(
                quoted == null ? "" : quoted,
                why,
                table.toString().trim(),
                neighborhood,
                "");
    }

    public String ollamaSystem() {
        return """
                Pick exactly one candidateId from the scored shortlist.
                Reply with JSON only: {"candidateId":"<id from shortlist>"}.
                Ids must come from the shortlist only. Do not return locators. JSON only - no prose.
                """;
    }

    public String ollamaUser(String tcId, String failureReason) {
        String shot = screenshotPath == null || screenshotPath.isBlank()
                ? ""
                : "\nScreenshot path (optional evidence already on disk): " + screenshotPath + "\n";
        return """
                TC_ID: %s
                Quoted name: %s
                Why tied: %s
                Failure: %s
                Scored shortlist (id | strategy | value | role/tag | label | totals):
                %s
                Neighborhood (parent HTML of shortlisted nodes; nearby label/role context):
                %s
                %s""".formatted(
                tcId == null ? "" : tcId,
                quotedName == null ? "" : quotedName,
                whyTied == null ? "" : whyTied,
                failureReason == null ? "" : failureReason,
                scoredTable == null ? "" : scoredTable,
                neighborhood == null ? "" : neighborhood,
                shot);
    }

    private static String neighborhood(String html, List<DomCandidate> shortlist) {
        if (html == null || html.isBlank() || shortlist == null || shortlist.isEmpty()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        Set<String> bits = new LinkedHashSet<>();
        for (DomCandidate c : shortlist) {
            Element el = findElement(doc, c);
            if (el == null) continue;
            Element parent = el.parent();
            String snippet = parent == null ? el.outerHtml() : parent.outerHtml();
            if (snippet != null && !snippet.isBlank()) {
                bits.add(snippet);
            }
        }
        String joined = String.join("\n", bits);
        if (joined.length() <= NEIGHBORHOOD_MAX) {
            return joined;
        }
        return joined.substring(0, NEIGHBORHOOD_MAX);
    }

    private static Element findElement(Document doc, DomCandidate c) {
        if (c == null || c.value() == null) return null;
        String strategy = c.strategy() == null ? "" : c.strategy().toLowerCase(Locale.ROOT);
        String value = c.value();
        try {
            if ("css".equals(strategy) || "cssselector".equals(strategy)) {
                return doc.selectFirst(value);
            }
            if ("id".equals(strategy)) {
                return doc.getElementById(value);
            }
            if (value.contains("data-axis-test-id=") || value.contains("data-testid=")) {
                var m = java.util.regex.Pattern.compile(
                        "data-(?:axis-test-id|testid)=['\"]([^'\"]+)['\"]").matcher(value);
                if (m.find()) {
                    Element byHook = doc.selectFirst("[data-axis-test-id=" + cssQuote(m.group(1)) + "]");
                    if (byHook != null) return byHook;
                    return doc.selectFirst("[data-testid=" + cssQuote(m.group(1)) + "]");
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String cssQuote(String v) {
        if (v.indexOf('\'') >= 0) return "\"" + v + "\"";
        return "'" + v + "'";
    }
}
