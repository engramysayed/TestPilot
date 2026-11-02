package delivery.portal.service.credits;

import delivery.portal.persistence.credits.ServicePrice;
import delivery.portal.persistence.credits.ServicePriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Credits the account owner sets. Every job reads these, not a number typed by the browser. */
@Service
public class ServicePriceService {
    public static final long MAX_COST = 1_000_000L;

    private final ServicePriceRepository prices;
    private final DeliveryCreditsProperties defaults;

    public ServicePriceService(ServicePriceRepository prices, DeliveryCreditsProperties defaults) {
        this.prices = prices;
        this.defaults = defaults;
    }

    @Transactional
    public List<Map<String, Object>> list() {
        ensureSeeded();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServicePrice row : prices.findAllByOrderBySortOrderAsc()) {
            rows.add(toMap(row));
        }
        return rows;
    }

    @Transactional
    public long cost(CreditsBillingKind kind) {
        if (kind == null) {
            return 0L;
        }
        ensureSeeded();
        return prices.findById(kind.name()).map(ServicePrice::getCost).orElse(fallback(kind));
    }

    /** Resolve admin price by ServicePrice code (core or add-on). Missing code -> 0 after seed. */
    @Transactional
    public long costByCode(String code) {
        if (code == null || code.isBlank()) {
            return 0L;
        }
        ensureSeeded();
        String c = code.trim().toUpperCase(Locale.ROOT);
        return prices.findById(c).map(ServicePrice::getCost).orElse(0L);
    }

    @Transactional
    public void save(Map<String, String> form) {
        ensureSeeded();
        for (ServicePrice row : prices.findAllByOrderBySortOrderAsc()) {
            String label = form.get("label_" + row.getCode());
            String chargedAs = form.get("charged_" + row.getCode());
            String covers = form.get("covers_" + row.getCode());
            String costRaw = form.get("cost_" + row.getCode());
            if (label == null || chargedAs == null || covers == null || costRaw == null) {
                throw new IllegalArgumentException("Enter a name, charged as, what it covers, and a credit cost for every service.");
            }
            String cleaned = oneLine(label, 80, "Each service name needs 1 to 80 characters.");
            String charged = oneLine(chargedAs, 80, "Charged as needs 1 to 80 characters.");
            String cover = oneLine(covers, 240, "What it covers needs 1 to 240 characters.");
            long cost;
            try {
                cost = Long.parseLong(costRaw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Credit costs are whole numbers.");
            }
            if (cost < 0 || cost > MAX_COST) {
                throw new IllegalArgumentException("Each credit cost must be from 0 to " + MAX_COST + ".");
            }
            row.setLabel(cleaned);
            row.setChargedAs(charged);
            row.setCovers(cover);
            row.setCost(cost);
            row.setUpdatedAt(Instant.now());
            prices.save(row);
        }
    }

    private void ensureSeeded() {
        if (prices.count() == 0) {
            saveDefault("GENERATE", "Generate", defaults.getCost().getGenerate(), 1);
            saveDefault("EXECUTE", "Execute", defaults.getCost().getExecute(), 2);
            saveDefault("AUTOMATE", "Automation", defaults.getCost().getAutomate(), 3);
            saveDefault("HUNT", "Bug Hunter", defaults.getCost().getHunt(), 4);
        } else {
            renameLegacy("GENERATE", "Write test cases", "Generate");
            renameLegacy("EXECUTE", "Run a test", "Execute");
            renameLegacy("AUTOMATE", "Build the framework", "Automation");
            refreshLegacyCovers("GENERATE", "One price for the whole writing job.");
            refreshLegacyCovers("EXECUTE", "Charged once for each test case in the run, including Run it once.");
            refreshLegacyCovers("AUTOMATE", "Charged once for each test case in the build, not once for the package.");
            for (ServicePrice row : prices.findAllByOrderBySortOrderAsc()) {
                boolean changed = false;
                if (row.getChargedAs() == null || row.getChargedAs().isBlank()
                        || ("HUNT".equals(row.getCode()) && "Each allowed action".equals(row.getChargedAs()))) {
                    row.setChargedAs(unitLabel(row.getCode()));
                    changed = true;
                }
                if (row.getCovers() == null || row.getCovers().isBlank()
                        || ("HUNT".equals(row.getCode()) && row.getCovers().startsWith("Charged once for each allowed action"))) {
                    row.setCovers(detail(row.getCode()));
                    changed = true;
                }
                if (changed) {
                    row.setUpdatedAt(Instant.now());
                    prices.save(row);
                }
            }
        }
        // Confirmed add-on rows: display + admin-editable; debit via CreditService.tryDebitForAddon.
        ensureAddon("VISUAL_DIFF", "Visual diffs", 5L, 5);
        ensureAddon("SUITES_MONITOR", "Suites & monitor", 10L, 6);
        ensureAddon("HEAL_REVIEW", "Heal review", 5L, 7);
        // Refresh old canned draft covers only when still exact match (owner edits preserved).
        refreshLegacyCovers("VISUAL_DIFF",
                "Compare screenshots and review visual changes after a prove run. Draft credit amount — the account owner can edit it on Admin → Prices.");
        refreshLegacyCovers("SUITES_MONITOR",
                "Scheduled suite runs and monitor alerts that watch tagged cases. Draft credit amount — the account owner can edit it on Admin → Prices.");
        refreshLegacyCovers("HEAL_REVIEW",
                "Review heal proposals: Accept or Reject suggested fixes after a failed run. Draft credit amount — the account owner can edit it on Admin → Prices.");
    }

    /** Insert an add-on row only when missing so re-runs keep owner edits. */
    private void ensureAddon(String code, String label, long cost, int order) {
        if (prices.findById(code).isPresent()) {
            return;
        }
        saveDefault(code, label, cost, order);
    }

    private static String oneLine(String raw, int max, String error) {
        String cleaned = raw == null ? "" : raw.trim().replaceAll("[\\r\\n]+", " ");
        if (cleaned.isBlank() || cleaned.length() > max) {
            throw new IllegalArgumentException(error);
        }
        return cleaned;
    }

    private void renameLegacy(String code, String previousName, String featureName) {
        prices.findById(code).ifPresent(row -> {
            if (previousName.equals(row.getLabel())) {
                row.setLabel(featureName);
                row.setUpdatedAt(Instant.now());
                prices.save(row);
            }
        });
    }

    /** Replace canned covers with the new detail when the owner never customized them. */
    private void refreshLegacyCovers(String code, String previousCovers) {
        prices.findById(code).ifPresent(row -> {
            if (previousCovers.equals(row.getCovers())) {
                row.setCovers(detail(code));
                row.setUpdatedAt(Instant.now());
                prices.save(row);
            }
        });
    }

    private void saveDefault(String code, String label, long cost, int order) {
        ServicePrice row = new ServicePrice();
        row.setCode(code);
        row.setLabel(label);
        row.setChargedAs(unitLabel(code));
        row.setCovers(detail(code));
        row.setCost(Math.max(0, cost));
        row.setSortOrder(order);
        row.setUpdatedAt(Instant.now());
        prices.save(row);
    }

    private long fallback(CreditsBillingKind kind) {
        return switch (kind) {
            case GENERATE -> defaults.getCost().getGenerate();
            case EXECUTE -> defaults.getCost().getExecute();
            case AUTOMATE -> defaults.getCost().getAutomate();
            case HUNT -> defaults.getCost().getHunt();
        };
    }

    private static Map<String, Object> toMap(ServicePrice row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", row.getCode());
        out.put("label", row.getLabel());
        out.put("cost", row.getCost());
        out.put("unit", blank(row.getChargedAs()) ? unitLabel(row.getCode()) : row.getChargedAs());
        out.put("detail", blank(row.getCovers()) ? detail(row.getCode()) : row.getCovers());
        return out;
    }

    static String unitLabel(String code) {
        return switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "EXECUTE", "AUTOMATE" -> "Each test case";
            case "HUNT" -> "Each step";
            case "VISUAL_DIFF" -> "Each review";
            case "SUITES_MONITOR" -> "Each scheduled run";
            case "HEAL_REVIEW" -> "Each proposal";
            default -> "Each writing job";
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String detail(String code) {
        return switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "GENERATE" ->
                    "Writing jobs that turn stories or knowledge into test cases. Includes classic Generate and Generate-from-knowledge. One credit charge for the whole writing job.";
            case "EXECUTE" ->
                    "Run cost for each test case. Includes evidence when a run fails: replay the failure, Accept or Reject heal suggestions, and flaky tags in everyday words.";
            case "AUTOMATE" ->
                    "Build cost for each test case turned into automation. Builds include stronger locators that heal when the page changes and clearer steps you can read.";
            case "HUNT" ->
                    "A step is one click or one thing typed. The hunt page shows how many steps this run may try.";
            case "VISUAL_DIFF" ->
                    "Compare screenshots and review visual changes after a prove run. Owner can edit on Admin → Prices.";
            case "SUITES_MONITOR" ->
                    "Scheduled suite runs and monitor alerts that watch tagged cases. Owner can edit on Admin → Prices.";
            case "HEAL_REVIEW" ->
                    "Review heal proposals: Accept or Reject suggested fixes after a failed run. Owner can edit on Admin → Prices.";
            default -> "";
        };
    }
}
