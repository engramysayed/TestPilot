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

/** Prices the account owner sets. Every job reads these, not a number typed by the browser. */
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

    @Transactional
    public void save(Map<String, String> form) {
        ensureSeeded();
        for (ServicePrice row : prices.findAllByOrderBySortOrderAsc()) {
            String label = form.get("label_" + row.getCode());
            String chargedAs = form.get("charged_" + row.getCode());
            String covers = form.get("covers_" + row.getCode());
            String costRaw = form.get("cost_" + row.getCode());
            if (label == null || chargedAs == null || covers == null || costRaw == null) {
                throw new IllegalArgumentException("Enter a name, charged as, what it covers, and a price for every service.");
            }
            String cleaned = oneLine(label, 80, "Each service name needs 1 to 80 characters.");
            String charged = oneLine(chargedAs, 80, "Charged as needs 1 to 80 characters.");
            String cover = oneLine(covers, 240, "What it covers needs 1 to 240 characters.");
            long cost;
            try {
                cost = Long.parseLong(costRaw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Prices are whole numbers.");
            }
            if (cost < 0 || cost > MAX_COST) {
                throw new IllegalArgumentException("Each price must be from 0 to " + MAX_COST + ".");
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
            return;
        }
        renameLegacy("GENERATE", "Write test cases", "Generate");
        renameLegacy("EXECUTE", "Run a test", "Execute");
        renameLegacy("AUTOMATE", "Build the framework", "Automation");
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
            default -> "Each writing job";
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static String detail(String code) {
        return switch (code == null ? "" : code.toUpperCase(Locale.ROOT)) {
            case "GENERATE" -> "One price for the whole writing job.";
            case "EXECUTE" -> "Charged once for each test case in the run, including Run it once.";
            case "AUTOMATE" -> "Charged once for each test case in the build, not once for the package.";
            case "HUNT" -> "A step is one click or one thing typed. The hunt page shows how many steps this run may try.";
            default -> "";
        };
    }
}
